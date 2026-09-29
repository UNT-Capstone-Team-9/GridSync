package com.cloud9.gridsync

import android.net.Uri
import android.os.Bundle
import android.content.Intent
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.cloud9.gridsync.backup.PlayBackupManager
import kotlin.concurrent.thread

class SettingsActivity : SwipeBackActivity() {

    private lateinit var resultText: TextView

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult

        thread {
            try {
                val count = PlayBackupManager.exportActivePlays(applicationContext, uri)
                runOnUiThread {
                    showResult("Exported $count plays successfully")
                    Toast.makeText(this, "Export complete", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showResult("Export failed")
                    Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult

        thread {
            try {
                val count = PlayBackupManager.importPlays(applicationContext, uri)
                runOnUiThread {
                    showResult("Imported $count plays successfully")
                    Toast.makeText(this, "Import complete", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showResult("Import failed")
                    Toast.makeText(this, "Import failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val backButton = findViewById<ImageButton>(R.id.backButton)
        val exportButton = findViewById<Button>(R.id.exportButton)
        findViewById<View>(R.id.sessionLogButton).setOnClickListener {
            startActivity(Intent(this, SessionLogActivity::class.java))
        }
        val importButton = findViewById<Button>(R.id.importButton)
        resultText = findViewById(R.id.resultText)

        backButton.setOnClickListener {
            goBack()
        }

        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: Exception) {
            null
        }
        findViewById<TextView>(R.id.versionText).text =
            if (versionName.isNullOrBlank()) "GridSync" else "GridSync  \u2022  Version $versionName"

        exportButton.setOnClickListener {
            exportLauncher.launch("gridsync_plays_backup.json")
        }

        importButton.setOnClickListener {
            importLauncher.launch(arrayOf("application/json"))
        }
    }

    private fun showResult(message: String) {
        resultText.text = message
        resultText.visibility = View.VISIBLE
    }
}
