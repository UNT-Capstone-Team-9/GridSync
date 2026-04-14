package com.cloud9.gridsync.network

import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArraySet

object SessionLogManager {

    interface SessionLogListener {
        fun onSessionLogChanged(entries: List<String>)
    }

    private const val MAX_ENTRIES = 30

    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<SessionLogListener>()
    private val entries = mutableListOf<String>()
    private val timeFormat = SimpleDateFormat("h:mm:ss a", Locale.US)

    fun addListener(listener: SessionLogListener) {
        listeners.add(listener)
        notifyListeners()
    }

    fun removeListener(listener: SessionLogListener) {
        listeners.remove(listener)
    }

    fun addEntry(message: String) {
        val timestamp = timeFormat.format(Date())
        val entry = "$timestamp  $message"

        synchronized(entries) {
            entries.add(0, entry)
            while (entries.size > MAX_ENTRIES) {
                entries.removeAt(entries.lastIndex)
            }
        }

        notifyListeners()
    }

    fun getEntries(): List<String> {
        synchronized(entries) {
            return entries.toList()
        }
    }

    private fun notifyListeners() {
        val snapshot = getEntries()
        mainHandler.post {
            listeners.forEach { it.onSessionLogChanged(snapshot) }
        }
    }
}