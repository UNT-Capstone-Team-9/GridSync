package com.cloud9.gridsync.network

import android.content.Context
import android.net.wifi.WifiManager
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
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread

object WatchClientManager {

    private const val TAG = "WatchClientManager"
    private const val SERVER_PORT = 5001
    private const val PAIR_CODE = "CLOUD9"
    private const val RECONNECT_DELAY_MS = 5000L

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

    private var listener: WatchMessageListener? = null
    private var appContext: Context? = null
    private var watchId: String? = null

    @Volatile
    private var shouldRun = false

    @Volatile
    private var connected = false

    @Volatile
    private var socket: Socket? = null

    @Volatile
    private var writer: BufferedWriter? = null

    private val reconnectRunnable = Runnable {
        if (shouldRun && !connected) {
            scanAndConnect()
        }
    }

    fun setListener(listener: WatchMessageListener) {
        this.listener = listener
    }

    fun clearListener() {
        this.listener = null
    }

    fun connect(context: Context, watchId: String) {
        this.appContext = context.applicationContext
        this.watchId = watchId
        shouldRun = true
        mainHandler.removeCallbacks(reconnectRunnable)
        scanAndConnect()
    }

    fun disconnect() {
        shouldRun = false
        connected = false
        mainHandler.removeCallbacks(reconnectRunnable)
        closeSocket()
        postConnection(false)
    }

    private fun scanAndConnect() {
        val context = appContext ?: return
        val localWatchId = watchId ?: return

        thread {
            try {
                val wifiManager =
                    context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

                val ipInt = wifiManager.connectionInfo.ipAddress
                val ipAddress = intToIp(ipInt)

                if (ipAddress.isBlank()) {
                    scheduleReconnect()
                    return@thread
                }

                val prefix = ipAddress.substringBeforeLast(".")
                Log.d(TAG, "Scanning subnet $prefix.x")

                for (i in 1..254) {
                    if (!shouldRun || connected) return@thread

                    val host = "$prefix.$i"

                    if (tryConnect(host, SERVER_PORT, localWatchId)) {
                        return@thread
                    }
                }

                scheduleReconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Scan failed", e)
                scheduleReconnect()
            }
        }
    }

    private fun tryConnect(host: String, port: Int, watchId: String): Boolean {
        return try {
            val s = Socket()
            s.connect(InetSocketAddress(host, port), 300)

            val localWriter = BufferedWriter(OutputStreamWriter(s.getOutputStream()))
            val reader = BufferedReader(InputStreamReader(s.getInputStream()))

            val hello = JSONObject()
                .put("type", "hello")
                .put("pairCode", PAIR_CODE)
                .put("watchId", watchId)
                .put("watchName", "Watch-$watchId")

            localWriter.write(hello.toString())
            localWriter.newLine()
            localWriter.flush()

            val responseLine = reader.readLine()
            val response = JSONObject(responseLine ?: "{}")

            if (response.optString("type") != "accepted") {
                s.close()
                return false
            }

            socket = s
            writer = localWriter
            connected = true
            postConnection(true)

            listenForMessages(reader)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun listenForMessages(reader: BufferedReader) {
        thread {
            try {
                while (shouldRun && connected) {
                    val line = reader.readLine() ?: break
                    val message = JSONObject(line)

                    when (message.optString("type")) {
                        "role" -> {
                            val role = message.optString("role", "Unassigned")
                            postRole(role)
                        }

                        "play" -> {
                            val role = message.optString("role", "Unassigned")
                            val playName = message.optString("playName", "")
                            val assignment = message.optString("assignment", "")
                            val movementJson = message.optString("movements", "{}")

                            val movementType = object : TypeToken<Map<String, List<PointData>>>() {}.type
                            val movements: Map<String, List<PointData>> = try {
                                gson.fromJson(movementJson, movementType) ?: emptyMap()
                            } catch (_: Exception) {
                                emptyMap()
                            }

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

                        "pong" -> {
                            Log.d(TAG, "Received pong")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Listen failed", e)
            } finally {
                connected = false
                closeSocket()
                postConnection(false)
                if (shouldRun) {
                    scheduleReconnect()
                }
            }
        }
    }

    private fun sendDeliveryAck(role: String, kind: String, name: String) {
        thread {
            try {
                val currentWriter = writer ?: return@thread
                val ack = JSONObject()
                    .put("type", "delivery_ack")
                    .put("role", role)
                    .put("kind", kind)
                    .put("name", name)

                synchronized(currentWriter) {
                    currentWriter.write(ack.toString())
                    currentWriter.newLine()
                    currentWriter.flush()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ack send failed", e)
            }
        }
    }

    private fun scheduleReconnect() {
        mainHandler.removeCallbacks(reconnectRunnable)
        mainHandler.postDelayed(reconnectRunnable, RECONNECT_DELAY_MS)
    }

    private fun closeSocket() {
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
        writer = null
    }

    private fun postConnection(isConnected: Boolean) {
        mainHandler.post {
            listener?.onConnectionChanged(isConnected)
        }
    }

    private fun postRole(role: String) {
        mainHandler.post {
            listener?.onRoleChanged(role)
        }
    }

    private fun postPlay(
        playName: String,
        playTextMessage: String,
        movements: Map<String, List<PointData>>
    ) {
        mainHandler.post {
            listener?.onPlayReceived(playName, playTextMessage, movements)
        }
    }

    private fun postTextMessage(role: String, message: String) {
        mainHandler.post {
            listener?.onTextMessageReceived(role, message)
        }
    }

    private fun intToIp(ip: Int): String {
        return ((ip and 0xFF).toString() + "." +
                (ip shr 8 and 0xFF) + "." +
                (ip shr 16 and 0xFF) + "." +
                (ip shr 24 and 0xFF))
    }
}