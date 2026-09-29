package com.cloud9.gridsync

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.cloud9.gridsync.network.PointData
import com.cloud9.gridsync.network.WatchClientManager
import com.cloud9.gridsync.ui.WatchPlayView
import kotlin.random.Random

class WatchDashboardActivity : AppCompatActivity(), WatchClientManager.WatchMessageListener {

    private lateinit var playText: TextView
    private lateinit var roleText: TextView
    private lateinit var playNameText: TextView
    private lateinit var playView: WatchPlayView
    private val handler = Handler(Looper.getMainLooper())
    private var currentRole = "Unassigned"

    private val resetRunnable = Runnable {
        playView.clearRoute()
        playNameText.visibility = View.GONE
        showCenteredMessage(if (currentRole == "Unassigned") "Ready for assignment" else "Waiting for play")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_watch_dashboard)

        playText = findViewById(R.id.playText)
        roleText = findViewById(R.id.playerRoleText)
        playNameText = findViewById(R.id.playNameText)
        playView = findViewById(R.id.watchPlayView)
        roleText.text = currentRole
        showCenteredMessage("Scanning for tablet...")

        WatchClientManager.setListener(this)
        WatchClientManager.connect(applicationContext, getOrCreateWatchId())
    }

    override fun onConnectionChanged(isConnected: Boolean) {
        handler.removeCallbacks(resetRunnable)
        if (!isConnected) {
            playView.clearRoute()
            playNameText.visibility = View.GONE
        }
        showCenteredMessage(if (isConnected) "Ready for assignment" else "Waiting for connection...")
    }

    override fun onRoleChanged(role: String) {
        currentRole = role.ifBlank { "Unassigned" }
        roleText.text = currentRole
        if (playView.visibility != View.VISIBLE) {
            showCenteredMessage(if (currentRole == "Unassigned") "Ready for assignment" else "Waiting for play")
        }
    }

    override fun onPlayReceived(
        playName: String,
        playTextMessage: String,
        movements: Map<String, List<PointData>>
    ) {
        handler.removeCallbacks(resetRunnable)

        val route = movements.entries.firstOrNull {
            it.key.trim().equals(currentRole.trim(), ignoreCase = true)
        }?.value ?: movements.values.firstOrNull().orEmpty()

        if (route.size >= 2) {
            playText.visibility = View.GONE
            playNameText.text = playName.ifBlank { "Play" }
            playNameText.visibility = View.VISIBLE
            playView.showRoute(currentRole, route)
        } else {
            playView.clearRoute()
            playNameText.visibility = View.GONE
            val display = buildString {
                if (playName.isNotBlank()) append(playName)
                if (playTextMessage.isNotBlank()) {
                    if (isNotEmpty()) append("\n\n")
                    append(playTextMessage)
                }
            }.ifBlank { "Play received" }
            showCenteredMessage(display)
        }

        handler.postDelayed(resetRunnable, 18_000L)
    }

    override fun onTextMessageReceived(role: String, message: String) {
        handler.removeCallbacks(resetRunnable)
        playView.clearRoute()
        playNameText.visibility = View.GONE
        if (role.isNotBlank()) {
            currentRole = role
            roleText.text = currentRole
        }
        showCenteredMessage(message.ifBlank { "Message received" })
        handler.postDelayed(resetRunnable, 18_000L)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        WatchClientManager.disconnect()
        WatchClientManager.clearListener()
        super.onDestroy()
    }

    private fun showCenteredMessage(text: String) {
        playText.visibility = View.VISIBLE
        playText.text = text.trim()
        playText.textSize = 28f
        playText.gravity = Gravity.CENTER
    }

    private fun getOrCreateWatchId(): String {
        val prefs = getSharedPreferences("watch_prefs", Context.MODE_PRIVATE)
        return prefs.getString("watch_id", null) ?: String.format("%02d", Random.nextInt(100)).also {
            prefs.edit().putString("watch_id", it).apply()
        }
    }
}
