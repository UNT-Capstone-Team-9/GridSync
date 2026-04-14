package com.cloud9.gridsync

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.cloud9.gridsync.network.PointData
import com.cloud9.gridsync.network.WatchClientManager
import com.cloud9.gridsync.ui.WatchRouteView
import kotlin.random.Random

class WatchDashboardActivity : AppCompatActivity(),
    WatchClientManager.WatchMessageListener {

    private lateinit var waitingContainer: LinearLayout
    private lateinit var textMessageContainer: LinearLayout
    private lateinit var playContainer: LinearLayout

    private lateinit var waitingRoleText: TextView
    private lateinit var waitingTitleText: TextView
    private lateinit var waitingStatusText: TextView

    private lateinit var messageRoleText: TextView
    private lateinit var messageTitleText: TextView
    private lateinit var messageBodyText: TextView

    private lateinit var playText: TextView
    private lateinit var roleText: TextView
    private lateinit var playNameText: TextView
    private lateinit var watchRouteView: WatchRouteView

    private var currentRole: String = "Unassigned"

    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val PLAY_DISPLAY_DURATION_MS = 18000L
        private const val MESSAGE_DISPLAY_DURATION_MS = 18000L
    }

    private val resetToWaitingRunnable = Runnable {
        showWaitingState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_watch_dashboard)

        waitingContainer = findViewById(R.id.waitingContainer)
        textMessageContainer = findViewById(R.id.textMessageContainer)
        playContainer = findViewById(R.id.playContainer)

        waitingRoleText = findViewById(R.id.waitingRoleText)
        waitingTitleText = findViewById(R.id.waitingTitleText)
        waitingStatusText = findViewById(R.id.waitingStatusText)

        messageRoleText = findViewById(R.id.messageRoleText)
        messageTitleText = findViewById(R.id.messageTitleText)
        messageBodyText = findViewById(R.id.messageBodyText)

        playText = findViewById(R.id.playText)
        roleText = findViewById(R.id.playerRoleText)
        playNameText = findViewById(R.id.playNameText)
        watchRouteView = findViewById(R.id.watchRouteView)

        val watchId = getOrCreateWatchId()

        watchRouteView.setRole("Unassigned")
        watchRouteView.setMovements(emptyMap())
        showScanningState()

        WatchClientManager.setListener(this)
        WatchClientManager.connect(applicationContext, watchId)
    }

    override fun onConnectionChanged(isConnected: Boolean) {
        mainHandler.removeCallbacks(resetToWaitingRunnable)

        if (isConnected) {
            showWaitingState()
        } else {
            showScanningState()
        }
    }

    override fun onRoleChanged(role: String) {
        currentRole = role
        waitingRoleText.text = role
        messageRoleText.text = role
        roleText.text = role
        watchRouteView.setRole(role)
    }

    override fun onPlayReceived(
        playName: String,
        playTextMessage: String,
        movements: Map<String, List<PointData>>
    ) {
        mainHandler.removeCallbacks(resetToWaitingRunnable)

        waitingContainer.visibility = View.GONE
        textMessageContainer.visibility = View.GONE
        playContainer.visibility = View.VISIBLE

        playNameText.text = if (playName.isBlank()) "Incoming Play" else playName
        playText.text = playTextMessage
        watchRouteView.setMovements(movements)

        mainHandler.postDelayed(resetToWaitingRunnable, PLAY_DISPLAY_DURATION_MS)
    }

    override fun onTextMessageReceived(role: String, message: String) {
        mainHandler.removeCallbacks(resetToWaitingRunnable)

        waitingContainer.visibility = View.GONE
        playContainer.visibility = View.GONE
        textMessageContainer.visibility = View.VISIBLE

        messageRoleText.text = role
        messageTitleText.text = "Coach Message"
        messageBodyText.text = message
        watchRouteView.setMovements(emptyMap())

        mainHandler.postDelayed(resetToWaitingRunnable, MESSAGE_DISPLAY_DURATION_MS)
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(resetToWaitingRunnable)
        WatchClientManager.clearListener()
    }

    private fun showScanningState() {
        waitingContainer.visibility = View.VISIBLE
        textMessageContainer.visibility = View.GONE
        playContainer.visibility = View.GONE

        waitingRoleText.text = currentRole
        waitingTitleText.text = "Waiting for play"
        waitingStatusText.text = "Scanning for tablet..."
        watchRouteView.setMovements(emptyMap())
    }

    private fun showWaitingState() {
        waitingContainer.visibility = View.VISIBLE
        textMessageContainer.visibility = View.GONE
        playContainer.visibility = View.GONE

        waitingRoleText.text = currentRole
        waitingTitleText.text = "Waiting for play"
        waitingStatusText.text = "Waiting for assignment"
        watchRouteView.setMovements(emptyMap())
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