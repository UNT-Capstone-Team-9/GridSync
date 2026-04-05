// NEW EMULATOR FALLBACK CODE HERE
// This code uses 10.0.2.2 and port 6001.
package com.cloud9.gridsync.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket

object WatchClientManager {

    private const val TAG = "WatchClientManager"

    interface Listener {
        fun onStatusChanged(status: String)
        fun onRoleChanged(role: String?)
        fun onPlayReceived(play: PlayMessage)
    }

    private const val SERVICE_TYPE = "_gridsync._tcp."
    private const val PAIR_CODE = "CLOUD9"

    private const val EMULATOR_FALLBACK_HOST = "10.0.2.2"
    private const val EMULATOR_FALLBACK_PORT = 6001

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var started = false

    private var listener: Listener? = null
    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null

    fun start(context: Context, newListener: Listener) {
        listener = newListener

        if (started) {
            Log.d(TAG, "Already started")
            postStatus("Searching for tablet")
            return
        }

        started = true

        if (isProbablyEmulator()) {
            Log.d(TAG, "Using emulator direct connect fallback")
            postStatus("Connecting to tablet")
            connectDirect(context.applicationContext, EMULATOR_FALLBACK_HOST, EMULATOR_FALLBACK_PORT)
        } else {
            Log.d(TAG, "Starting watch client")
            discoverTablet(context.applicationContext)
        }
    }

    fun stop() {
        started = false

        try {
            discoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop discovery", e)
        }

        discoveryListener = null
        nsdManager = null

        try {
            socket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to close socket", e)
        }

        socket = null
        reader = null
        writer = null

        Log.d(TAG, "Watch client stopped")
    }

    private fun discoverTablet(context: Context) {
        postStatus("Searching for tablet")
        Log.d(TAG, "Starting NSD discovery")

        nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery failed code $errorCode")
                postStatus("Discovery failed")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Stop discovery failed code $errorCode")
            }

            override fun onDiscoveryStarted(serviceType: String) {
                Log.d(TAG, "Discovery started")
                postStatus("Searching for tablet")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Discovery stopped")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service found ${serviceInfo.serviceName} ${serviceInfo.serviceType}")

                if (serviceInfo.serviceType != SERVICE_TYPE) {
                    return
                }

                nsdManager?.resolveService(
                    serviceInfo,
                    object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            Log.e(TAG, "Resolve failed code $errorCode")
                            postStatus("Resolve failed")
                        }

                        override fun onServiceResolved(resolvedServiceInfo: NsdServiceInfo) {
                            Log.d(
                                TAG,
                                "Service resolved host=${resolvedServiceInfo.host} port=${resolvedServiceInfo.port}"
                            )
                            connectSocket(context, resolvedServiceInfo.host?.hostAddress, resolvedServiceInfo.port)
                        }
                    }
                )
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service lost ${serviceInfo.serviceName}")
                postStatus("Tablet lost")
            }
        }

        nsdManager?.discoverServices(
            SERVICE_TYPE,
            NsdManager.PROTOCOL_DNS_SD,
            discoveryListener
        )
    }

    private fun connectDirect(context: Context, host: String, port: Int) {
        Log.d(TAG, "Direct connect to host=$host port=$port")
        connectSocket(context, host, port)
    }

    private fun connectSocket(context: Context, host: String?, port: Int) {
        Thread {
            try {
                val safeHost = host ?: run {
                    Log.e(TAG, "Resolved service had no host")
                    postStatus("No tablet host")
                    return@Thread
                }

                try {
                    discoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
                } catch (_: Exception) {
                }

                Log.d(TAG, "Connecting to host=$safeHost port=$port")

                val newSocket = Socket(safeHost, port)
                val newReader = BufferedReader(InputStreamReader(newSocket.getInputStream()))
                val newWriter = BufferedWriter(OutputStreamWriter(newSocket.getOutputStream()))

                socket = newSocket
                reader = newReader
                writer = newWriter

                val hello = JSONObject()
                    .put("type", "hello")
                    .put("watchId", getWatchId(context))
                    .put("watchName", getWatchName())
                    .put("pairCode", PAIR_CODE)

                sendJson(newWriter, hello)
                Log.d(TAG, "Hello sent $hello")

                while (started && !newSocket.isClosed) {
                    val line = newReader.readLine() ?: break
                    Log.d(TAG, "Message received $line")

                    val message = JSONObject(line)

                    when (message.optString("type")) {
                        "accepted" -> {
                            postStatus("Connected")
                        }

                        "reject" -> {
                            postStatus("Pair failed")
                            newSocket.close()
                            break
                        }

                        "role" -> {
                            val role = message.optString("role")
                            mainHandler.post {
                                listener?.onRoleChanged(role)
                                listener?.onStatusChanged("Connected as $role")
                            }
                        }

                        "play" -> {
                            val play = PlayMessage(
                                playName = message.optString("playName"),
                                assignment = message.optString("assignment"),
                                imageResourceName = message.optString("imageResourceName"),
                                role = message.optString("role")
                            )
                            mainHandler.post {
                                listener?.onPlayReceived(play)
                            }
                        }

                        "pong" -> {
                        }
                    }
                }

                postStatus("Disconnected")
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed", e)
                postStatus("Connection failed")
            }
        }.start()
    }

    private fun getWatchId(context: Context): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown_watch"
    }

    private fun getWatchName(): String {
        return "Watch ${Build.MODEL}"
    }

    private fun isProbablyEmulator(): Boolean {
        return Build.FINGERPRINT.contains("generic", true) ||
                Build.MODEL.contains("Emulator", true) ||
                Build.HARDWARE.contains("ranchu", true) ||
                Build.PRODUCT.contains("sdk", true)
    }

    private fun postStatus(status: String) {
        mainHandler.post {
            listener?.onStatusChanged(status)
        }
    }

    private fun sendJson(writer: BufferedWriter, json: JSONObject) {
        writer.write(json.toString())
        writer.newLine()
        writer.flush()
    }
}


