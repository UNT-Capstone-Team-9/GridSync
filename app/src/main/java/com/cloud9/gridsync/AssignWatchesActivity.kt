package com.cloud9.gridsync

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.cloud9.gridsync.network.ConnectedWatch
import com.cloud9.gridsync.network.RoleRepository
import com.cloud9.gridsync.network.TabletServerManager

class AssignWatchesActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var pairCodeText: TextView
    private lateinit var emptyText: TextView
    private lateinit var watchListView: ListView

    private lateinit var adapter: ArrayAdapter<String>

    private var currentWatches: List<ConnectedWatch> = emptyList()
    private var roles: MutableList<String> = mutableListOf()

    private val watchListener = object : TabletServerManager.WatchListListener {
        override fun onWatchListChanged(watches: List<ConnectedWatch>) {
            currentWatches = watches
            runOnUiThread {
                renderWatchList(watches)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_assign_watches)

        val backButton = findViewById<ImageButton>(R.id.backButton)
        statusText = findViewById(R.id.statusText)
        pairCodeText = findViewById(R.id.pairCodeText)
        emptyText = findViewById(R.id.emptyText)
        watchListView = findViewById(R.id.watchListView)

        backButton.setOnClickListener {
            finish()
        }

        roles = RoleRepository.getRoles(this).map { it.trim() }.toMutableList()

        adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            mutableListOf()
        )
        watchListView.adapter = adapter

        pairCodeText.text = "Pair code ${TabletServerManager.PAIR_CODE}"
        statusText.text = "Waiting for watches"

        watchListView.setOnItemClickListener { _, _, position, _ ->
            if (position in currentWatches.indices) {
                showAssignRoleDialog(currentWatches[position])
            }
        }
    }

    override fun onStart() {
        super.onStart()
        TabletServerManager.addListener(watchListener)
        renderWatchList(TabletServerManager.getConnectedWatches())
    }

    override fun onStop() {
        super.onStop()
        TabletServerManager.removeListener(watchListener)
    }

    private fun renderWatchList(watches: List<ConnectedWatch>) {
        val items = watches.map { watch ->
            val roleText = watch.role ?: "Unassigned"
            "${watch.watchName}   ID ${watch.watchId}   Role $roleText"
        }

        adapter.clear()
        adapter.addAll(items)
        adapter.notifyDataSetChanged()

        if (watches.isEmpty()) {
            statusText.text = "Waiting for watches"
            emptyText.text = "No connected watches"
            emptyText.visibility = View.VISIBLE
            watchListView.visibility = View.GONE
        } else {
            statusText.text = "Tap a watch to assign or edit role"
            emptyText.visibility = View.GONE
            watchListView.visibility = View.VISIBLE
        }
    }

    private fun showAssignRoleDialog(watch: ConnectedWatch) {
        val options = mutableListOf("Unassign")
        options.addAll(roles)

        AlertDialog.Builder(this)
            .setTitle("Assign role to ${watch.watchName}")
            .setItems(options.toTypedArray()) { _, which ->
                val selected = options[which].trim()

                if (selected == "Unassign") {
                    showUnassignConfirmation(watch)
                    return@setItems
                }

                val existingWatchId = TabletServerManager.getAssignedWatchIdForRole(selected)

                if (existingWatchId != null && existingWatchId != watch.watchId) {
                    val existingWatchName = currentWatches.firstOrNull {
                        it.watchId == existingWatchId
                    }?.watchName ?: "another watch"

                    AlertDialog.Builder(this)
                        .setTitle("Move role")
                        .setMessage("$selected is currently assigned to $existingWatchName. Move it to ${watch.watchName}?")
                        .setPositiveButton("Move") { _, _ ->
                            TabletServerManager.assignRole(watch.watchId, selected)
                            Toast.makeText(
                                this,
                                "${watch.watchName} assigned to $selected",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    TabletServerManager.assignRole(watch.watchId, selected)
                    Toast.makeText(
                        this,
                        "${watch.watchName} assigned to $selected",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showUnassignConfirmation(watch: ConnectedWatch) {
        AlertDialog.Builder(this)
            .setTitle("Unassign watch")
            .setMessage("Remove the role from ${watch.watchName}?")
            .setPositiveButton("Unassign") { _, _ ->
                TabletServerManager.unassignRole(watch.watchId)
                Toast.makeText(
                    this,
                    "${watch.watchName} is now unassigned",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}