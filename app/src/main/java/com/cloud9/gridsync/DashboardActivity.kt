package com.cloud9.gridsync

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.cloud9.gridsync.network.TabletServerManager

class DashboardActivity : AppCompatActivity() {

    private fun isLokmatWatch(): Boolean {
        val model = Build.MODEL ?: ""
        val manufacturer = Build.MANUFACTURER ?: ""
        val brand = Build.BRAND ?: ""

        return model.contains("APPLLP", ignoreCase = true) ||
                model.contains("LOKMAT", ignoreCase = true) ||
                manufacturer.contains("LOKMAT", ignoreCase = true) ||
                brand.contains("LOKMAT", ignoreCase = true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (isLokmatWatch()) {
            startActivity(Intent(this, WatchDashboardActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_dashboard)
        TabletServerManager.start(applicationContext)

        val settingsIcon = findViewById<ImageView>(R.id.settingsIcon)
        val assignWatchesCard = findViewById<LinearLayout>(R.id.assignWatchesCard)
        val sendPlayCard = findViewById<LinearLayout>(R.id.sendPlayCard)
        val createPlayCard = findViewById<LinearLayout>(R.id.createPlayCard)
        val playLibraryCard = findViewById<LinearLayout>(R.id.playLibraryCard)

        createPlayCard.setOnClickListener {
            startActivity(Intent(this, CreatePlayActivity::class.java))
        }

        playLibraryCard.setOnClickListener {
            startActivity(Intent(this, PlayLibraryActivity::class.java))
        }

        assignWatchesCard.setOnClickListener {
            startActivity(Intent(this, AssignWatchesActivity::class.java))
        }

        sendPlayCard.setOnClickListener {
            startActivity(Intent(this, SendPlayActivity::class.java))
        }

        settingsIcon.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }
}