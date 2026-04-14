package com.cloud9.gridsync

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.cloud9.gridsync.network.ConnectedWatch
import com.cloud9.gridsync.network.RoleRepository
import com.cloud9.gridsync.network.RoleStatusInfo
import com.cloud9.gridsync.network.SessionLogManager
import com.cloud9.gridsync.network.TabletServerManager

class DashboardActivity : AppCompatActivity(),
    TabletServerManager.WatchListListener,
    SessionLogManager.SessionLogListener {

    private lateinit var settingsIcon: ImageView
    private lateinit var assignWatchesCard: LinearLayout
    private lateinit var sendPlayCard: LinearLayout
    private lateinit var createPlayCard: LinearLayout
    private lateinit var playLibraryCard: LinearLayout

    private lateinit var networkStatusText: TextView
    private lateinit var watchCountText: TextView
    private lateinit var playerStatusListContainer: LinearLayout
    private lateinit var sessionLogListContainer: LinearLayout

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
        SessionLogManager.addEntry("Tablet server started")
        Toast.makeText(this, "Tablet server started", Toast.LENGTH_SHORT).show()

        settingsIcon = findViewById(R.id.settingsIcon)
        assignWatchesCard = findViewById(R.id.assignWatchesCard)
        sendPlayCard = findViewById(R.id.sendPlayCard)
        createPlayCard = findViewById(R.id.createPlayCard)
        playLibraryCard = findViewById(R.id.playLibraryCard)

        networkStatusText = findViewById(R.id.networkStatusText)
        watchCountText = findViewById(R.id.watchCountText)
        playerStatusListContainer = findViewById(R.id.playerStatusListContainer)
        sessionLogListContainer = findViewById(R.id.sessionLogListContainer)

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

        renderDashboardStatuses()
        renderSessionLog(SessionLogManager.getEntries())
    }

    override fun onStart() {
        super.onStart()
        TabletServerManager.addListener(this)
        SessionLogManager.addListener(this)
        renderDashboardStatuses()
        renderSessionLog(SessionLogManager.getEntries())
    }

    override fun onStop() {
        super.onStop()
        TabletServerManager.removeListener(this)
        SessionLogManager.removeListener(this)
    }

    override fun onWatchListChanged(watches: List<ConnectedWatch>) {
        runOnUiThread {
            renderDashboardStatuses()
        }
    }

    override fun onSessionLogChanged(entries: List<String>) {
        runOnUiThread {
            renderSessionLog(entries)
        }
    }

    private fun renderDashboardStatuses() {
        val roles = RoleRepository.getRoles(this).map { it.trim() }
        val statuses = TabletServerManager.getRoleStatuses(roles)

        val activeCount = statuses.count {
            it.status == "Connecting" || it.status == "Online"
        }

        val assignedCount = statuses.count {
            it.status != "Unassigned"
        }

        networkStatusText.text = if (activeCount > 0) "Live" else "Listening"
        watchCountText.text = "$activeCount active / $assignedCount assigned"

        playerStatusListContainer.removeAllViews()
        statuses.forEach { info ->
            playerStatusListContainer.addView(buildStatusRow(info))
        }
    }

    private fun buildStatusRow(info: RoleStatusInfo): TextView {
        val textView = TextView(this)

        val statusText = when (info.status) {
            "Offline" -> if (info.assignedWatchId.isNullOrBlank()) "Offline" else "Offline  ID ${info.assignedWatchId}"
            "Connecting" -> if (info.assignedWatchId.isNullOrBlank()) "Connecting" else "Connecting  ID ${info.assignedWatchId}"
            "Online" -> if (info.assignedWatchId.isNullOrBlank()) "Online" else "Online  ID ${info.assignedWatchId}"
            else -> "Unassigned"
        }

        textView.text = "${info.role}    $statusText"
        textView.textSize = 16f
        textView.typeface = Typeface.MONOSPACE
        textView.setPadding(0, dp(9), 0, dp(9))
        textView.setTextColor(
            when (info.status) {
                "Online" -> Color.parseColor("#0F9D58")
                "Connecting" -> Color.parseColor("#F59E0B")
                "Offline" -> Color.parseColor("#7B8794")
                else -> Color.parseColor("#1F2937")
            }
        )
        textView.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return textView
    }

    private fun renderSessionLog(entries: List<String>) {
        sessionLogListContainer.removeAllViews()

        if (entries.isEmpty()) {
            val empty = TextView(this)
            empty.text = "No activity yet"
            empty.textSize = 14f
            empty.setTextColor(Color.parseColor("#52606D"))
            sessionLogListContainer.addView(empty)
            return
        }

        entries.forEach { entry ->
            val row = TextView(this)
            row.text = entry
            row.textSize = 14f
            row.setTextColor(Color.parseColor("#52606D"))
            row.setPadding(0, dp(4), 0, dp(4))
            sessionLogListContainer.addView(row)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}