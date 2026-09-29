package com.cloud9.gridsync.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

object WatchClientManager {

    private const val TAG = "WatchClientManager"
    private const val SERVICE_TYPE = "_gridsync._tcp."
    private const val SERVER_PORT = 5001
    private const val PAIR_CODE = "CLOUD9"
    private const val CONNECT_TIMEOUT_MS = 2500
    private const val RECONNECT_DELAY_MS = 3000L
    private const val NSD_FALLBACK_DELAY_MS = 4500L
    private const val PING_INTERVAL_MS = 10000L

    interface WatchMessageListener {
        fun onConnectionChanged(isConnected: Boolean)
        fun onRoleChanged(role: String)
        fun onPlayReceived(
            playName: String,
            playTextMessage: String,
            movements: Map<String, List<PointData>>
        )
        fun onTextMessageReceived(role: String, message: String)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val gson = Gson()
    private val connecting = AtomicBoolean(false)
    private val fallbackRunning = AtomicBoolean(false)

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
        if (shouldRun && !connected) beginConnectionCycle()
    }

    private val fallbackRunnable = Runnable {
        if (shouldRun && !connected && !connecting.get()) scanLocalSubnet()
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
        mainHandler.removeCallbacks(fallbackRunnable)
        if (connected) postConnection(true) else beginConnectionCycle()
    }

    fun disconnect() {
        shouldRun = false
        connected = false
        connecting.set(false)
        fallbackRunning.set(false)
        mainHandler.removeCallbacks(reconnectRunnable)
        mainHandler.removeCallbacks(fallbackRunnable)
        mainHandler.removeCallbacks(pingRunnable)
        stopDiscovery()
        closeSocket()
        postConnection(false)
    }

    private fun beginConnectionCycle() {
        if (!shouldRun || connected) return
        startDiscovery()
        mainHandler.removeCallbacks(fallbackRunnable)
        mainHandler.postDelayed(fallbackRunnable, NSD_FALLBACK_DELAY_MS)
    }

