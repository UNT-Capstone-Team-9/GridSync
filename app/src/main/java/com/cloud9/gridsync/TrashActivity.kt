package com.cloud9.gridsync

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cloud9.gridsync.database.AppDatabase
import com.cloud9.gridsync.network.PlayMessage
import com.cloud9.gridsync.network.SessionLogManager
import com.google.gson.Gson
import kotlin.concurrent.thread

class TrashActivity : AppCompatActivity() {

    private lateinit var trashRecyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var adapter: TrashListAdapter
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trash)

        val backButton = findViewById<ImageButton>(R.id.backButton)
        trashRecyclerView = findViewById(R.id.trashRecyclerView)
        emptyStateText = findViewById(R.id.emptyStateText)

        backButton.setOnClickListener {
            finish()
        }

        adapter = TrashListAdapter(
            plays = mutableListOf(),
            onRestoreClicked = { play ->
                showRestoreConfirmation(play)
            },
            onDeleteForeverClicked = { play ->
                showDeleteForeverConfirmation(play)
            }
        )

        trashRecyclerView.layoutManager = LinearLayoutManager(this)
        trashRecyclerView.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        loadTrashFromDatabase()
    }

    private fun loadTrashFromDatabase() {
        thread {
            val dao = AppDatabase.getDatabase(applicationContext).playDao()
            val deletedPlays = dao.getDeletedPlays()

            val plays = deletedPlays.mapNotNull { entity ->
                try {
                    gson.fromJson(entity.dataJson, PlayMessage::class.java)
                } catch (_: Exception) {
                    null
                }
            }

            runOnUiThread {
                adapter.replaceAll(plays)
                emptyStateText.visibility = if (plays.isEmpty()) View.VISIBLE else View.GONE
                trashRecyclerView.visibility = if (plays.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    private fun showRestoreConfirmation(play: PlayMessage) {
        AlertDialog.Builder(this)
            .setTitle("Restore play")
            .setMessage("Restore ${play.playName} back to the play library?")
            .setPositiveButton("Restore") { _, _ ->
                restorePlay(play)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteForeverConfirmation(play: PlayMessage) {
        AlertDialog.Builder(this)
            .setTitle("Delete forever")
            .setMessage("Delete ${play.playName} permanently? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                permanentlyDeletePlay(play)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun restorePlay(play: PlayMessage) {
        thread {
            try {
                val dao = AppDatabase.getDatabase(applicationContext).playDao()
                dao.restorePlay(play.playName)

                runOnUiThread {
                    SessionLogManager.addEntry("Restored ${play.playName} from trash")
                    Toast.makeText(this, "Restored ${play.playName}", Toast.LENGTH_SHORT).show()
                    loadTrashFromDatabase()
                }
            } catch (_: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Restore failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun permanentlyDeletePlay(play: PlayMessage) {
        thread {
            try {
                val dao = AppDatabase.getDatabase(applicationContext).playDao()
                dao.permanentlyDeleteByName(play.playName)

                runOnUiThread {
                    SessionLogManager.addEntry("Deleted forever ${play.playName}")
                    Toast.makeText(this, "Deleted forever ${play.playName}", Toast.LENGTH_SHORT).show()
                    loadTrashFromDatabase()
                }
            } catch (_: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Permanent delete failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}