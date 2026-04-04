package com.cloud9.gridsync

import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

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

        // Initially disable Send button
        sendButton.isEnabled = false

        // Function to handle selection
        fun selectPlay(button: Button) {
            // Deselect previous
            selectedPlayButton?.setBackgroundResource(R.drawable.button_background)
            // Select new
            selectedPlayButton = button
            button.setBackgroundResource(R.drawable.button_background_selected)
            // Enable Send button
            sendButton.isEnabled = true
        }

        play1.setOnClickListener { selectPlay(play1) }
        play2.setOnClickListener { selectPlay(play2) }
        play3.setOnClickListener { selectPlay(play3) }

        sendButton.setOnClickListener {
            selectedPlayButton?.let { selected ->
                // Do something with the selected play
                // e.g., send selected.text
            }
        }

    }
}