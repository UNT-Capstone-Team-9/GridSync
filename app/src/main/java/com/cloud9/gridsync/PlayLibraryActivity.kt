package com.cloud9.gridsync

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cloud9.gridsync.database.AppDatabase
import com.cloud9.gridsync.database.DefaultPlaySeeder
import com.cloud9.gridsync.network.PlayMessage
import com.cloud9.gridsync.network.SessionLogManager
import com.cloud9.gridsync.network.TabletServerManager
import com.google.gson.Gson
import kotlin.concurrent.thread

class PlayLibraryActivity : AppCompatActivity() {

    private lateinit var playRecyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var sendButton: Button
    private lateinit var adapter: PlayListAdapter
    private lateinit var searchInput: EditText
    private lateinit var sortSpinner: Spinner

    private val gson = Gson()
    private var selectedPlay: PlayMessage? = null
    private var allVisiblePlays: List<PlayMessage> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_play_library)

        val backButton = findViewById<ImageButton>(R.id.backButton)
        val trashButton = findViewById<ImageButton>(R.id.trashButton)

        playRecyclerView = findViewById(R.id.playRecyclerView)
        emptyStateText = findViewById(R.id.emptyStateText)
        sendButton = findViewById(R.id.sendButton)
        searchInput = findViewById(R.id.searchInput)
        sortSpinner = findViewById(R.id.sortSpinner)

        backButton.setOnClickListener {
            finish()
        }

        trashButton.setOnClickListener {
            startActivity(Intent(this@PlayLibraryActivity, TrashActivity::class.java))
        }

        val sortOptions = listOf("Name A to Z", "Newest First", "Oldest First")
        val sortAdapter = ArrayAdapter(
            this@PlayLibraryActivity,
            android.R.layout.simple_spinner_item,
            sortOptions
        )
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sortSpinner.adapter = sortAdapter

        sendButton.isEnabled = false
        sendButton.setOnClickListener {
            val play = selectedPlay
            if (play == null) {
                Toast.makeText(this@PlayLibraryActivity, "Select a play first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            TabletServerManager.sendPlayToAssigned(play)
            SessionLogManager.addEntry("Play sent ${play.playName}")
            Toast.makeText(this@PlayLibraryActivity, "Play sent ${play.playName}", Toast.LENGTH_SHORT).show()
        }

        adapter = PlayListAdapter(
            plays = mutableListOf(),
            onPlayClicked = { play ->
                selectedPlay = play
                sendButton.isEnabled = true
            },
            onEditClicked = { play ->
                val intent = Intent(this@PlayLibraryActivity, CreatePlayActivity::class.java)
                intent.putExtra("edit_play_json", gson.toJson(play))
                startActivity(intent)
            },
            onDeleteClicked = { play ->
                showMoveToTrashConfirmation(play)
            }
        )

        playRecyclerView.layoutManager = LinearLayoutManager(this@PlayLibraryActivity)
        playRecyclerView.adapter = adapter

        sortSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: View?, position: Int, id: Long) {
                loadPlaysFromDatabase()
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {
            }
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable?) {
                applySearchFilter()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        loadPlaysFromDatabase()
    }

    private fun loadPlaysFromDatabase() {
        thread {
            DefaultPlaySeeder.seedDefaultsIfMissing(applicationContext)

            val dao = AppDatabase.getDatabase(applicationContext).playDao()
            val thirtyDaysMillis = 30L * 24L * 60L * 60L * 1000L
            val cutoffTime = System.currentTimeMillis() - thirtyDaysMillis
            dao.deleteExpiredTrash(cutoffTime)

            val selectedSort = sortSpinner.selectedItem?.toString() ?: "Name A to Z"

            val savedPlays = when (selectedSort) {
                "Newest First" -> dao.getAllPlaysNewestFirst()
                "Oldest First" -> dao.getAllPlaysOldestFirst()
                else -> dao.getAllPlays()
            }

            val plays = savedPlays.mapNotNull { entity ->
                try {
                    gson.fromJson(entity.dataJson, PlayMessage::class.java)
                } catch (_: Exception) {
                    null
                }
            }

            runOnUiThread {
                allVisiblePlays = plays
                applySearchFilter()
            }
        }
    }

    private fun applySearchFilter() {
        val query = searchInput.text.toString().trim().lowercase()

        val filtered = if (query.isBlank()) {
            allVisiblePlays
        } else {
            allVisiblePlays.filter { it.playName.lowercase().contains(query) }
        }

        adapter.replaceAll(filtered)
        selectedPlay = adapter.getSelectedPlay()
        sendButton.isEnabled = selectedPlay != null
        emptyStateText.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        playRecyclerView.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun showMoveToTrashConfirmation(play: PlayMessage) {
        AlertDialog.Builder(this)
            .setTitle("Move to trash")
            .setMessage("Move ${play.playName} to trash?")
            .setPositiveButton("Move") { _, _ ->
                movePlayToTrash(play)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun movePlayToTrash(play: PlayMessage) {
        thread {
            try {
                val dao = AppDatabase.getDatabase(applicationContext).playDao()
                dao.moveToTrash(play.playName, System.currentTimeMillis())

                runOnUiThread {
                    if (selectedPlay?.playName == play.playName) {
                        selectedPlay = null
                        sendButton.isEnabled = false
                    }

                    SessionLogManager.addEntry("Moved to trash ${play.playName}")
                    Toast.makeText(this@PlayLibraryActivity, "Moved to trash ${play.playName}", Toast.LENGTH_SHORT).show()
                    loadPlaysFromDatabase()
                }
            } catch (_: Exception) {
                runOnUiThread {
                    Toast.makeText(this@PlayLibraryActivity, "Move to trash failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}