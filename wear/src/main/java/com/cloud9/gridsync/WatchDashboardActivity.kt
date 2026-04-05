package com.cloud9.gridsync

import android.app.Activity
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.cloud9.gridsync.network.PlayMessage
import com.cloud9.gridsync.network.WatchClientManager

class WatchDashboardActivity : Activity() {

    private lateinit var playTitle: TextView
    private lateinit var connectMessage: TextView
    private lateinit var playImage: ImageView

    private val watchListener = object : WatchClientManager.Listener {
        override fun onStatusChanged(status: String) {
            if (playTitle.text.toString().isBlank() || playTitle.text.toString() == "Waiting for Coach...") {
                playTitle.text = "GridSync"
            }
            connectMessage.text = status
        }

        override fun onRoleChanged(role: String?) {
            connectMessage.text = if (role.isNullOrBlank()) {
                "Connected"
            } else {
                "Connected as $role"
            }
        }

        override fun onPlayReceived(play: PlayMessage) {
            playTitle.text = play.playName
            connectMessage.text = play.assignment

            if (play.imageResourceName.isNotBlank()) {
                val resId = resources.getIdentifier(
                    play.imageResourceName,
                    "drawable",
                    packageName
                )

                if (resId != 0) {
                    playImage.setImageResource(resId)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_watch_dashboard)

        playTitle = findViewById(R.id.playTitle)
        connectMessage = findViewById(R.id.connectMessage)
        playImage = findViewById(R.id.playImage)

        playTitle.text = "GridSync"
        connectMessage.text = "Searching for tablet"

        // Visual confirmation that this code is running
        Toast.makeText(this, "Watch client starting", Toast.LENGTH_SHORT).show()

        // Trigger the networking logic
        WatchClientManager.start(applicationContext, watchListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        WatchClientManager.stop()
    }
}