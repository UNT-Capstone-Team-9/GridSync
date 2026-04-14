package com.cloud9.gridsync

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.cloud9.gridsync.database.AppDatabase
import com.cloud9.gridsync.database.PlayEntity
import com.cloud9.gridsync.network.PlayMessage
import com.cloud9.gridsync.network.RoleRepository
import com.cloud9.gridsync.network.SessionLogManager
import com.cloud9.gridsync.ui.CoachDrawingView
import com.google.gson.Gson

class CreatePlayActivity : AppCompatActivity() {

    private lateinit var drawingView: CoachDrawingView
    private lateinit var roleSpinner: Spinner
    private lateinit var playNameInput: EditText
    private lateinit var roles: List<String>

    private val gson = Gson()
    private var originalPlayName: String? = null
    private var isEditMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_play)

        drawingView = findViewById(R.id.coachDrawingView)
        playNameInput = findViewById(R.id.etPlayName)
        roleSpinner = findViewById(R.id.spinnerRoles)

        val saveButton = findViewById<Button>(R.id.btnSavePlay)
        val clearButton = findViewById<Button>(R.id.btnClear)
        val backButton = findViewById<ImageButton>(R.id.backButton)

        roles = RoleRepository.getRoles(this).map { it.trim() }

        val spinnerAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            roles
        )
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        roleSpinner.adapter = spinnerAdapter

        val qbIndex = roles.indexOf("QB")
        if (qbIndex >= 0) {
            roleSpinner.setSelection(qbIndex)
            drawingView.setRole("QB")
        } else if (roles.isNotEmpty()) {
            drawingView.setRole(roles.first())
        }

        roleSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedRole = parent.getItemAtPosition(position).toString().trim()
                drawingView.setRole(selectedRole)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
            }
        }

        backButton.setOnClickListener {
            finish()
        }

        clearButton.setOnClickListener {
            drawingView.clearCurrentRole()
            Toast.makeText(this@CreatePlayActivity, "Cleared current role", Toast.LENGTH_SHORT).show()
        }

        loadEditPlayIfPresent()

        saveButton.setOnClickListener {
            val name = playNameInput.text.toString().trim()
            if (name.isBlank()) {
                Toast.makeText(this@CreatePlayActivity, "Enter a play name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val movements = drawingView.getMovements()
                .filterKeys { it.isNotBlank() }
                .filterValues { it.size >= 2 }

            if (movements.isEmpty()) {
                Toast.makeText(this@CreatePlayActivity, "Draw at least one route before saving", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val assignments = mutableMapOf<String, String>()
            roles.forEach { role ->
                assignments[role] = if (movements.containsKey(role)) {
                    "Follow the drawn route for $role"
                } else {
                    "No route drawn for $role"
                }
            }

            val play = PlayMessage(
                playName = name,
                assignments = assignments,
                movements = movements,
                imageResourceName = ""
            )

            saveToDatabase(name, play)
        }
    }

    private fun loadEditPlayIfPresent() {
        val editJson = intent.getStringExtra("edit_play_json") ?: return

        try {
            val play = gson.fromJson(editJson, PlayMessage::class.java)
            isEditMode = true
            originalPlayName = play.playName

            playNameInput.setText(play.playName)
            drawingView.setMovements(play.movements)

            val roleToSelect = when {
                play.movements.containsKey("QB") -> "QB"
                play.movements.isNotEmpty() -> play.movements.keys.first().trim()
                else -> null
            }

            if (roleToSelect != null) {
                val index = roles.indexOf(roleToSelect)
                if (index >= 0) {
                    roleSpinner.setSelection(index)
                    drawingView.setRole(roleToSelect)
                }
            }

            title = "Edit Play"
        } catch (_: Exception) {
            Toast.makeText(this@CreatePlayActivity, "Failed to load play for editing", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveToDatabase(name: String, play: PlayMessage) {
        Thread {
            try {
                val db = AppDatabase.getDatabase(this)
                val dao = db.playDao()

                if (isEditMode && originalPlayName != null && originalPlayName != name) {
                    dao.permanentlyDeleteByName(originalPlayName!!)
                }

                val entity = PlayEntity(
                    name = name,
                    dataJson = gson.toJson(play),
                    isDeleted = false,
                    deletedAt = null,
                    updatedAt = System.currentTimeMillis()
                )

                dao.insertPlay(entity)

                runOnUiThread {
                    SessionLogManager.addEntry(
                        if (isEditMode) "Play updated $name" else "Play saved $name"
                    )
                    Toast.makeText(
                        this@CreatePlayActivity,
                        if (isEditMode) "Play updated" else "Play saved to library",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@CreatePlayActivity, "Error saving play ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}