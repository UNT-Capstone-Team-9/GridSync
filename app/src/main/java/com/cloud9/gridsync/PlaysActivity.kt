package com.cloud9.gridsync

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.cloud9.gridsync.database.AppDatabase
import com.cloud9.gridsync.database.HurryUpRepository
import kotlin.concurrent.thread

// Hub behind the dashboard's Plays tile: the full Play Library and the coach's Hurry-Up Packages.
class PlaysActivity : AppCompatActivity() {

    private lateinit var playLibraryCountText: TextView
    private lateinit var hurryUpCountText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plays)

        playLibraryCountText = findViewById(R.id.playLibraryCountText)
        hurryUpCountText = findViewById(R.id.hurryUpCountText)

        findViewById<ImageButton>(R.id.backButton).setOnClickListener { finish() }

        findViewById<LinearLayout>(R.id.playLibraryOption).setOnClickListener {
            startActivity(Intent(this, PlayLibraryActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.hurryUpOption).setOnClickListener {
            startActivity(Intent(this, HurryUpPackagesActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        thread {
            val playCount = AppDatabase.getDatabase(applicationContext).playDao().getPlayCount()
            val packageCount = HurryUpRepository.getSummaries(applicationContext).size

            runOnUiThread {
                playLibraryCountText.text = if (playCount == 1) "1 saved play" else "$playCount saved plays"
                hurryUpCountText.text = if (packageCount == 1) "1 package" else "$packageCount packages"
            }
        }
    }
}