    private fun startDiscovery() {
        val context = appContext ?: return
        if (!shouldRun || connected || discoveryListener != null) return

        val manager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        nsdManager = manager

        val discovery = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.i(TAG, "NSD discovery started for $SERVICE_TYPE")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!shouldRun || connected || connecting.get()) return
                if (!serviceInfo.serviceType.trimEnd('.').equals(SERVICE_TYPE.trimEnd('.'), true)) return
                Log.i(TAG, "Found GridSync tablet service: ${serviceInfo.serviceName}")
                resolveService(manager, serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service lost: ${serviceInfo.serviceName}")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                discoveryListener = null
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "NSD start failed: $errorCode; using subnet fallback")
                discoveryListener = null
                scanLocalSubnet()
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
            Log.w(TAG, "Unable to start NSD: ${e.message}")
            discoveryListener = null
            scanLocalSubnet()
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
                Log.i(TAG, "Resolved tablet at $host:$port")
                connectSocket(host, port)
            }
        })
    }

    private fun scanLocalSubnet() {
        if (!shouldRun || connected || connecting.get()) return
        if (!fallbackRunning.compareAndSet(false, true)) return

        thread(name = "GridSync-Subnet-Fallback") {
            try {
                val localIp = getLocalIpv4Address()
                if (localIp == null) {
                    Log.w(TAG, "No usable local IPv4 address; cannot run fallback scan")
                    return@thread
                }

                val prefix = localIp.substringBeforeLast('.')
                Log.i(TAG, "Fallback scan on $prefix.0/24 from $localIp")

                for (i in 1..254) {
                    if (!shouldRun || connected || connecting.get()) break
                    val host = "$prefix.$i"
                    if (host == localIp) continue
                    if (probeAndConnect(host)) break
                }
            } finally {
                fallbackRunning.set(false)
                if (shouldRun && !connected && !connecting.get()) scheduleReconnect()
            }
        }
    }

    private fun probeAndConnect(host: String): Boolean {
        var probe: Socket? = null
        return try {
            probe = Socket()
            probe.connect(InetSocketAddress(host, SERVER_PORT), 180)
            try { probe.close() } catch (_: Exception) {}
            probe = null
            if (!connecting.compareAndSet(false, true)) return false
            Log.i(TAG, "GridSync port found at $host:$SERVER_PORT")
            connectSocket(host, SERVER_PORT)
            true
        } catch (_: Exception) {
            false
        } finally {
            try { probe?.close() } catch (_: Exception) {}
        }
    }

    private fun getLocalIpv4Address(): String? {
        return try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            interfaces.asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { Collections.list(it.inetAddresses).asSequence() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
                ?.hostAddress
        } catch (e: Exception) {
            Log.w(TAG, "Could not determine local IP: ${e.message}")
            null
        }
    }

    private fun connectSocket(host: String, port: Int) {
        thread(name = "GridSync-Watch-Connect") {
            var newSocket: Socket? = null
            try {
                if (!shouldRun) return@thread
                newSocket = Socket().apply {
                    tcpNoDelay = true
                    keepAlive = true
                    connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                }

                val reader = BufferedReader(InputStreamReader(newSocket.getInputStream()))
                val newWriter = BufferedWriter(OutputStreamWriter(newSocket.getOutputStream()))

                sendJson(newWriter, JSONObject()
                    .put("type", "hello")
                    .put("pairCode", PAIR_CODE)
                    .put("watchId", watchId)
                    .put("watchName", "${Build.MANUFACTURER} ${Build.MODEL}".trim()))

                val firstLine = reader.readLine() ?: error("Tablet closed connection")
                val firstMessage = JSONObject(firstLine)
                if (firstMessage.optString("type") != "accepted") error("Tablet rejected pairing")

                socket = newSocket
                writer = newWriter
                connected = true
                connecting.set(false)
                mainHandler.removeCallbacks(fallbackRunnable)
                stopDiscovery()
                postConnection(true)
                mainHandler.removeCallbacks(pingRunnable)
                mainHandler.postDelayed(pingRunnable, PING_INTERVAL_MS)
                listenLoop(reader, newSocket)
            } catch (e: Exception) {
                Log.w(TAG, "Connection failed to $host:$port: ${e.message}")
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
                        val playName = message.optString("playName", "")
                        val assignment = message.optString("assignment", "")
                        val movementJson = message.optString("movements", "{}")
                        val movementType = object : TypeToken<Map<String, List<PointData>>>() {}.type
                        val movements: Map<String, List<PointData>> = try {
                            gson.fromJson(movementJson, movementType) ?: emptyMap()
                        } catch (_: Exception) { emptyMap() }
                        postRole(role)
                        sendDeliveryAck(role, "play", playName)
                        postPlay(playName, assignment, movements)
                    }
                    "text_message" -> {
                        val role = message.optString("role", "Unassigned")
                        val text = message.optString("message", "")
                        postRole(role)
                        sendDeliveryAck(role, "text_message", "")
                        postTextMessage(role, text)
                    }
                    "pong" -> Log.d(TAG, "Tablet heartbeat received")
                }
            }
        } catch (e: Exception) {
            if (shouldRun) Log.w(TAG, "Connection lost: ${e.message}")
        } finally {
            if (socket === activeSocket) {
                connected = false
                connecting.set(false)
                mainHandler.removeCallbacks(pingRunnable)
                closeSocket()
                postConnection(false)
                if (shouldRun) scheduleReconnect()
            }
        }
    }

    private fun sendDeliveryAck(role: String, kind: String, name: String) {
        sendJsonSafely(JSONObject().put("type", "delivery_ack").put("role", role).put("kind", kind).put("name", name))
    }

    private fun sendJsonSafely(json: JSONObject) {
        val currentWriter = writer ?: return
        thread(name = "GridSync-Watch-Send") {
            try { synchronized(currentWriter) { sendJson(currentWriter, json) } }
            catch (e: Exception) { Log.w(TAG, "Send failed: ${e.message}") }
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

    private fun postConnection(value: Boolean) = mainHandler.post { listener?.onConnectionChanged(value) }
    private fun postRole(role: String) = mainHandler.post { listener?.onRoleChanged(role) }
    private fun postPlay(playName: String, text: String, movements: Map<String, List<PointData>>) =
        mainHandler.post { listener?.onPlayReceived(playName, text, movements) }
    private fun postTextMessage(role: String, message: String) =
        mainHandler.post { listener?.onTextMessageReceived(role, message) }
}
