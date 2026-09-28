package com.cloud9.gridsync

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.cloud9.gridsync.database.AppDatabase
import com.cloud9.gridsync.database.HurryUpRepository
import com.cloud9.gridsync.database.PlayEntity
import com.cloud9.gridsync.network.PlayFormation
import com.cloud9.gridsync.network.PlayMessage
import com.cloud9.gridsync.network.PlayerPosition
import com.cloud9.gridsync.network.SessionLogManager
import com.cloud9.gridsync.ui.CoachDrawingView
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson

class CreatePlayActivity : AppCompatActivity(), CoachDrawingView.Listener {

    private lateinit var drawingView: CoachDrawingView
    private lateinit var playNameInput: EditText
    private lateinit var formationInput: EditText
    private lateinit var passButton: MaterialButton
    private lateinit var runButton: MaterialButton
    private lateinit var moveButton: MaterialButton
    private lateinit var routeButton: MaterialButton
    private lateinit var modeHintText: TextView
    private lateinit var selectedPlayerText: TextView
    private lateinit var assignmentSpinner: Spinner
    private lateinit var instructionInput: EditText
    private lateinit var activeCountText: TextView
    private lateinit var swapHintText: TextView
    private lateinit var benchContainer: LinearLayout
    private lateinit var cancelSwapButton: MaterialButton

    private val gson = Gson()
    private var originalPlayName: String? = null
    private var originalImageResourceName = ""
    private var isEditMode = false

    private var playType = PlayFormation.PLAY_TYPE_PASS
    private var assignmentOptions: List<String> = emptyList()
    private var pendingSwapBenchId: String? = null

    // Set while the panel is being filled from a player so the listeners do not write back.
    private var isBindingPanel = false

