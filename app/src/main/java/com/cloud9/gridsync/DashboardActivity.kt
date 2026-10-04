package com.cloud9.gridsync

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
    private lateinit var playsCard: LinearLayout

    private lateinit var networkStatusText: TextView
    private lateinit var networkStatusDot: View
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

        if (isLokmatWatch() || packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)) {
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
        playsCard = findViewById(R.id.playsCard)

        networkStatusText = findViewById(R.id.networkStatusText)
        networkStatusDot = findViewById(R.id.networkStatusDot)
        watchCountText = findViewById(R.id.watchCountText)
        playerStatusListContainer = findViewById(R.id.playerStatusListContainer)
        sessionLogListContainer = findViewById(R.id.sessionLogListContainer)

        createPlayCard.setOnClickListener {
            startActivity(Intent(this, CreatePlayActivity::class.java))
        }

        // Plays opens the hub holding the Play Library and Hurry-Up Packages.
        playsCard.setOnClickListener {
            startActivity(Intent(this, PlaysActivity::class.java))
        }

        assignWatchesCard.setOnClickListener {
            startActivity(Intent(this, AssignWatchesActivity::class.java))
        }

        // Shown to the coach as Send Messages. It sends text to selected roles, not plays.
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

        val isLive = activeCount > 0
        networkStatusText.text = if (isLive) "Live" else "Listening"
        networkStatusText.setTextColor(color(if (isLive) R.color.gridsync_green_bright else R.color.gridsync_text_primary))
        networkStatusDot.backgroundTintList = ColorStateList.valueOf(
            color(if (isLive) R.color.gridsync_green_bright else R.color.gridsync_amber)
        )
        watchCountText.text = "$activeCount active / $assignedCount assigned"

        playerStatusListContainer.removeAllViews()
        statuses.forEachIndexed { index, info ->
            if (index > 0) playerStatusListContainer.addView(buildDivider())
            playerStatusListContainer.addView(buildStatusRow(info))
        }
    }

    // One Player Status row: role, status and a coloured dot.
    private fun buildStatusRow(info: RoleStatusInfo): View {
        val statusText = when (info.status) {
            "Offline" -> if (info.assignedWatchId.isNullOrBlank()) "Offline" else "Offline  ID ${info.assignedWatchId}"
            "Connecting" -> if (info.assignedWatchId.isNullOrBlank()) "Connecting" else "Connecting  ID ${info.assignedWatchId}"
            "Online" -> if (info.assignedWatchId.isNullOrBlank()) "Online" else "Online  ID ${info.assignedWatchId}"
            else -> "Unassigned"
        }

        val statusColor = color(
            when (info.status) {
                "Online" -> R.color.gridsync_green_bright
                "Connecting" -> R.color.gridsync_amber
                "Offline" -> R.color.gridsync_text_secondary
                else -> R.color.gridsync_text_muted
            }
        )

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(11), 0, dp(11))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        row.addView(TextView(this).apply {
            text = info.role
            textSize = 16f
            setTextColor(color(R.color.gridsync_text_primary))
            layoutParams = LinearLayout.LayoutParams(dp(72), ViewGroup.LayoutParams.WRAP_CONTENT)
        })

        row.addView(TextView(this).apply {
            text = statusText
            textSize = 15f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(statusColor)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })

        row.addView(View(this).apply {
            setBackgroundResource(R.drawable.dashboard_status_dot)
            backgroundTintList = ColorStateList.valueOf(
                if (info.status == "Unassigned") color(R.color.gridsync_border) else statusColor
            )
            layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply { marginStart = dp(8) }
        })

        return row
    }

    private fun renderSessionLog(entries: List<String>) {
        sessionLogListContainer.removeAllViews()

        if (entries.isEmpty()) {
            val empty = TextView(this)
            empty.text = "No activity yet"
            empty.textSize = 14f
            empty.setTextColor(color(R.color.gridsync_text_muted))
            sessionLogListContainer.addView(empty)
            return
        }

        entries.forEachIndexed { index, entry ->
            if (index > 0) sessionLogListContainer.addView(buildDivider())
            sessionLogListContainer.addView(buildLogRow(entry))
        }
    }

    private fun buildDivider(): View {
        return View(this).apply {
            setBackgroundColor(color(R.color.gridsync_divider))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
        }
    }

    // SessionLogManager entries are "h:mm:ss a  message", so the time gets its own column.
    private fun buildLogRow(entry: String): View {
        val split = entry.indexOf("  ")
        val time = if (split > 0) entry.substring(0, split) else ""
        val message = if (split > 0) entry.substring(split).trim() else entry

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }

        // Green bar marking each entry.
        row.addView(View(this).apply {
            setBackgroundColor(color(R.color.gridsync_green_bright))
            layoutParams = LinearLayout.LayoutParams(dp(3), dp(22)).apply { marginEnd = dp(18) }
        })

        if (time.isNotEmpty()) {
            row.addView(TextView(this).apply {
                text = time
                textSize = 15f
                setTextColor(color(R.color.gridsync_text_secondary))
                layoutParams = LinearLayout.LayoutParams(dp(118), ViewGroup.LayoutParams.WRAP_CONTENT)
            })
        }

        row.addView(TextView(this).apply {
            text = message
            textSize = 15f
            setTextColor(color(R.color.gridsync_text_primary))
        })

        return row
    }

    private fun color(resId: Int): Int = ContextCompat.getColor(this, resId)

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}