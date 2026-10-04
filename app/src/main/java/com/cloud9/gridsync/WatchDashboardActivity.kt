package com.cloud9.gridsync

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.cloud9.gridsync.network.PlayMessage
import com.cloud9.gridsync.network.PlayerPosition
import com.cloud9.gridsync.network.PointData
import com.cloud9.gridsync.network.RouteBranch
import com.cloud9.gridsync.network.WatchClientManager
import com.cloud9.gridsync.ui.PlayPreviewView
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
    private lateinit var fullPlayView: PlayPreviewView

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

        // When the watch drops to its watch face the app is backgrounded, Android freezes it and
        // the connection to the tablet is lost, so the screen stays on while this is open.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

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
        fullPlayView = findViewById(R.id.fullPlayView)
        fullPlayView.largeMarkers = true

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
    //Display Role
    override fun onRoleChanged(role: String) {
        currentRole = role
        waitingRoleText.text = role
        messageRoleText.text = role
        roleText.text = role
        watchRouteView.setRole(role)
    }

    // Displays the play information received from the coach's tablet on the assigned player's watch.
    override fun onPlayReceived(
        playName: String,
        playTextMessage: String,
        movements: Map<String, List<PointData>>,
        players: List<PlayerPosition>,
        options: Map<String, List<RouteBranch>>,
        routeColors: Map<String, String>,
        isFullPlay: Boolean
    ) {
        mainHandler.removeCallbacks(resetToWaitingRunnable)

        waitingContainer.visibility = View.GONE
        textMessageContainer.visibility = View.GONE
        playContainer.visibility = View.VISIBLE

        playNameText.text = if (playName.isBlank()) "Incoming Play" else playName
        watchRouteView.setPlay(movements, players, isFullPlay, options)

        // A lineman with no drawn path has nothing to draw, so the assignment fills the screen.
        // With a route, the text shrinks so the drawing gets most of the small screen; at the
        // full text sizes the route view was left with almost no height.
        val hasRoute = movements.values.any { it.size >= 2 }

        // Every watch draws in the Play Library style and colours. The QB sees the whole play;
        // the preview matches route keys by player id or label, so the label keyed payload works
        // as is. A receiver sees only their own route, in the colour the tablet worked out from
        // the whole play. Plays from older tablets without players keep the plain route view.
        val ownRole = movements.keys.firstOrNull()
        val showFullPlayCard = isFullPlay && players.isNotEmpty()
        val showOwnRouteCard = !isFullPlay && hasRoute && players.isNotEmpty() && ownRole != null

        when {
            showFullPlayCard -> fullPlayView.setPlay(
                PlayMessage(
                    playName = playName,
                    assignments = emptyMap(),
                    movements = movements,
                    players = players,
                    routeOptions = options
                )
            )

            showOwnRouteCard -> fullPlayView.setPlayerRoute(
                player = players.firstOrNull { it.displayLabel == ownRole } ?: players.first(),
                route = movements.getValue(ownRole!!),
                options = options[ownRole].orEmpty(),
                colorHex = routeColors[ownRole]
            )

            else -> fullPlayView.setPlay(null)
        }
        val showCard = showFullPlayCard || showOwnRouteCard

        if (isFullPlay || hasRoute) {
            roleText.textSize = 16f
            playNameText.textSize = 14f
            playNameText.maxLines = 1
            playText.text = playTextMessage
            playText.textSize = 14f
            playText.maxLines = 2
            watchRouteView.visibility = if (showCard) View.GONE else View.VISIBLE
            fullPlayView.visibility = if (showCard) View.VISIBLE else View.GONE
        } else {
            roleText.textSize = 36f
            playNameText.textSize = 26f
            playNameText.maxLines = 2
            playText.text = formatAssignment(playTextMessage)
            playText.textSize = 34f
            playText.maxLines = 6
            watchRouteView.visibility = View.GONE
            fullPlayView.visibility = View.GONE
        }

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

    // "Pull Right - Block the edge defender" becomes PULL RIGHT over the instruction.
    private fun formatAssignment(text: String): String {
        val parts = text.split(" - ", limit = 2)
        return if (parts.size == 2) "${parts[0].uppercase()}\n${parts[1]}" else text.uppercase()
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