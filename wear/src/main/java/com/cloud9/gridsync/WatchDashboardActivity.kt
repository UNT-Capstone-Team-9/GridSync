package com.cloud9.gridsync

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.cloud9.gridsync.network.WatchClientManager
import kotlin.random.Random

class WatchDashboardActivity : AppCompatActivity(),
    WatchClientManager.WatchMessageListener {

    private lateinit var playText: TextView
    private lateinit var roleText: TextView

    private val handler = Handler(Looper.getMainLooper())

    private val resetRunnable = Runnable {
        showCenteredMessage("Ready for assignment")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_watch_dashboard)

        playText = findViewById(R.id.playText)
        roleText = findViewById(R.id.playerRoleText)

        val watchId = getOrCreateWatchId()

        roleText.text = "Unassigned"
        showCenteredMessage("Scanning for tablet...")

        WatchClientManager.setListener(this)
        WatchClientManager.connect(applicationContext, watchId)
    }

    override fun onConnectionChanged(isConnected: Boolean) {
        handler.removeCallbacks(resetRunnable)

        if (isConnected) {
            showCenteredMessage("Ready for assignment")
        } else {
            showCenteredMessage("Waiting for connection...")
        }
    }

    override fun onRoleChanged(role: String) {
        roleText.text = role
    }

    override fun onPlayReceived(playMessage: String) {
        handler.removeCallbacks(resetRunnable)
        showCenteredMessage(playMessage)
        handler.postDelayed(resetRunnable, 15000)
    }

    override fun onDestroy() {
        super.onDestroy()
        WatchClientManager.clearListener()
        handler.removeCallbacksAndMessages(null)
    }

    private fun showCenteredMessage(text: String) {
        playText.text = text.trim()
        playText.textSize = 42f
        playText.gravity = Gravity.CENTER
    }

    private fun getOrCreateWatchId(): String {
        val prefs = getSharedPreferences("watch_prefs", Context.MODE_PRIVATE)
        var id = prefs.getString("watch_id", null)

        if (id == null) {
            id = String.format("%02d", Random.nextInt(0, 100))
            prefs.edit().putString("watch_id", id).apply()
        }

        return id ?: "00"
    }
}