package com.cloud9.gridsync

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cloud9.gridsync.database.HurryUpRepository
import com.cloud9.gridsync.network.PlayMessage
import com.cloud9.gridsync.network.SessionLogManager
import com.cloud9.gridsync.network.TabletServerManager
import kotlin.concurrent.thread

class HurryUpPackageDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PACKAGE_ID = "hurry_up_package_id"
    }

    private lateinit var adapter: PlayCardAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var titleText: TextView

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

        findViewById<ImageButton>(R.id.backButton).setOnClickListener { finish() }
        findViewById<TextView>(R.id.addFromLibraryButton).setOnClickListener { addFromLibrary() }
        findViewById<TextView>(R.id.packageMenuButton).setOnClickListener { showPackageMenu(it) }

        // Same visual cards as the Play Library, with package actions instead of edit and trash.
        adapter = PlayCardAdapter(
            showEdit = false,
            showOrder = true,
            onOpen = { PlayDetailDialog.show(this, it, onSend = ::sendPlay) },
            onSend = { sendPlay(it) },
            onMenu = { view, play -> showPlayMenu(view, play) }
        )
        recyclerView.layoutManager = GridLayoutManager(this, (resources.configuration.screenWidthDp / 340).coerceIn(1, 4))
        recyclerView.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        loadPackage()
    }

    private fun loadPackage() {
        thread {
            val entity = HurryUpRepository.getPackage(applicationContext, packageId)
            val plays = if (entity != null) HurryUpRepository.getPlays(applicationContext, packageId) else emptyList()

            runOnUiThread {
                if (entity == null) {
                    Toast.makeText(this, "This Hurry-Up Package no longer exists", Toast.LENGTH_SHORT).show()
                    finish()
                    return@runOnUiThread
                }

                packageName = entity.packageName
                titleText.text = packageName.uppercase()

                if (!loggedOpen) {
                    loggedOpen = true
                    SessionLogManager.addEntry("Hurry-Up Package \"$packageName\" opened")
                }

                adapter.replaceAll(plays)
                emptyStateText.visibility = if (plays.isEmpty()) View.VISIBLE else View.GONE
                recyclerView.visibility = if (plays.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    // Uses the same send path as the Play Library, so the QB still gets the full play and every
    // other assigned watch gets only its own assignment.
    private fun sendPlay(play: PlayMessage) {
        TabletServerManager.sendPlayToAssigned(play)
        SessionLogManager.addEntry("Hurry-Up play \"${play.playName}\" sent")

        val message = if (TabletServerManager.getConnectedRoles().isEmpty()) {
            "Sent ${play.playName} (no assigned watches are connected)"
        } else {
            "Sent ${play.playName}"
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun addFromLibrary() {
        thread {
            val libraryPlays = HurryUpRepository.getLibraryPlayNames(applicationContext)
            val inPackage = HurryUpRepository.getPlays(applicationContext, packageId).map { it.playName }.toSet()

            runOnUiThread {
                HurryUpDialogs.showPlayPickerDialog(
                    activity = this,
                    title = "Add to \"$packageName\"",
                    playNames = libraryPlays,
                    labels = libraryPlays.map { if (it in inPackage) "$it  (already in package)" else it },
                    positiveLabel = "Add",
                    minimumSelection = 1,
                    minimumMessage = "Select at least one play"
                ) { selected -> addPlays(selected) }
            }
        }
    }

    private fun addPlays(playNames: List<String>) {
        thread {
            val result = HurryUpRepository.addPlays(applicationContext, packageId, playNames)
            runOnUiThread {
                result.added.forEach { SessionLogManager.addEntry("Play \"$it\" added to \"$packageName\"") }

                when {
                    result.added.isEmpty() ->
                        Toast.makeText(this, HurryUpRepository.DUPLICATE_PLAY_MESSAGE, Toast.LENGTH_LONG).show()
                    result.alreadyInPackage.isNotEmpty() ->
                        Toast.makeText(
                            this,
                            "Added ${result.added.size}. Already in this package: ${result.alreadyInPackage.joinToString(", ")}",
                            Toast.LENGTH_LONG
                        ).show()
                    else ->
                        Toast.makeText(this, "Added ${result.added.size} to \"$packageName\"", Toast.LENGTH_SHORT).show()
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
                "Remove from Hurry-Up Package" -> removePlay(play)
            }
            true
        }
        popup.show()
    }

    private fun removePlay(play: PlayMessage) {
        thread {
            val error = HurryUpRepository.removePlay(applicationContext, packageId, play.playName)
            runOnUiThread {
                if (error != null) {
                    AlertDialog.Builder(this)
                        .setTitle("Can't remove ${play.playName}")
                        .setMessage("$error\n\nAdd another play first, or delete the whole package. Its plays stay in the Play Library.")
                        .setPositiveButton("Delete Package") { _, _ ->
                            HurryUpDialogs.confirmDeletePackage(this, packageId, packageName) { finish() }
                        }
                        .setNegativeButton("Keep", null)
                        .show()
                } else {
                    SessionLogManager.addEntry("Play \"${play.playName}\" removed from \"$packageName\"")
                    Toast.makeText(this, "Removed ${play.playName} from \"$packageName\"", Toast.LENGTH_SHORT).show()
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
                "Rename Package" -> HurryUpDialogs.renamePackage(this, packageId, packageName) { loadPackage() }
                "Delete Package" -> HurryUpDialogs.confirmDeletePackage(this, packageId, packageName) { finish() }
            }
            true
        }
        popup.show()
    }
}
