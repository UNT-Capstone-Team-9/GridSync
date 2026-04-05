package com.cloud9.gridsync

import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.cloud9.gridsync.network.PlayMessage
import com.cloud9.gridsync.network.TabletServerManager

class PlayLibraryActivity : AppCompatActivity() {
    private var selectedPlayButton: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_play_library)

        val backButton = findViewById<ImageButton>(R.id.backButton)
        backButton.setOnClickListener {
            finish()
        }

        val play1 = findViewById<Button>(R.id.play1)
        val play2 = findViewById<Button>(R.id.play2)
        val play3 = findViewById<Button>(R.id.play3)
        val sendButton = findViewById<Button>(R.id.sendButton)

        sendButton.isEnabled = false

        fun selectPlay(button: Button) {
            selectedPlayButton?.setBackgroundResource(R.drawable.button_background)
            selectedPlayButton = button
            button.setBackgroundResource(R.drawable.button_background_selected)
            sendButton.isEnabled = true
        }

        play1.setOnClickListener { selectPlay(play1) }
        play2.setOnClickListener { selectPlay(play2) }
        play3.setOnClickListener { selectPlay(play3) }

        val playAssignments = mapOf(
            "Power Run" to mapOf(
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
            "Ace Florida" to mapOf(
                "QB"  to "5-step drop. Read WR1 first, check down to TE.",
                "RB"  to "Chip the blitzing linebacker, then release into flat.",
                "WR1" to "Run a 12-yard curl route. Sit down vs zone.",
                "WR2" to "Run a 10-yard out route. Be sharp on the break.",
                "TE"  to "Run a seam route up the middle. Stay on your landmark.",
                "LT"  to "Pass protect. Mirror the defensive end.",
                "LG"  to "Pass protect. Watch for inside stunt.",
                "C"   to "Snap and pick up any blitzing linebacker.",
                "RG"  to "Pass protect. Watch for inside stunt.",
                "RT"  to "Pass protect. Mirror the defensive end.",
                "FB"  to "Stay in and block. Release into the flat if clear."
            ),
            "Ace 66" to mapOf(
                "QB"  to "Quick 3-step drop. Get the ball out fast to WR1 or WR2.",
                "RB"  to "Block first, then release into the flat as a safety valve.",
                "WR1" to "Run a 6-yard hitch. Turn and look immediately.",
                "WR2" to "Run a 6-yard hitch. Turn and look immediately.",
                "TE"  to "Run a crossing route at 8 yards. Settle vs zone.",
                "LT"  to "Pass protect. Quick set — this is a hot route.",
                "LG"  to "Pass protect. Quick set — this is a hot route.",
                "C"   to "Snap and protect the inside gap.",
                "RG"  to "Pass protect. Quick set — this is a hot route.",
                "RT"  to "Pass protect. Quick set — this is a hot route.",
                "FB"  to "Block immediately. Do not release."
            )
        )

        sendButton.setOnClickListener {
            selectedPlayButton?.let { selected ->
                val playName = selected.text.toString()
                val assignments = playAssignments[playName] ?: emptyMap()
                val play = PlayMessage(
                    playName = playName,
                    assignments = assignments,
                    imageResourceName = ""
                )
                TabletServerManager.sendPlayToAssigned(play)
                Toast.makeText(this, "Play sent: $playName", Toast.LENGTH_SHORT).show()
            }
        }
    }
}