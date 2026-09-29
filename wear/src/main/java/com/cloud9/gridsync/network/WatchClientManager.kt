package com.cloud9.gridsync.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

object WatchClientManager {

    private const val TAG = "WatchClientManager"
    private const val SERVICE_TYPE = "_gridsync._tcp."
    private const val PAIR_CODE = "CLOUD9"
    private const val CONNECT_TIMEOUT_MS = 4000
    private const val RECONNECT_DELAY_MS = 3000L
    private const val PING_INTERVAL_MS = 10000L

    interface WatchMessageListener {
        fun onConnectionChanged(isConnected: Boolean)
        fun onRoleChanged(role: String)
        fun onPlayReceived(playMessage: String)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val connecting = AtomicBoolean(false)

    @Volatile private var shouldRun = false
    @Volatile private var connected = false
    @Volatile private var listener: WatchMessageListener? = null

    private var appContext: Context? = null
    private var watchId: String = "unknown_watch"
    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var socket: Socket? = null
    private var writer: BufferedWriter? = null

    private val reconnectRunnable = Runnable {
        if (shouldRun && !connected) startDiscovery()
    }

    private val pingRunnable = object : Runnable {
        override fun run() {
            if (!shouldRun || !connected) return
            sendJsonSafely(JSONObject().put("type", "ping"))
            mainHandler.postDelayed(this, PING_INTERVAL_MS)
        }
    }

    fun setListener(newListener: WatchMessageListener) {
        listener = newListener
        postConnection(connected)
    }

    fun clearListener() {
        listener = null
    }

    fun connect(context: Context, watchId: String) {
        appContext = context.applicationContext
        this.watchId = watchId
        shouldRun = true
        mainHandler.removeCallbacks(reconnectRunnable)
        if (connected) {
            postConnection(true)
        } else {
            startDiscovery()
        }
    }

    fun disconnect() {
        shouldRun = false
        connected = false
        connecting.set(false)
        mainHandler.removeCallbacks(reconnectRunnable)
        mainHandler.removeCallbacks(pingRunnable)
        stopDiscovery()
        closeSocket()
        postConnection(false)
    }

