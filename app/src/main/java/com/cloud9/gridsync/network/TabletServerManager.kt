package com.cloud9.gridsync.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object TabletServerManager {

    private const val TAG = "TabletServerManager"
    const val PAIR_CODE = "CLOUD9"
    const val SERVER_PORT = 5001
    private const val SERVICE_TYPE = "_gridsync._tcp."
    private const val SERVICE_NAME = "GridSync-Tablet"

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
        @Volatile var role: String? = null,
        // One sender thread per watch: messages to a watch go out one at a time and
        // in the order they were sent (important when plays are tapped quickly).
        val sender: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "GridSync-Send").apply { isDaemon = true }
        }
    ) {
        fun close() {
            try {
                sender.shutdown()
            } catch (_: Exception) {
            }
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
    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null

    fun start(context: Context) {
        if (started) {
            notifyListeners()
            return
        }

        appContext = context.applicationContext
        SessionLogManager.init(context.applicationContext)
        started = true

        Thread {
            try {
                serverSocket = ServerSocket(SERVER_PORT)
                registerNsdService()
                SessionLogManager.addEntry("Tablet server ready on local network")
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
        unregisterNsdService()
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
                watchName = displayName(connection),
                ipAddress = connection.ipAddress,
                role = connection.role,
                deviceName = connection.watchName
            )
        }.sortedBy { it.watchName.lowercase() }
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
                assignedWatchId = assignedWatchId,
                assignedWatchName = assignedWatchId?.let { customNameFor(it) }
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
            SessionLogManager.addEntry("${displayName(connection)} assigned to $cleanRole")

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
                SessionLogManager.addEntry("${displayName(connection)} unassigned from $oldRole")
            } else {
                SessionLogManager.addEntry("${displayName(connection)} unassigned")
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

    /**
     * Sends a play to every connected watch that has a role. Each watch gets only its
     * own role-specific assignment and route.
     *
     * @return how many watches the play was sent to (0 if no assigned watch is connected).
     */
    fun sendPlayToAssigned(play: PlayMessage): Int {
        SessionLogManager.addEntry("Sending play ${play.playName}")

        var sentCount = 0

        connections.values.forEach { connection ->
            val role = connection.role?.trim() ?: return@forEach

            val assignment = getAssignmentForRole(play.assignments, role)

            // Send only this watch's assigned route. This keeps payloads small and
            // prevents one player from receiving every position's route.
            val filteredMovements = getMovementsForRole(play.movements, role)

            try {
                connection.sender.execute {
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
                }
                sentCount++
            } catch (e: Exception) {
                Log.e(TAG, "Could not queue play for $role", e)
                SessionLogManager.addEntry("Play send failed for $role")
            }
        }

        return sentCount
    }

    /** Gives a watch a custom name (blank restores the device name). */
    fun renameWatch(watchId: String, newName: String) {
        val context = appContext ?: return
        val connection = connections[watchId]
        val oldName = connection?.let { displayName(it) } ?: customNameFor(watchId) ?: "Watch $watchId"

        WatchNameStore.setCustomName(context, watchId, newName)

        val shownNow = connection?.let { displayName(it) } ?: customNameFor(watchId) ?: "Watch $watchId"
        if (shownNow != oldName) {
            SessionLogManager.addEntry("$oldName renamed to $shownNow")
        }
        notifyListeners()
    }

    fun getCustomWatchName(watchId: String): String? = customNameFor(watchId)

    private fun customNameFor(watchId: String): String? {
        val context = appContext ?: return null
        return WatchNameStore.getCustomName(context, watchId)
    }

    private fun displayName(connection: ClientConnection): String {
        return customNameFor(connection.watchId) ?: connection.watchName
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

                    SessionLogManager.addEntry("${displayName(otherConnection)} removed from $role")
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


    private fun registerNsdService() {
        val context = appContext ?: return
        val manager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        nsdManager = manager

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = SERVICE_NAME
            serviceType = SERVICE_TYPE
            port = SERVER_PORT
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(registeredInfo: NsdServiceInfo) {
                Log.i(TAG, "NSD registered: ${registeredInfo.serviceName}")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "NSD registration failed: $errorCode")
                SessionLogManager.addEntry("Local discovery unavailable; server still running")
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                Log.i(TAG, "NSD unregistered")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "NSD unregistration failed: $errorCode")
            }
        }

        registrationListener = listener
        manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun unregisterNsdService() {
        val manager = nsdManager
        val listener = registrationListener
        if (manager != null && listener != null) {
            try {
                manager.unregisterService(listener)
            } catch (e: Exception) {
                Log.w(TAG, "Unable to unregister NSD service", e)
            }
        }
        registrationListener = null
        nsdManager = null
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
        var connection: ClientConnection? = null

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

            val newConnection = ClientConnection(
                socket = socket,
                reader = reader,
                writer = writer,
                watchId = watchId,
                watchName = watchName,
                ipAddress = ipAddress,
                role = savedRole
            )

            connection = newConnection
            connections[watchId] = newConnection

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

            SessionLogManager.addEntry("${displayName(newConnection)} connected")
            notifyListeners()

            while (started && !socket.isClosed) {
                val line = reader.readLine() ?: break
                val msg = JSONObject(line)

                when (msg.optString("type")) {
                    "ping" -> {
                        sendJson(writer, JSONObject().put("type", "pong"))
                    }

                    "delivery_ack" -> {
                        val ackRole = msg.optString("role", newConnection.role ?: "Unknown").trim()
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
            // Only remove our own entry; a fresh reconnect from the same watch may
            // already have replaced it.
            connection?.let { mine ->
                connections.remove(mine.watchId, mine)
                mine.close()
            }
            val shownName = watchId?.let { customNameFor(it) } ?: watchName
            SessionLogManager.addEntry("$shownName disconnected")
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
        // Several threads can write to the same watch; keep each message in one piece.
        synchronized(writer) {
            writer.write(json.toString())
            writer.newLine()
            writer.flush()
        }
    }
}