/*
// ORIGINAL CODE IS HERE For real devices
// This code uses NSD to find the tablet automatically.

package com.cloud9.gridsync.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket

object WatchClientManager {

    private const val TAG = "WatchClientManager"

    interface Listener {
        fun onStatusChanged(status: String)
        fun onRoleChanged(role: String?)
        fun onPlayReceived(play: PlayMessage)
    }

    private const val SERVICE_TYPE = "_gridsync._tcp"
    private const val PAIR_CODE = "CLOUD9"

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var started = false

    private var listener: Listener? = null
    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null

    fun start(context: Context, newListener: Listener) {
        listener = newListener

        if (started) {
            Log.d(TAG, "Already started")
            postStatus("Searching for tablet")
            return
        }

        started = true
        Log.d(TAG, "Starting watch client")
        discoverTablet(context.applicationContext)
    }

    fun stop() {
        started = false

        try {
            discoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop discovery", e)
        }

        discoveryListener = null
        nsdManager = null

        try {
            socket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to close socket", e)
        }

        socket = null
        reader = null
        writer = null

        Log.d(TAG, "Watch client stopped")
    }

    private fun discoverTablet(context: Context) {
        postStatus("Searching for tablet")
        Log.d(TAG, "Starting NSD discovery")

        nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery failed code $errorCode")
                postStatus("Discovery failed")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Stop discovery failed code $errorCode")
            }

            override fun onDiscoveryStarted(serviceType: String) {
                Log.d(TAG, "Discovery started")
                postStatus("Searching for tablet")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Discovery stopped")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service found ${serviceInfo.serviceName} ${serviceInfo.serviceType}")

                if (serviceInfo.serviceType != SERVICE_TYPE) {
                    return
                }

                nsdManager?.resolveService(
                    serviceInfo,
                    object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            Log.e(TAG, "Resolve failed code $errorCode")
                            postStatus("Resolve failed")
                        }

                        override fun onServiceResolved(resolvedServiceInfo: NsdServiceInfo) {
                            Log.d(TAG, "Service resolved host=${resolvedServiceInfo.host} port=${resolvedServiceInfo.port}")
                            connectToTablet(context, resolvedServiceInfo)
                        }
                    }
                )
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service lost ${serviceInfo.serviceName}")
                postStatus("Tablet lost")
            }
        }

        nsdManager?.discoverServices(
            SERVICE_TYPE,
            NsdManager.PROTOCOL_DNS_SD,
            discoveryListener
        )
    }

    private fun connectToTablet(context: Context, serviceInfo: NsdServiceInfo) {
        Thread {
            try {
                val host = serviceInfo.host ?: run {
                    Log.e(TAG, "Resolved service had no host")
                    postStatus("No tablet host")
                    return@Thread
                }

                try {
                    discoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
                } catch (_: Exception) {
                }

                Log.d(TAG, "Connecting to host=$host port=${serviceInfo.port}")

                val newSocket = Socket(host, serviceInfo.port)
                val newReader = BufferedReader(InputStreamReader(newSocket.getInputStream()))
                val newWriter = BufferedWriter(OutputStreamWriter(newSocket.getOutputStream()))

                socket = newSocket
                reader = newReader
                writer = newWriter

                val hello = JSONObject()
                    .put("type", "hello")
                    .put("watchId", getWatchId(context))
                    .put("watchName", getWatchName())
                    .put("pairCode", PAIR_CODE)

                sendJson(newWriter, hello)
                Log.d(TAG, "Hello sent $hello")

                while (started && !newSocket.isClosed) {
                    val line = newReader.readLine() ?: break
                    Log.d(TAG, "Message received $line")

                    val message = JSONObject(line)

                    when (message.optString("type")) {
                        "accepted" -> {
                            postStatus("Connected")
                        }

                        "reject" -> {
                            postStatus("Pair failed")
                            newSocket.close()
                            break
                        }

                        "role" -> {
                            val role = message.optString("role")
                            mainHandler.post {
                                listener?.onRoleChanged(role)
                                listener?.onStatusChanged("Connected as $role")
                            }
                        }

                        "play" -> {
                            val play = PlayMessage(
                                playName = message.optString("playName"),
                                assignment = message.optString("assignment"),
                                imageResourceName = message.optString("imageResourceName"),
                                role = message.optString("role")
                            )
                            mainHandler.post {
                                listener?.onPlayReceived(play)
                            }
                        }

                        "pong" -> {
                        }
                    }
                }

                postStatus("Disconnected")
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed", e)
                postStatus("Connection failed")
            }
        }.start()
    }

    private fun getWatchId(context: Context): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown_watch"
    }

    private fun getWatchName(): String {
        return "Watch ${Build.MODEL}"
    }

    private fun postStatus(status: String) {
        mainHandler.post {
            listener?.onStatusChanged(status)
        }
    }

    private fun sendJson(writer: BufferedWriter, json: JSONObject) {
        writer.write(json.toString())
        writer.newLine()
        writer.flush()
    }
}

 */