package com.cloud9.gridsync

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.cloud9.gridsync.network.PointData
import com.cloud9.gridsync.network.WatchClientManager
import kotlin.random.Random

class WatchDashboardActivity : AppCompatActivity(),
    WatchClientManager.WatchMessageListener {

    private lateinit var playText: TextView
    private lateinit var playerRoleText: TextView

    private var currentRole: String = "Unassigned"

    private val mainHandler = Handler(Looper.getMainLooper())

    private val resetRunnable = Runnable {
        showCenteredMessage("Ready for assignment")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_watch_dashboard)

        playText = findViewById(R.id.playText)
        playerRoleText = findViewById(R.id.playerRoleText)

        val watchId = getOrCreateWatchId()

        currentRole = "Unassigned"
        playerRoleText.text = currentRole

        showCenteredMessage("Scanning for tablet...")

        WatchClientManager.setListener(this)
        WatchClientManager.connect(applicationContext, watchId)
    }

    override fun onConnectionChanged(isConnected: Boolean) {
        runOnUiThread {
            mainHandler.removeCallbacks(resetRunnable)

            if (isConnected) {
                showCenteredMessage("Ready for assignment")
            } else {
                showCenteredMessage("Waiting for connection...")
            }
        }
    }

    override fun onRoleChanged(role: String) {
        runOnUiThread {
            currentRole = role
            playerRoleText.text = role
        }
    }

    override fun onPlayReceived(
        playName: String,
        playTextMessage: String,
        movements: Map<String, List<PointData>>
    ) {
        runOnUiThread {
            mainHandler.removeCallbacks(resetRunnable)

            val displayText = if (playName.isNotBlank()) {
                "$playName\n\n$playTextMessage"
            } else {
                playTextMessage
            }

            showCenteredMessage(displayText)

            mainHandler.postDelayed(
                resetRunnable,
                18000L
            )
        }
    }

    override fun onTextMessageReceived(role: String, message: String) {
        runOnUiThread {
            mainHandler.removeCallbacks(resetRunnable)

            currentRole = role
            playerRoleText.text = role

            showCenteredMessage(message)

            mainHandler.postDelayed(
                resetRunnable,
                18000L
            )
        }
    }

    private fun showCenteredMessage(text: String) {
        playText.text = text.trim()
        playText.textSize = 42f
        playText.gravity = Gravity.CENTER
        playerRoleText.text = currentRole
    }

    private fun getOrCreateWatchId(): String {
        val prefs = getSharedPreferences(
            "watch_prefs",
            Context.MODE_PRIVATE
        )

        var id = prefs.getString("watch_id", null)

        if (id == null) {
            id = String.format(
                "%02d",
                Random.nextInt(0, 100)
            )

            prefs.edit()
                .putString("watch_id", id)
                .apply()
        }

        return id ?: "00"
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(resetRunnable)
        WatchClientManager.clearListener()
        super.onDestroy()
    }
}