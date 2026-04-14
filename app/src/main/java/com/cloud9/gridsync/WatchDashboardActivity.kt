package com.cloud9.gridsync

import android.content.Context
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.cloud9.gridsync.network.WatchClientManager
import kotlin.random.Random

class WatchDashboardActivity : AppCompatActivity(), WatchClientManager.WatchMessageListener {

    private lateinit var playText: TextView
    private lateinit var roleText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_watch_dashboard)

        playText = findViewById(R.id.playText)
        roleText = findViewById(R.id.playerRoleText)

        val watchId = getOrCreateWatchId()

        roleText.text = "Unassigned"
        playText.text = "Scanning for tablet..."

        WatchClientManager.setListener(this)
        WatchClientManager.connect(applicationContext, watchId)
    }

    override fun onConnectionChanged(isConnected: Boolean) {
        playText.text = if (isConnected) {
            "Ready for assignment"
        } else {
            "Waiting for connection..."
        }
    }

    override fun onRoleChanged(role: String) {
        roleText.text = role
    }

    override fun onPlayReceived(playMessage: String) {
        playText.text = playMessage
    }

    override fun onDestroy() {
        super.onDestroy()
        WatchClientManager.clearListener()
    }

    private fun getOrCreateWatchId(): String {
        val prefs = getSharedPreferences("watch_prefs", Context.MODE_PRIVATE)
        var id = prefs.getString("watch_id", null)

        if (id == null) {
            id = String.format("%02d", Random.nextInt(0, 100))
            prefs.edit().putString("watch_id", id).apply()
        }

        return id
    }
}