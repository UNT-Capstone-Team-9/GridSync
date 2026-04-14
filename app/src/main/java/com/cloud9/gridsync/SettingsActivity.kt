package com.cloud9.gridsync

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.cloud9.gridsync.backup.PlayBackupManager
import kotlin.concurrent.thread

class SettingsActivity : AppCompatActivity() {

    private lateinit var resultText: TextView

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult

        thread {
            try {
                val count = PlayBackupManager.exportActivePlays(applicationContext, uri)
                runOnUiThread {
                    resultText.text = "Exported $count plays successfully"
                    Toast.makeText(this, "Export complete", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    resultText.text = "Export failed"
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
                    resultText.text = "Imported $count plays successfully"
                    Toast.makeText(this, "Import complete", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    resultText.text = "Import failed"
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
        val importButton = findViewById<Button>(R.id.importButton)
        resultText = findViewById(R.id.resultText)

        backButton.setOnClickListener {
            finish()
        }

        exportButton.setOnClickListener {
            exportLauncher.launch("gridsync_plays_backup.json")
        }

        importButton.setOnClickListener {
            importLauncher.launch(arrayOf("application/json"))
        }
    }
}