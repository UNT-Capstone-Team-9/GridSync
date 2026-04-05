// NEW EMULATOR FALLBACK CODE HERE
// This code uses 10.0.2.2 and port 6001.

package com.cloud9.gridsync.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
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

    const val SERVICE_TYPE = "_gridsync._tcp"
    private const val SERVICE_NAME = "GridSyncTablet"
    const val PAIR_CODE = "CLOUD9"

    private const val SERVER_PORT = 5001

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
    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null

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
                registerService(context.applicationContext, socket.localPort)
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
            registrationListener?.let { listener ->
                nsdManager?.unregisterService(listener)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister service", e)
        }

        registrationListener = null
        nsdManager = null

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

    fun assignRole(watchId: String, role: String): Boolean {
        val connection = connections[watchId] ?: return false
        connection.role = role

        return try {
            sendJson(
                connection.writer,
                JSONObject()
                    .put("type", "role")
                    .put("role", role)
            )
            Log.d(TAG, "Assigned role $role to ${connection.watchName}")
            notifyListeners()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to assign role", e)
            false
        }
    }

    fun sendPlayToAssigned(play: PlayMessage): Int {
        var sentCount = 0

        connections.values.forEach { connection ->
            if (!connection.role.isNullOrBlank()) {
                try {
                    sendJson(
                        connection.writer,
                        JSONObject()
                            .put("type", "play")
                            .put("playName", play.playName)
                            .put("assignment", play.assignment)
                            .put("imageResourceName", play.imageResourceName)
                            .put("role", connection.role ?: "")
                    )
                    sentCount++
                    Log.d(TAG, "Sent play to ${connection.watchName}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed sending play to ${connection.watchName}", e)
                }
            }
        }

        return sentCount
    }

    private fun registerService(context: Context, port: Int) {
        nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = SERVICE_NAME
            serviceType = SERVICE_TYPE
            setPort(port)
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(nsdServiceInfo: NsdServiceInfo) {
                Log.d(TAG, "NSD service registered as ${nsdServiceInfo.serviceName}")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "NSD registration failed code $errorCode")
            }

            override fun onServiceUnregistered(nsdServiceInfo: NsdServiceInfo) {
                Log.d(TAG, "NSD service unregistered")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "NSD unregistration failed code $errorCode")
            }
        }

        nsdManager?.registerService(
            serviceInfo,
            NsdManager.PROTOCOL_DNS_SD,
            registrationListener
        )
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


/*
// ORIGINAL CODE IS HERE For real devices
// This code uses NSD to find the tablet automatically.

package com.cloud9.gridsync.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
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

    const val SERVICE_TYPE = "_gridsync._tcp"
    private const val SERVICE_NAME = "GridSyncTablet"
    const val PAIR_CODE = "CLOUD9"

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
    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null

    fun start(context: Context) {
        if (started) {
            Log.d(TAG, "Server already started")
            notifyListeners()
            return
        }

        started = true

        Thread {
            try {
                val socket = ServerSocket(0)
                serverSocket = socket
                Log.d(TAG, "Server socket started on port ${socket.localPort}")
                registerService(context.applicationContext, socket.localPort)
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
            registrationListener?.let { listener ->
                nsdManager?.unregisterService(listener)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister service", e)
        }

        registrationListener = null
        nsdManager = null

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

    fun assignRole(watchId: String, role: String): Boolean {
        val connection = connections[watchId] ?: return false
        connection.role = role

        return try {
            sendJson(
                connection.writer,
                JSONObject()
                    .put("type", "role")
                    .put("role", role)
            )
            Log.d(TAG, "Assigned role $role to ${connection.watchName}")
            notifyListeners()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to assign role", e)
            false
        }
    }

    fun sendPlayToAssigned(play: PlayMessage): Int {
        var sentCount = 0

        connections.values.forEach { connection ->
            if (!connection.role.isNullOrBlank()) {
                try {
                    sendJson(
                        connection.writer,
                        JSONObject()
                            .put("type", "play")
                            .put("playName", play.playName)
                            .put("assignment", play.assignment)
                            .put("imageResourceName", play.imageResourceName)
                            .put("role", connection.role ?: "")
                    )
                    sentCount++
                    Log.d(TAG, "Sent play to ${connection.watchName}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed sending play to ${connection.watchName}", e)
                }
            }
        }

        return sentCount
    }

    private fun registerService(context: Context, port: Int) {
        nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = SERVICE_NAME
            serviceType = SERVICE_TYPE
            setPort(port)
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(nsdServiceInfo: NsdServiceInfo) {
                Log.d(TAG, "NSD service registered as ${nsdServiceInfo.serviceName}")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "NSD registration failed code $errorCode")
            }

            override fun onServiceUnregistered(nsdServiceInfo: NsdServiceInfo) {
                Log.d(TAG, "NSD service unregistered")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "NSD unregistration failed code $errorCode")
            }
        }

        nsdManager?.registerService(
            serviceInfo,
            NsdManager.PROTOCOL_DNS_SD,
            registrationListener
        )
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
*/
