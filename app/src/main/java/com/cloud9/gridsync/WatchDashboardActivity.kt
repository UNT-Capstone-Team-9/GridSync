package com.cloud9.gridsync

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class WatchDashboardActivity : AppCompatActivity() {

    private lateinit var playText: TextView
    private lateinit var roleText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_watch_dashboard)

        playText = findViewById(R.id.playText)
        roleText = findViewById(R.id.playerRoleText)

        roleText.text = "QB"
        playText.text = "Waiting for connection..."
    }
}