    private fun startDiscovery() {
        val context = appContext ?: return
        if (!shouldRun || connected || connecting.get()) return
        if (discoveryListener != null) return

        val manager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        nsdManager = manager

        val discovery = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.d(TAG, "Searching local network for GridSync tablet")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!shouldRun || connected || connecting.get()) return
                if (!serviceInfo.serviceType.trimEnd('.').equals(SERVICE_TYPE.trimEnd('.'), ignoreCase = true)) return
                resolveService(manager, serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "GridSync service lost: ${serviceInfo.serviceName}")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                discoveryListener = null
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "NSD discovery failed: $errorCode")
                discoveryListener = null
                scheduleReconnect()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "NSD stop failed: $errorCode")
                discoveryListener = null
            }
        }

        discoveryListener = discovery
        try {
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discovery)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to start NSD discovery", e)
            discoveryListener = null
            scheduleReconnect()
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveService(manager: NsdManager, serviceInfo: NsdServiceInfo) {
        if (!connecting.compareAndSet(false, true)) return

        manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "NSD resolve failed: $errorCode")
                connecting.set(false)
            }

            override fun onServiceResolved(resolved: NsdServiceInfo) {
                val host = resolved.host?.hostAddress
                val port = resolved.port
                if (host.isNullOrBlank() || port <= 0) {
                    connecting.set(false)
                    return
                }
                connectSocket(host, port)
            }
        })
    }

    private fun connectSocket(host: String, port: Int) {
        thread(name = "GridSync-Watch-Connect") {
            var newSocket: Socket? = null
            try {
                if (!shouldRun) return@thread

                newSocket = Socket()
                newSocket.tcpNoDelay = true
                newSocket.keepAlive = true
                newSocket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)

                val reader = BufferedReader(InputStreamReader(newSocket.getInputStream()))
                val newWriter = BufferedWriter(OutputStreamWriter(newSocket.getOutputStream()))

                sendJson(newWriter, JSONObject()
                    .put("type", "hello")
                    .put("pairCode", PAIR_CODE)
                    .put("watchId", watchId)
                    .put("watchName", "${Build.MANUFACTURER} ${Build.MODEL}".trim()))

                val firstLine = reader.readLine() ?: throw IllegalStateException("Tablet closed connection")
                val firstMessage = JSONObject(firstLine)
                if (firstMessage.optString("type") != "accepted") {
                    throw IllegalStateException("Tablet rejected pairing")
                }

                socket = newSocket
                writer = newWriter
                connected = true
                connecting.set(false)
                stopDiscovery()
                postConnection(true)
                mainHandler.removeCallbacks(pingRunnable)
                mainHandler.postDelayed(pingRunnable, PING_INTERVAL_MS)

                listenLoop(reader, newSocket)
            } catch (e: Exception) {
                Log.w(TAG, "Connection attempt failed: ${e.message}")
                try { newSocket?.close() } catch (_: Exception) {}
                connected = false
                connecting.set(false)
                postConnection(false)
                if (shouldRun) scheduleReconnect()
            }
        }
    }

    private fun listenLoop(reader: BufferedReader, activeSocket: Socket) {
        try {
            while (shouldRun && connected && !activeSocket.isClosed) {
                val line = reader.readLine() ?: break
                val message = JSONObject(line)
                when (message.optString("type")) {
                    "role" -> postRole(message.optString("role", "Unassigned"))
                    "play" -> {
                        val role = message.optString("role", "Unassigned")
                        val playName = message.optString("playName", "Play")
                        val assignment = message.optString("assignment", "Follow your assigned route")
                        postRole(role)
                        sendDeliveryAck(role, "play", playName)
                        postPlay("$playName\n\n$assignment")
                    }
                    "text_message" -> {
                        val role = message.optString("role", "Unassigned")
                        val text = message.optString("message", "")
                        postRole(role)
                        sendDeliveryAck(role, "text_message", "")
                        postPlay(text)
                    }
                    "pong" -> Log.d(TAG, "Tablet heartbeat received")
                }
            }
        } catch (e: Exception) {
            if (shouldRun) Log.w(TAG, "Connection lost: ${e.message}")
        } finally {
            if (socket === activeSocket) {
                connected = false
                mainHandler.removeCallbacks(pingRunnable)
                closeSocket()
                postConnection(false)
                if (shouldRun) scheduleReconnect()
            }
        }
    }

    private fun sendDeliveryAck(role: String, kind: String, name: String) {
        sendJsonSafely(JSONObject()
            .put("type", "delivery_ack")
            .put("role", role)
            .put("kind", kind)
            .put("name", name))
    }

    private fun sendJsonSafely(json: JSONObject) {
        val currentWriter = writer ?: return
        thread(name = "GridSync-Watch-Send") {
            try {
                synchronized(currentWriter) { sendJson(currentWriter, json) }
            } catch (e: Exception) {
                Log.w(TAG, "Send failed: ${e.message}")
            }
        }
    }

    private fun sendJson(target: BufferedWriter, json: JSONObject) {
        target.write(json.toString())
        target.newLine()
        target.flush()
    }

    private fun scheduleReconnect() {
        if (!shouldRun) return
        mainHandler.removeCallbacks(reconnectRunnable)
        mainHandler.postDelayed(reconnectRunnable, RECONNECT_DELAY_MS)
    }

    private fun stopDiscovery() {
        val manager = nsdManager
        val discovery = discoveryListener
        if (manager != null && discovery != null) {
            try { manager.stopServiceDiscovery(discovery) } catch (_: Exception) {}
        }
        discoveryListener = null
        nsdManager = null
    }

    private fun closeSocket() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        writer = null
    }

    private fun postConnection(value: Boolean) {
        mainHandler.post { listener?.onConnectionChanged(value) }
    }

    private fun postRole(role: String) {
        mainHandler.post { listener?.onRoleChanged(role) }
    }

    private fun postPlay(message: String) {
        mainHandler.post { listener?.onPlayReceived(message) }
    }
}
