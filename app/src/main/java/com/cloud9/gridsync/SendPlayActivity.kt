package com.cloud9.gridsync

import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.cloud9.gridsync.network.PlayMessage
import com.cloud9.gridsync.network.TabletServerManager

class SendPlayActivity : AppCompatActivity() {

    private lateinit var assignmentSummaryText: TextView
    private lateinit var sendPlayButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_send_play)

        TabletServerManager.start(applicationContext)

        val backButton = findViewById<ImageButton>(R.id.backButton)
        assignmentSummaryText = findViewById(R.id.assignmentSummaryText)
        sendPlayButton = findViewById(R.id.sendPlayButton)

        backButton.setOnClickListener {
            finish()
        }

        sendPlayButton.setOnClickListener {
            val play = PlayMessage(
                playName = "Power Run",
                assignments = mapOf(
                    "QB"  to "Hand off to RB. Fake left after exchange.",
                    "RB"  to "Take handoff, hit the A gap hard.",
                    "WR1" to "Block the outside corner. Hold for 3 seconds.",
                    "WR2" to "Run a short drag route as a decoy.",
                    "TE"  to "Seal the outside linebacker. Do not release.",
                    "LT"  to "Drive block left. Push defensive end outside.",
                    "LG"  to "Pull right and lead block through the hole.",
                    "C"   to "Snap and block the nose tackle.",
                    "RG"  to "Double-team defensive tackle with RT.",
                    "RT"  to "Double-team defensive tackle with RG.",
                    "FB"  to "Lead block through the A gap ahead of RB."
                ),
                imageResourceName = ""
            )

            TabletServerManager.sendPlayToAssigned(play)

            Toast.makeText(this, "Play sent", Toast.LENGTH_SHORT).show()

            refreshAssignmentSummary()
        }

        refreshAssignmentSummary()
    }

    override fun onResume() {
        super.onResume()
        refreshAssignmentSummary()
    }

    private fun refreshAssignmentSummary() {
        val assignedCount = TabletServerManager
            .getConnectedWatches()
            .count { !it.role.isNullOrBlank() }

        assignmentSummaryText.text = if (assignedCount == 1) {
            "1 watch has a role and can receive the play"
        } else {
            "$assignedCount watches have roles and can receive the play"
        }
    }
}