    private val gold = Color.parseColor("#FCA311")
    private val navy = Color.parseColor("#14213D")
    private val slate = Color.parseColor("#415A77")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_play)

        drawingView = findViewById(R.id.coachDrawingView)
        playNameInput = findViewById(R.id.etPlayName)
        formationInput = findViewById(R.id.etFormation)
        passButton = findViewById(R.id.btnPlayTypePass)
        runButton = findViewById(R.id.btnPlayTypeRun)
        moveButton = findViewById(R.id.btnMovePlayers)
        routeButton = findViewById(R.id.btnDrawRoute)
        modeHintText = findViewById(R.id.tvModeHint)
        selectedPlayerText = findViewById(R.id.tvSelectedPlayer)
        assignmentSpinner = findViewById(R.id.spinnerAssignment)
        instructionInput = findViewById(R.id.etInstruction)
        activeCountText = findViewById(R.id.tvActiveCount)
        swapHintText = findViewById(R.id.tvSwapHint)
        benchContainer = findViewById(R.id.benchContainer)
        cancelSwapButton = findViewById(R.id.btnCancelSwap)

        drawingView.listener = this

        findViewById<ImageButton>(R.id.backButton).setOnClickListener { confirmLeave() }
        findViewById<MaterialButton>(R.id.btnCancel).setOnClickListener { confirmLeave() }
        findViewById<MaterialButton>(R.id.btnSavePlay).setOnClickListener { savePlay() }

        passButton.setOnClickListener { setPlayType(PlayFormation.PLAY_TYPE_PASS) }
        runButton.setOnClickListener { setPlayType(PlayFormation.PLAY_TYPE_RUN) }

        moveButton.setOnClickListener { setMode(CoachDrawingView.Mode.MOVE) }
        routeButton.setOnClickListener { setMode(CoachDrawingView.Mode.ROUTE) }

        findViewById<MaterialButton>(R.id.btnClearRoute).setOnClickListener { clearSelectedRoute() }
        findViewById<MaterialButton>(R.id.btnResetFormation).setOnClickListener { confirmResetFormation() }
        cancelSwapButton.setOnClickListener { endSwap() }

        setupAssignmentPanel()

        drawingView.setFormation(PlayFormation.defaultPlayers(), emptyMap())
        setPlayType(PlayFormation.PLAY_TYPE_PASS)
        setMode(CoachDrawingView.Mode.MOVE)

        loadEditPlayIfPresent()
    }

    override fun onPlayerSelected(player: PlayerPosition?) {
        bindAssignmentPanel(player)
    }

    override fun onSwapTargetChosen(activePlayer: PlayerPosition) {
        val benchId = pendingSwapBenchId ?: return
        val result = PlayFormation.swap(
            drawingView.getPlayers(),
            drawingView.getMovements(),
            benchId,
            activePlayer.id
        )

        if (result == null) {
            Toast.makeText(this, "That swap is not possible", Toast.LENGTH_SHORT).show()
            endSwap()
            return
        }

        val incomingLabel = result.players.first { it.id == benchId }.displayLabel
        endSwap()
        drawingView.setFormation(result.players, result.movements)
        drawingView.selectPlayer(benchId)

        Toast.makeText(
            this,
            "$incomingLabel in for ${activePlayer.displayLabel}",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onFormationChanged() {
        val activeCount = drawingView.getPlayers().count { it.isActive }
        activeCountText.text = "Active: $activeCount / ${PlayFormation.ACTIVE_PLAYER_COUNT}"
        renderBench()
    }

    override fun onSelectionNeeded() {
        Toast.makeText(this, "Tap a player to draw their route", Toast.LENGTH_SHORT).show()
    }

    private fun setupAssignmentPanel() {
        assignmentSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (isBindingPanel) return
                writePanelToSelectedPlayer()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
            }
        }

        instructionInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable?) {
                if (isBindingPanel) return
                writePanelToSelectedPlayer()
            }
        })

        bindAssignmentPanel(null)
    }

    private fun bindAssignmentPanel(player: PlayerPosition?) {
        isBindingPanel = true

        if (player == null) {
            selectedPlayerText.text = "Selected: none"
            assignmentOptions = emptyList()
            assignmentSpinner.adapter = ArrayAdapter(this, R.layout.item_spinner_light, listOf("Select a player"))
            assignmentSpinner.isEnabled = false
            instructionInput.setText("")
            instructionInput.isEnabled = false
        } else {
            selectedPlayerText.text = "Selected: ${player.displayLabel}"
            assignmentOptions = PlayFormation.assignmentOptions(player)

            val adapter = ArrayAdapter(this, R.layout.item_spinner_light, assignmentOptions)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            assignmentSpinner.adapter = adapter
            assignmentSpinner.isEnabled = true

            val index = assignmentOptions.indexOf(player.assignmentType.orEmpty())
            assignmentSpinner.setSelection(if (index > 0) index else 0, false)

            instructionInput.isEnabled = true
            instructionInput.setText(player.instruction.orEmpty())
        }

        // Spinner selection callbacks are posted, so release the guard after they run.
        assignmentSpinner.post { isBindingPanel = false }
    }

    private fun writePanelToSelectedPlayer() {
        val player = drawingView.getSelectedPlayer() ?: return
        val position = assignmentSpinner.selectedItemPosition
        val assignmentType = if (position > 0) assignmentOptions.getOrNull(position).orEmpty() else ""

        drawingView.updatePlayerDetails(
            player.id,
            assignmentType,
            instructionInput.text.toString()
        )
    }

    private fun setPlayType(type: String) {
        playType = type
        styleToggle(passButton, type == PlayFormation.PLAY_TYPE_PASS)
        styleToggle(runButton, type == PlayFormation.PLAY_TYPE_RUN)
    }

    private fun setMode(mode: CoachDrawingView.Mode) {
        drawingView.mode = mode
        styleToggle(moveButton, mode == CoachDrawingView.Mode.MOVE)
        styleToggle(routeButton, mode == CoachDrawingView.Mode.ROUTE)

        modeHintText.text = when (mode) {
            CoachDrawingView.Mode.MOVE -> "Drag any player to set the formation."
            CoachDrawingView.Mode.ROUTE -> "Tap a player, then drag from them to draw their route."
        }
    }

    private fun styleToggle(button: MaterialButton, selected: Boolean) {
        button.backgroundTintList = ColorStateList.valueOf(if (selected) gold else Color.TRANSPARENT)
        button.setTextColor(if (selected) navy else Color.WHITE)
        button.strokeColor = ColorStateList.valueOf(if (selected) gold else slate)
    }

    private fun clearSelectedRoute() {
        val player = drawingView.getSelectedPlayer()
        if (player == null) {
            Toast.makeText(this, "Select a player first", Toast.LENGTH_SHORT).show()
            return
        }

        if (!drawingView.hasRoute(player.id)) {
            Toast.makeText(this, "${player.displayLabel} has no route", Toast.LENGTH_SHORT).show()
            return
        }

        drawingView.clearRoute(player.id)
        Toast.makeText(this, "Cleared ${player.displayLabel} route", Toast.LENGTH_SHORT).show()
    }

    private fun confirmResetFormation() {
        AlertDialog.Builder(this)
            .setTitle("Reset formation?")
            .setMessage(
                "This returns the default 11 players to their starting spots and removes every " +
                    "route, assignment and instruction on this screen. The play name, formation " +
                    "name and play type stay. Nothing in the Play Library changes until you save."
            )
            .setPositiveButton("Reset") { _, _ ->
                endSwap()
                drawingView.setFormation(PlayFormation.defaultPlayers(), emptyMap())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun renderBench() {
        benchContainer.removeAllViews()

        val benchPlayers = drawingView.getPlayers().filter { !it.isActive }
        if (benchPlayers.isEmpty()) {
            benchContainer.addView(TextView(this).apply {
                text = "No substitutes"
                setTextColor(Color.parseColor("#778DA9"))
            })
            return
        }

        benchPlayers.forEach { player ->
            val button = MaterialButton(
                this,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                text = player.displayLabel
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(4) }
                setOnClickListener { onBenchPlayerTapped(player) }
            }

            styleToggle(button, player.id == pendingSwapBenchId)
            benchContainer.addView(button)
        }
    }

    private fun onBenchPlayerTapped(player: PlayerPosition) {
        if (pendingSwapBenchId == player.id) {
            endSwap()
            return
        }

        pendingSwapBenchId = player.id
        drawingView.swapPending = true
        swapHintText.text = "Tap the player on the field that ${player.displayLabel} replaces."
        swapHintText.setTextColor(gold)
        cancelSwapButton.visibility = View.VISIBLE
        renderBench()
    }

    private fun endSwap() {
        pendingSwapBenchId = null
        drawingView.swapPending = false
        swapHintText.text = "Tap a substitute, then tap the player on the field it replaces."
        swapHintText.setTextColor(Color.parseColor("#B0BCCB"))
        cancelSwapButton.visibility = View.GONE
        renderBench()
    }

    private fun confirmLeave() {
        AlertDialog.Builder(this)
            .setTitle("Leave without saving?")
            .setMessage("Changes on this screen will be lost.")
            .setPositiveButton("Leave") { _, _ -> finish() }
            .setNegativeButton("Stay", null)
            .show()
    }

    private fun savePlay() {
        val name = playNameInput.text.toString().trim()
        if (name.isBlank()) {
            Toast.makeText(this, "Enter a play name", Toast.LENGTH_SHORT).show()
            return
        }

        // Make sure the latest panel edits are on the selected player before saving.
        if (!isBindingPanel) writePanelToSelectedPlayer()

        val players = drawingView.getPlayers()
        val problem = PlayFormation.validate(players)
        if (problem != null) {
            Toast.makeText(this, problem, Toast.LENGTH_LONG).show()
            return
        }

        val play = PlayFormation.buildPlay(
            playName = name,
            formationName = formationInput.text.toString().trim(),
            playType = playType,
            players = players,
            movements = drawingView.getMovements(),
            imageResourceName = originalImageResourceName
        )

        saveToDatabase(name, play)
    }

    private fun loadEditPlayIfPresent() {
        val editJson = intent.getStringExtra("edit_play_json") ?: return

        try {
            val play = gson.fromJson(editJson, PlayMessage::class.java)
            val loaded = PlayFormation.fromSavedPlay(play)

            isEditMode = true
            originalPlayName = play.playName
            originalImageResourceName = play.imageResourceName.orEmpty()

            playNameInput.setText(play.playName)
            formationInput.setText(play.formationName.orEmpty())
            setPlayType(
                if (play.playType == PlayFormation.PLAY_TYPE_RUN) PlayFormation.PLAY_TYPE_RUN
                else PlayFormation.PLAY_TYPE_PASS
            )

            drawingView.setFormation(loaded.players, loaded.movements)
            findViewById<TextView>(R.id.tvScreenTitle).text = "EDIT PLAY"

            if (loaded.droppedRouteKeys.isNotEmpty()) {
                Toast.makeText(
                    this,
                    "Routes for ${loaded.droppedRouteKeys.joinToString(", ")} are not on the field and were not loaded",
                    Toast.LENGTH_LONG
                ).show()
            }
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

                // A play renamed while editing keeps its place in any Hurry-Up Packages.
                if (isEditMode && originalPlayName != null && originalPlayName != name) {
                    HurryUpRepository.onPlayRenamed(this, originalPlayName!!, name)
                }

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

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
