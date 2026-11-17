package com.cloud9.gridsync

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class PlaysMenuActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plays_menu)

        val backButton = findViewById<TextView>(R.id.backButton)
        val playLibraryCard = findViewById<LinearLayout>(R.id.playLibraryCard)
        val hurryUpCard = findViewById<LinearLayout>(R.id.hurryUpCard)

        backButton.setOnClickListener {
            finish()
        }

        playLibraryCard.setOnClickListener {
            startActivity(
                Intent(this, PlayLibraryActivity::class.java)
            )
        }

        hurryUpCard.setOnClickListener {
            startActivity(
                Intent(this, HurryUpPackagesActivity::class.java)
            )
        }
    }
}