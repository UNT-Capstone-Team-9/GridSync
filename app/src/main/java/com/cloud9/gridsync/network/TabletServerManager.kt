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

    private const val PREFS_NAME = "tablet_role_assignments"
    private const val KEY_PREFIX = "watch_role_"

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
    private var appContext: Context? = null

    fun start(context: Context) {
        if (started) {
            Log.d(TAG, "Server already started")
            notifyListeners()
            return
        }

        appContext = context.applicationContext
        started = true

        Thread {
            try {
                val socket = ServerSocket(SERVER_PORT)
                serverSocket = socket
                Log.d(TAG, "Server started on port ${socket.localPort}")
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
            Log.e(TAG, "Error closing server", e)
        }

        serverSocket = null

        connections.values.forEach { it.close() }
        connections.clear()
        notifyListeners()
    }

    fun addListener(listener: WatchListListener) {
        listeners.add(listener)
        notifyListeners()
    }

    fun removeListener(listener: WatchListListener) {
        listeners.remove(listener)
    }

    fun getConnectedWatches(): List<ConnectedWatch> {
        return connections.values.map {
            ConnectedWatch(
                watchId = it.watchId,
                watchName = it.watchName,
                ipAddress = it.ipAddress,
                role = it.role
            )
        }
    }

    fun getConnectedRoles(): Set<String> {
        return connections.values.mapNotNull { it.role }.toSet()
    }

    fun assignRole(watchId: String, role: String) {
        saveAssignedRole(watchId, role)

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
            } catch (e: Exception) {
                Log.e(TAG, "Assign role failed", e)
            }
        }.start()
    }

    // ✅ CLEAN SEND (NO "Assignment" LABEL)
    fun sendToRole(role: String, message: String) {
        val connection = connections.values.firstOrNull { it.role == role } ?: return

        Thread {
            try {
                sendJson(
                    connection.writer,
                    JSONObject()
                        .put("type", "play")
                        .put("assignment", message)
                        .put("role", role)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Send failed for role $role", e)
            }
        }.start()
    }

    fun sendPlayToAssigned(play: PlayMessage) {
        connections.values.forEach { connection ->
            val role = connection.role ?: return@forEach

            val assignment = play.assignments[role]
                ?: play.assignments.values.firstOrNull()
                ?: ""

            Thread {
                try {
                    sendJson(
                        connection.writer,
                        JSONObject()
                            .put("type", "play")
                            .put("assignment", assignment)
                            .put("role", role)
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Send play failed", e)
                }
            }.start()
        }
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (started && !socket.isClosed) {
            try {
                val client = socket.accept()
                Thread { handleClient(client) }.start()
            } catch (e: Exception) {
                if (started) Log.e(TAG, "Accept error", e)
            }
        }
    }

    private fun handleClient(socket: Socket) {
        var watchId: String? = null

        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))

            val hello = JSONObject(reader.readLine() ?: return)

            if (hello.optString("type") != "hello") {
                socket.close()
                return
            }

            if (hello.optString("pairCode") != PAIR_CODE) {
                socket.close()
                return
            }

            watchId = hello.optString("watchId")
            val watchName = hello.optString("watchName", "Watch")
            val ip = socket.inetAddress?.hostAddress ?: ""

            val savedRole = getAssignedRole(watchId)

            val connection = ClientConnection(
                socket,
                reader,
                writer,
                watchId,
                watchName,
                ip,
                savedRole
            )

            connections[watchId] = connection

            sendJson(
                writer,
                JSONObject()
                    .put("type", "accepted")
                    .put("watchId", watchId)
            )

            // Restore role automatically
            if (!savedRole.isNullOrBlank()) {
                sendJson(
                    writer,
                    JSONObject()
                        .put("type", "role")
                        .put("role", savedRole)
                )
            }

            notifyListeners()

            while (started && !socket.isClosed) {
                val msg = JSONObject(reader.readLine() ?: break)

                if (msg.optString("type") == "ping") {
                    sendJson(writer, JSONObject().put("type", "pong"))
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Client error", e)
        } finally {
            watchId?.let { connections.remove(it) }
            notifyListeners()
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun saveAssignedRole(watchId: String, role: String) {
        val prefs = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs?.edit()?.putString(KEY_PREFIX + watchId, role)?.apply()
    }

    private fun getAssignedRole(watchId: String): String? {
        val prefs = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs?.getString(KEY_PREFIX + watchId, null)
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