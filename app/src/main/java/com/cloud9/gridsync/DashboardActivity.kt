package com.cloud9.gridsync

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

class DashboardActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

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
    }
}