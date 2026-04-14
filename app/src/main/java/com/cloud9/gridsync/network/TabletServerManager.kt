package com.cloud9.gridsync.network

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

object TabletServerManager {

    private const val TAG = "TabletServerManager"

    const val PAIR_CODE = "CLOUD9"
    const val SERVER_PORT = 5001

    interface WatchListListener {
        fun onWatchListChanged(watches: List<ConnectedWatch>)
    }

    private data class ClientConnection(
        val socket: Socket,
        val reader: BufferedReader,
        val writer: BufferedWriter,
        val watchId: String,
        val watchName: String,
        val ipAddress: String,
        @Volatile var role: String? = null
    ) {
        fun close() {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<WatchListListener>()
    private val connections = ConcurrentHashMap<String, ClientConnection>()

    @Volatile
    private var started = false

    private var serverSocket: ServerSocket? = null

    fun start(context: Context) {
        if (started) {
            Log.d(TAG, "Server already started")
            notifyListeners()
            return
        }

        started = true

        Thread {
            try {
                val socket = ServerSocket(SERVER_PORT)
                serverSocket = socket
                Log.d(TAG, "Server socket started on port ${socket.localPort}")
                acceptLoop(socket)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start server", e)
                started = false
            }
        }.start()
    }

    fun stop() {
        started = false

        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to close server socket", e)
        }

        serverSocket = null

        connections.values.forEach { it.close() }
        connections.clear()
        notifyListeners()

        Log.d(TAG, "Server stopped")
    }

    fun addListener(listener: WatchListListener) {
        listeners.add(listener)
        notifyListeners()
    }

    fun removeListener(listener: WatchListListener) {
        listeners.remove(listener)
    }

    fun getConnectedWatches(): List<ConnectedWatch> {
        return connections.values
            .sortedWith(compareBy<ClientConnection> { it.role == null }.thenBy { it.watchName.lowercase() })
            .map {
                ConnectedWatch(
                    watchId = it.watchId,
                    watchName = it.watchName,
                    ipAddress = it.ipAddress,
                    role = it.role
                )
            }
    }

    fun assignRole(watchId: String, role: String) {
        val connection = connections[watchId] ?: return
        connection.role = role
        notifyListeners()

        Thread {
            try {
                sendJson(
                    connection.writer,
                    JSONObject()
                        .put("type", "role")
                        .put("role", role)
                )
                Log.d(TAG, "Assigned role $role to ${connection.watchName}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to assign role", e)
            }
        }.start()
    }

    fun sendPlayToAssigned(play: PlayMessage) {
        connections.values.forEach { connection ->
            val role = connection.role ?: return@forEach
            if (role.isBlank()) return@forEach
            val assignment = play.assignments[role] ?: play.assignments.values.firstOrNull() ?: ""
            Thread {
                try {
                    sendJson(
                        connection.writer,
                        JSONObject()
                            .put("type", "play")
                            .put("playName", play.playName)
                            .put("assignment", assignment)
                            .put("imageResourceName", play.imageResourceName)
                            .put("role", role)
                    )
                    Log.d(TAG, "Sent play '${play.playName}' to ${connection.watchName} as $role")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed sending play to ${connection.watchName}", e)
                }
            }.start()
        }
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (started && !socket.isClosed) {
            try {
                val client = socket.accept()
                Log.d(TAG, "Client accepted from ${client.inetAddress?.hostAddress}")
                Thread {
                    handleClient(client)
                }.start()
            } catch (e: Exception) {
                if (started) {
                    Log.e(TAG, "Accept loop failed", e)
                }
            }
        }
    }

    private fun handleClient(socket: Socket) {
        var watchId: String? = null

        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))

            val helloLine = reader.readLine() ?: run {
                socket.close()
                return
            }

            Log.d(TAG, "Received hello line $helloLine")

            val hello = JSONObject(helloLine)

            if (hello.optString("type") != "hello") {
                sendJson(
                    writer,
                    JSONObject()
                        .put("type", "reject")
                        .put("reason", "bad_hello")
                )
                socket.close()
                return
            }

            val pairCode = hello.optString("pairCode")
            if (pairCode != PAIR_CODE) {
                sendJson(
                    writer,
                    JSONObject()
                        .put("type", "reject")
                        .put("reason", "bad_code")
                )
                socket.close()
                return
            }

            watchId = hello.optString("watchId")
            val watchName = hello.optString("watchName").ifBlank { "Unknown Watch" }
            val ipAddress = socket.inetAddress?.hostAddress ?: "Unknown IP"

            connections[watchId]?.close()

            val connection = ClientConnection(
                socket = socket,
                reader = reader,
                writer = writer,
                watchId = watchId,
                watchName = watchName,
                ipAddress = ipAddress
            )

            connections[watchId] = connection
            Log.d(TAG, "Watch connected $watchName $ipAddress")

            sendJson(
                writer,
                JSONObject()
                    .put("type", "accepted")
                    .put("watchId", watchId)
                    .put("pairCode", PAIR_CODE)
            )

            notifyListeners()

            while (started && !socket.isClosed) {
                val line = reader.readLine() ?: break
                val message = JSONObject(line)

                when (message.optString("type")) {
                    "ping" -> {
                        sendJson(
                            writer,
                            JSONObject().put("type", "pong")
                        )
                    }

                    "disconnect" -> {
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Client handler failed", e)
        } finally {
            if (watchId != null) {
                connections.remove(watchId)
                notifyListeners()
            }

            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun notifyListeners() {
        val snapshot = getConnectedWatches()
        mainHandler.post {
            listeners.forEach { it.onWatchListChanged(snapshot) }
        }
    }

    private fun sendJson(writer: BufferedWriter, json: JSONObject) {
        writer.write(json.toString())
        writer.newLine()
        writer.flush()
    }
}