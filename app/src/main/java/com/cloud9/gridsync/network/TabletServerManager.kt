package com.cloud9.gridsync.network

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
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
    private const val CONNECTING_WINDOW_MS = 4000L

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
    private val connectingUntilByRole = ConcurrentHashMap<String, Long>()
    private val gson = Gson()

    @Volatile
    private var started = false

    private var serverSocket: ServerSocket? = null
    private var appContext: Context? = null

    fun start(context: Context) {
        if (started) {
            notifyListeners()
            return
        }

        appContext = context.applicationContext
        started = true

        Thread {
            try {
                serverSocket = ServerSocket(SERVER_PORT)
                acceptLoop(serverSocket!!)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start server", e)
                started = false
                SessionLogManager.addEntry("Tablet server failed to start")
            }
        }.start()
    }

    fun stop() {
        started = false

        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }

        serverSocket = null
        connections.values.forEach { it.close() }
        connections.clear()
        connectingUntilByRole.clear()
        SessionLogManager.addEntry("Tablet server stopped")
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
        return connections.values.map { connection ->
            ConnectedWatch(
                watchId = connection.watchId,
                watchName = connection.watchName,
                ipAddress = connection.ipAddress,
                role = connection.role
            )
        }.sortedBy { it.watchName }
    }

    fun getConnectedRoles(): Set<String> {
        return connections.values.mapNotNull { it.role?.trim() }.toSet()
    }

    fun getRoleStatuses(allRoles: List<String>): List<RoleStatusInfo> {
        cleanupExpiredConnectingStates()

        val savedAssignments = getSavedAssignmentsByRole()

        return allRoles.map { rawRole ->
            val role = rawRole.trim()
            val connectedWatch = connections.values.firstOrNull {
                it.role?.trim().equals(role, ignoreCase = true)
            }

            val assignedWatchId = savedAssignments[role]

            val status = when {
                connectedWatch != null && isRoleConnecting(role) -> "Connecting"
                connectedWatch != null -> "Online"
                assignedWatchId != null -> "Offline"
                else -> "Unassigned"
            }

            RoleStatusInfo(
                role = role,
                status = status,
                assignedWatchId = assignedWatchId
            )
        }
    }

    fun getAssignedWatchIdForRole(role: String): String? {
        return getSavedAssignmentsByRole()[role.trim()]
    }

    fun assignRole(watchId: String, role: String) {
        val cleanRole = role.trim()
        if (cleanRole.isBlank()) return

        val prefs = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) ?: return

        clearRoleFromOtherWatches(cleanRole, watchId, prefs)
        prefs.edit().putString(KEY_PREFIX + watchId, cleanRole).apply()

        connections[watchId]?.let { connection ->
            connection.role = cleanRole
            markRoleConnecting(cleanRole)
            SessionLogManager.addEntry("${connection.watchName} assigned to $cleanRole")

            Thread {
                try {
                    sendJson(
                        connection.writer,
                        JSONObject()
                            .put("type", "role")
                            .put("role", cleanRole)
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Assign role failed", e)
                    SessionLogManager.addEntry("Failed to assign $cleanRole")
                }
            }.start()
        }

        notifyListeners()
    }

    fun unassignRole(watchId: String) {
        val prefs = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) ?: return
        prefs.edit().remove(KEY_PREFIX + watchId).apply()

        connections[watchId]?.let { connection ->
            val oldRole = connection.role
            connection.role = null

            Thread {
                try {
                    sendJson(
                        connection.writer,
                        JSONObject()
                            .put("type", "role")
                            .put("role", "Unassigned")
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Unassign role failed", e)
                }
            }.start()

            if (!oldRole.isNullOrBlank()) {
                SessionLogManager.addEntry("${connection.watchName} unassigned from $oldRole")
            } else {
                SessionLogManager.addEntry("${connection.watchName} unassigned")
            }
        }

        notifyListeners()
    }

    fun sendToRole(role: String, message: String) {
        val cleanRole = role.trim()

        val matchingConnections = connections.values.filter {
            it.role?.trim().equals(cleanRole, ignoreCase = true)
        }

        matchingConnections.forEach { connection ->
            Thread {
                try {
                    sendJson(
                        connection.writer,
                        JSONObject()
                            .put("type", "text_message")
                            .put("role", cleanRole)
                            .put("message", message)
                    )
                    SessionLogManager.addEntry("Message sent to $cleanRole")
                } catch (e: Exception) {
                    Log.e(TAG, "Send failed for role $cleanRole", e)
                    SessionLogManager.addEntry("Send failed for $cleanRole")
                }
            }.start()
        }
    }

    fun sendPlayToAssigned(play: PlayMessage) {
        SessionLogManager.addEntry("Sending play ${play.playName}")

        connections.values.forEach { connection ->
            val role = connection.role?.trim() ?: return@forEach

            val assignment = getAssignmentForRole(play.assignments, role)

            val filteredMovements = if (role.equals("QB", ignoreCase = true)) {
                play.movements
            } else {
                getMovementsForRole(play.movements, role)
            }

            Thread {
                try {
                    sendJson(
                        connection.writer,
                        JSONObject()
                            .put("type", "play")
                            .put("playName", play.playName)
                            .put("assignment", assignment)
                            .put("role", role)
                            .put("movements", gson.toJson(filteredMovements))
                    )
                    SessionLogManager.addEntry("Play ${play.playName} sent to $role")
                } catch (e: Exception) {
                    Log.e(TAG, "Send play failed", e)
                    SessionLogManager.addEntry("Play send failed for $role")
                }
            }.start()
        }
    }

    private fun clearRoleFromOtherWatches(
        role: String,
        keepWatchId: String,
        prefs: android.content.SharedPreferences
    ) {
        val editor = prefs.edit()

        prefs.all.forEach { entry ->
            val key = entry.key
            val value = entry.value as? String ?: return@forEach

            if (!key.startsWith(KEY_PREFIX)) return@forEach

            val otherWatchId = key.removePrefix(KEY_PREFIX)
            if (otherWatchId == keepWatchId) return@forEach

            if (value.trim().equals(role, ignoreCase = true)) {
                editor.remove(key)

                connections[otherWatchId]?.let { otherConnection ->
                    otherConnection.role = null

                    Thread {
                        try {
                            sendJson(
                                otherConnection.writer,
                                JSONObject()
                                    .put("type", "role")
                                    .put("role", "Unassigned")
                            )
                        } catch (_: Exception) {
                        }
                    }.start()

                    SessionLogManager.addEntry("${otherConnection.watchName} removed from $role")
                }
            }
        }

        editor.apply()
    }

    private fun getAssignmentForRole(assignments: Map<String, String>, role: String): String {
        return assignments.entries.firstOrNull {
            it.key.trim().equals(role.trim(), ignoreCase = true)
        }?.value ?: "Follow your assigned route"
    }

    private fun getMovementsForRole(
        movements: Map<String, List<PointData>>,
        role: String
    ): Map<String, List<PointData>> {
        val match = movements.entries.firstOrNull {
            it.key.trim().equals(role.trim(), ignoreCase = true)
        }

        return if (match != null) {
            mapOf(match.key to match.value)
        } else {
            emptyMap()
        }
    }

    private fun acceptLoop(server: ServerSocket) {
        while (started && !server.isClosed) {
            try {
                val client = server.accept()
                Thread { handleClient(client) }.start()
            } catch (e: Exception) {
                if (started) {
                    Log.e(TAG, "Accept error", e)
                }
            }
        }
    }

    private fun handleClient(socket: Socket) {
        var watchId: String? = null
        var watchName = "Watch"

        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))

            val helloLine = reader.readLine() ?: return
            val hello = JSONObject(helloLine)

            if (hello.optString("type") != "hello") {
                socket.close()
                return
            }

            if (hello.optString("pairCode") != PAIR_CODE) {
                socket.close()
                return
            }

            watchId = hello.optString("watchId")
            watchName = hello.optString("watchName", "Watch")
            val ipAddress = socket.inetAddress?.hostAddress ?: ""

            val savedRole = getAssignedRole(watchId)

            val connection = ClientConnection(
                socket = socket,
                reader = reader,
                writer = writer,
                watchId = watchId,
                watchName = watchName,
                ipAddress = ipAddress,
                role = savedRole
            )

            connections[watchId] = connection

            if (!savedRole.isNullOrBlank()) {
                markRoleConnecting(savedRole)
            }

            sendJson(
                writer,
                JSONObject()
                    .put("type", "accepted")
                    .put("watchId", watchId)
            )

            if (!savedRole.isNullOrBlank()) {
                sendJson(
                    writer,
                    JSONObject()
                        .put("type", "role")
                        .put("role", savedRole)
                )
            }

            SessionLogManager.addEntry("$watchName connected")
            notifyListeners()

            while (started && !socket.isClosed) {
                val line = reader.readLine() ?: break
                val msg = JSONObject(line)

                when (msg.optString("type")) {
                    "ping" -> {
                        sendJson(writer, JSONObject().put("type", "pong"))
                    }

                    "delivery_ack" -> {
                        val ackRole = msg.optString("role", connection.role ?: "Unknown").trim()
                        val ackKind = msg.optString("kind", "content")
                        val ackName = msg.optString("name", "").trim()

                        val ackText = when (ackKind) {
                            "play" -> {
                                if (ackName.isBlank()) "$ackRole received play"
                                else "$ackRole received play $ackName"
                            }
                            "text_message" -> "$ackRole received coach message"
                            else -> "$ackRole received content"
                        }

                        SessionLogManager.addEntry(ackText)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Client error", e)
        } finally {
            watchId?.let { connections.remove(it) }
            SessionLogManager.addEntry("$watchName disconnected")
            notifyListeners()
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun markRoleConnecting(role: String) {
        val cleanRole = role.trim()
        connectingUntilByRole[cleanRole] = System.currentTimeMillis() + CONNECTING_WINDOW_MS
    }

    private fun isRoleConnecting(role: String): Boolean {
        val until = connectingUntilByRole[role.trim()] ?: return false
        return until > System.currentTimeMillis()
    }

    private fun cleanupExpiredConnectingStates() {
        val now = System.currentTimeMillis()
        val iterator = connectingUntilByRole.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value <= now) {
                iterator.remove()
            }
        }
    }

    private fun getAssignedRole(watchId: String): String? {
        val prefs = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs?.getString(KEY_PREFIX + watchId, null)
    }

    private fun getSavedAssignmentsByRole(): Map<String, String> {
        val prefs = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) ?: return emptyMap()
        val result = mutableMapOf<String, String>()

        prefs.all.forEach { entry ->
            val key = entry.key
            val value = entry.value as? String ?: return@forEach

            if (key.startsWith(KEY_PREFIX) && value.isNotBlank()) {
                val watchId = key.removePrefix(KEY_PREFIX)
                result[value.trim()] = watchId
            }
        }

        return result
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