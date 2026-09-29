package com.cloud9.gridsync.network

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Keeps the Session Activity Log.
 *
 * A "session" is one run of the app. Sessions are saved to a small file so the
 * log can be browsed by date later ("Today", "Yesterday", "September 25, 2026")
 * and individual sessions (or everything) can be deleted.
 */
object SessionLogManager {

    interface SessionLogListener {
        fun onSessionLogChanged(entries: List<String>)
    }

    data class LogEntry(val time: Long, val message: String)

    data class LogSession(
        val id: Long,
        val startTime: Long,
        val entries: MutableList<LogEntry> = mutableListOf()
    )

    private const val MAX_SESSIONS = 60
    private const val MAX_ENTRIES_PER_SESSION = 300
    private const val FILE_NAME = "session_logs.json"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<SessionLogListener>()
    private val lock = Any()

    // Oldest session first.
    private val sessions = mutableListOf<LogSession>()
    private var currentSession: LogSession? = null

    private var appContext: Context? = null
    private var loaded = false

    private val saveScheduled = AtomicBoolean(false)
    private val saveExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "GridSync-LogSave").apply { isDaemon = true }
    }

    private val timeFormat = SimpleDateFormat("h:mm:ss a", Locale.US)

    /** Loads saved sessions. Safe to call many times. */
    fun init(context: Context) {
        synchronized(lock) {
            appContext = context.applicationContext
            if (loaded) return
            loaded = true

            val saved = readFromDisk()
            if (saved.isNotEmpty()) {
                sessions.addAll(0, saved)
                sessions.sortBy { it.startTime }
            }
        }
        notifyListeners()
    }

    fun addListener(listener: SessionLogListener) {
        listeners.add(listener)
        notifyListeners()
    }

    fun removeListener(listener: SessionLogListener) {
        listeners.remove(listener)
    }

    fun addEntry(message: String) {
        val now = System.currentTimeMillis()

        synchronized(lock) {
            val session = currentSession ?: LogSession(
                id = maxOf(now, (sessions.lastOrNull()?.id ?: 0L) + 1L),
                startTime = now
            ).also {
                sessions.add(it)
                currentSession = it
            }

            session.entries.add(LogEntry(now, message))
            while (session.entries.size > MAX_ENTRIES_PER_SESSION) {
                session.entries.removeAt(0)
            }

            while (sessions.size > MAX_SESSIONS) {
                val oldest = sessions.first()
                if (oldest === session) break
                sessions.removeAt(0)
            }
        }

        scheduleSave()
        notifyListeners()
    }

    /** All sessions, oldest first. Returns copies that are safe to read on any thread. */
    fun getSessions(): List<LogSession> {
        synchronized(lock) {
            return sessions.map { it.copy(entries = it.entries.toMutableList()) }
        }
    }

    fun getCurrentSessionId(): Long? {
        synchronized(lock) {
            return currentSession?.id
        }
    }

    /** Entries of the running session, newest first, as "time  message" text. */
    fun getEntries(): List<String> {
        synchronized(lock) {
            return currentSession?.entries
                ?.asReversed()
                ?.map { formatEntry(it) }
                ?: emptyList()
        }
    }

    fun formatEntry(entry: LogEntry): String {
        return "${timeFormat.format(Date(entry.time))}  ${entry.message}"
    }

    fun deleteSession(sessionId: Long) {
        synchronized(lock) {
            sessions.removeAll { it.id == sessionId }
            if (currentSession?.id == sessionId) currentSession = null
        }
        scheduleSave()
        notifyListeners()
    }

    fun deleteAll() {
        synchronized(lock) {
            sessions.clear()
            currentSession = null
        }
        scheduleSave()
        notifyListeners()
    }

    private fun notifyListeners() {
        val snapshot = getEntries()
        mainHandler.post {
            listeners.forEach { it.onSessionLogChanged(snapshot) }
        }
    }

    // ---- Persistence -------------------------------------------------------

    private fun scheduleSave() {
        if (appContext == null) return
        if (!saveScheduled.compareAndSet(false, true)) return

        saveExecutor.execute {
            try {
                // Small delay so a burst of log lines is written once.
                Thread.sleep(300)
            } catch (_: InterruptedException) {
            }
            saveScheduled.set(false)
            writeToDisk()
        }
    }

    private fun logFile(): File? {
        val context = appContext ?: return null
        return File(context.filesDir, FILE_NAME)
    }

    private fun writeToDisk() {
        val file = logFile() ?: return

        val json = synchronized(lock) {
            val array = JSONArray()
            sessions.forEach { session ->
                val entries = JSONArray()
                session.entries.forEach { entry ->
                    entries.put(
                        JSONObject()
                            .put("t", entry.time)
                            .put("m", entry.message)
                    )
                }
                array.put(
                    JSONObject()
                        .put("id", session.id)
                        .put("start", session.startTime)
                        .put("entries", entries)
                )
            }
            array.toString()
        }

        try {
            val temp = File(file.parentFile, "$FILE_NAME.tmp")
            temp.writeText(json)
            if (!temp.renameTo(file)) {
                file.writeText(json)
                temp.delete()
            }
        } catch (_: Exception) {
            // Logging must never crash the app.
        }
    }

    private fun readFromDisk(): List<LogSession> {
        val file = logFile() ?: return emptyList()
        if (!file.exists()) return emptyList()

        return try {
            val array = JSONArray(file.readText())
            (0 until array.length()).map { index ->
                val obj = array.getJSONObject(index)
                val entriesJson = obj.getJSONArray("entries")
                val entries = (0 until entriesJson.length()).map { i ->
                    val e = entriesJson.getJSONObject(i)
                    LogEntry(e.getLong("t"), e.getString("m"))
                }.toMutableList()

                LogSession(
                    id = obj.getLong("id"),
                    startTime = obj.getLong("start"),
                    entries = entries
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
