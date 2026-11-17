
package com.cloud9.gridsync

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cloud9.gridsync.database.HurryUpRepository
import com.cloud9.gridsync.network.PlayMessage
import com.cloud9.gridsync.network.PlayFormation
import com.cloud9.gridsync.network.SessionLogManager
import com.cloud9.gridsync.network.TabletServerManager
import com.cloud9.gridsync.ui.CoachDrawingView
import kotlin.concurrent.thread

class HurryUpPackageDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PACKAGE_ID = "hurry_up_package_id"
    }

    private lateinit var adapter: HurryUpPlayAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var titleText: TextView

    // Play preview components
    private lateinit var previewTitleText: TextView
    private lateinit var previewDetailText: TextView
    private lateinit var previewContainer: FrameLayout
    private lateinit var previewPlaceholderText: TextView
    private lateinit var previewSendButton: Button

    private var selectedPlay: PlayMessage? = null

    private var packageId: Long = 0
    private var packageName: String = ""
    private var loggedOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hurry_up_package_detail)

        packageId = intent.getLongExtra(EXTRA_PACKAGE_ID, 0)

        recyclerView = findViewById(R.id.playRecyclerView)
        emptyStateText = findViewById(R.id.emptyStateText)
        titleText = findViewById(R.id.packageTitleText)

        // Initialize preview components
        previewTitleText = findViewById(R.id.previewTitleText)
        previewDetailText = findViewById(R.id.previewDetailText)
        previewContainer = findViewById(R.id.previewContainer)
        previewPlaceholderText = findViewById(R.id.previewPlaceholderText)
        previewSendButton = findViewById(R.id.previewSendButton)

        findViewById<ImageButton>(R.id.backButton)
            .setOnClickListener { finish() }

        findViewById<Button>(R.id.addFromLibraryButton)
            .setOnClickListener { addFromLibrary() }

        findViewById<ImageButton>(R.id.packageMenuButton)
            .setOnClickListener { showPackageMenu(it) }

        previewSendButton.isEnabled = false

        previewSendButton.setOnClickListener {
            selectedPlay?.let { play ->
                sendPlay(play)
            }
        }

        adapter = HurryUpPlayAdapter(
            onSend = { sendPlay(it) },
            onMenu = { view, play ->
                showPlayMenu(view, play)
            },
            onPlayClicked = { play ->
                selectedPlay = play
                showPlayPreview(play)
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        clearPlayPreview()
    }

    override fun onResume() {
        super.onResume()
        loadPackage()
    }

    private fun loadPackage() {
        thread {
            val entity = HurryUpRepository.getPackage(
                applicationContext,
                packageId
            )

            val plays = if (entity != null) {
                HurryUpRepository.getPlays(
                    applicationContext,
                    packageId
                )
            } else {
                emptyList()
            }

            runOnUiThread {
                if (entity == null) {
                    Toast.makeText(
                        this,
                        "This Hurry-Up Package no longer exists",
                        Toast.LENGTH_SHORT
                    ).show()

                    finish()
                    return@runOnUiThread
                }

                packageName = entity.packageName
                titleText.text = packageName.uppercase()

                if (!loggedOpen) {
                    loggedOpen = true
                    SessionLogManager.addEntry(
                        "Hurry-Up Package \"$packageName\" opened"
                    )
                }

                val previouslySelectedName = selectedPlay?.playName

                adapter.replaceAll(plays)

                emptyStateText.visibility =
                    if (plays.isEmpty()) View.VISIBLE else View.GONE

                recyclerView.visibility =
                    if (plays.isEmpty()) View.GONE else View.VISIBLE

                // Refresh the selected play if it still exists.
                val refreshedPlay = plays.firstOrNull {
                    it.playName == previouslySelectedName
                }

                selectedPlay = refreshedPlay

                if (refreshedPlay != null) {
                    showPlayPreview(refreshedPlay)
                } else {
                    clearPlayPreview()
                }
            }
        }
    }

    // Display the saved formation and player routes.
    private fun showPlayPreview(play: PlayMessage) {
        selectedPlay = play

        previewTitleText.text = play.playName

        val details = listOfNotNull(
            play.formationName?.takeIf { it.isNotBlank() },
            play.playType?.takeIf { it.isNotBlank() }
        ).joinToString(" | ")

        previewDetailText.text = details

        previewDetailText.visibility =
            if (details.isBlank()) View.GONE else View.VISIBLE

        previewPlaceholderText.visibility = View.GONE
        previewContainer.visibility = View.VISIBLE
        previewSendButton.isEnabled = true

        val loaded = PlayFormation.fromSavedPlay(play)

        previewContainer.removeAllViews()

        val drawingView = CoachDrawingView(this, null)

        drawingView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )

        drawingView.setFormation(
            loaded.players,
            loaded.movements
        )

        // Keep the preview read-only.
        drawingView.setOnTouchListener { _, _ -> true }

        previewContainer.addView(drawingView)
    }

    // Reset the preview when no play is selected.
    private fun clearPlayPreview() {
        selectedPlay = null

        previewTitleText.text = "PLAY PREVIEW"
        previewDetailText.text = ""
        previewDetailText.visibility = View.GONE

        previewContainer.removeAllViews()
        previewContainer.visibility = View.GONE

        previewPlaceholderText.visibility = View.VISIBLE
        previewSendButton.isEnabled = false
    }

    // Preserve the existing QB and role-specific sending logic.
    private fun sendPlay(play: PlayMessage) {
        TabletServerManager.sendPlayToAssigned(play)

        SessionLogManager.addEntry(
            "Hurry-Up play \"${play.playName}\" sent"
        )

        val message = if (
            TabletServerManager.getConnectedRoles().isEmpty()
        ) {
            "Sent ${play.playName} (no assigned watches are connected)"
        } else {
            "Sent ${play.playName}"
        }

        Toast.makeText(
            this,
            message,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun addFromLibrary() {
        thread {
            val libraryPlays =
                HurryUpRepository.getLibraryPlayNames(applicationContext)

            val inPackage = HurryUpRepository.getPlays(
                applicationContext,
                packageId
            ).map { it.playName }.toSet()

            runOnUiThread {
                HurryUpDialogs.showPlayPickerDialog(
                    activity = this,
                    title = "Add to \"$packageName\"",
                    playNames = libraryPlays,
                    labels = libraryPlays.map {
                        if (it in inPackage) {
                            "$it  (already in package)"
                        } else {
                            it
                        }
                    },
                    positiveLabel = "Add",
                    minimumSelection = 1,
                    minimumMessage = "Select at least one play"
                ) { selected ->
                    addPlays(selected)
                }
            }
        }
    }

    private fun addPlays(playNames: List<String>) {
        thread {
            val result = HurryUpRepository.addPlays(
                applicationContext,
                packageId,
                playNames
            )

            runOnUiThread {
                result.added.forEach {
                    SessionLogManager.addEntry(
                        "Play \"$it\" added to \"$packageName\""
                    )
                }

                when {
                    result.added.isEmpty() ->
                        Toast.makeText(
                            this,
                            HurryUpRepository.DUPLICATE_PLAY_MESSAGE,
                            Toast.LENGTH_LONG
                        ).show()

                    result.alreadyInPackage.isNotEmpty() ->
                        Toast.makeText(
                            this,
                            "Added ${result.added.size}. Already in this package: ${result.alreadyInPackage.joinToString(", ")}",
                            Toast.LENGTH_LONG
                        ).show()

                    else ->
                        Toast.makeText(
                            this,
                            "Added ${result.added.size} to \"$packageName\"",
                            Toast.LENGTH_SHORT
                        ).show()
                }

                loadPackage()
            }
        }
    }

    private fun showPlayMenu(anchor: View, play: PlayMessage) {
        val popup = PopupMenu(this, anchor)

        popup.menu.add("Send")
        popup.menu.add("Remove from Hurry-Up Package")

        popup.setOnMenuItemClickListener { item ->
            when (item.title.toString()) {
                "Send" -> sendPlay(play)

                "Remove from Hurry-Up Package" ->
                    removePlay(play)
            }

            true
        }

        popup.show()
    }

    private fun removePlay(play: PlayMessage) {
        thread {
            val error = HurryUpRepository.removePlay(
                applicationContext,
                packageId,
                play.playName
            )

            runOnUiThread {
                if (error != null) {
                    AlertDialog.Builder(this)
                        .setTitle("Can't remove ${play.playName}")
                        .setMessage(
                            "$error\n\nAdd another play first, or delete the whole package. Its plays stay in the Play Library."
                        )
                        .setPositiveButton("Delete Package") { _, _ ->
                            HurryUpDialogs.confirmDeletePackage(
                                this,
                                packageId,
                                packageName
                            ) {
                                finish()
                            }
                        }
                        .setNegativeButton("Keep", null)
                        .show()
                } else {
                    SessionLogManager.addEntry(
                        "Play \"${play.playName}\" removed from \"$packageName\""
                    )

                    Toast.makeText(
                        this,
                        "Removed ${play.playName} from \"$packageName\"",
                        Toast.LENGTH_SHORT
                    ).show()

                    loadPackage()
                }
            }
        }
    }

    private fun showPackageMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)

        popup.menu.add("Rename Package")
        popup.menu.add("Delete Package")

        popup.setOnMenuItemClickListener { item ->
            when (item.title.toString()) {
                "Rename Package" ->
                    HurryUpDialogs.renamePackage(
                        this,
                        packageId,
                        packageName
                    ) {
                        loadPackage()
                    }

                "Delete Package" ->
                    HurryUpDialogs.confirmDeletePackage(
                        this,
                        packageId,
                        packageName
                    ) {
                        finish()
                    }
            }

            true
        }

        popup.show()
    }
}
