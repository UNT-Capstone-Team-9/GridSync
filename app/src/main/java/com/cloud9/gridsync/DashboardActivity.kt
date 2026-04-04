package com.cloud9.gridsync

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import android.widget.ImageView

class DashboardActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        val settingsIcon = findViewById<ImageView>(R.id.settingsIcon)
        val assignWatchesCard = findViewById<LinearLayout>(R.id.assignWatchesCard)
        val sendPlayCard = findViewById<LinearLayout>(R.id.sendPlayCard)
        val createPlayCard = findViewById<LinearLayout>(R.id.createPlayCard)
        val playLibraryCard = findViewById<LinearLayout>(R.id.playLibraryCard)
        createPlayCard.setOnClickListener {
            val intent = Intent(this, CreatePlayActivity::class.java)
            startActivity(intent)
        }
        playLibraryCard.setOnClickListener {
            val intent = Intent(this, PlayLibraryActivity::class.java)
            startActivity(intent)
        }
        assignWatchesCard.setOnClickListener {
            val intent = Intent(this, AssignWatchesActivity::class.java)
            startActivity(intent)
        }
        sendPlayCard.setOnClickListener {
            val intent = Intent(this, SendPlayActivity::class.java)
            startActivity(intent)
        }
        settingsIcon.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }
}