package com.cloud9.gridsync

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
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
            renderWatchList(watches)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_assign_watches)

        TabletServerManager.start(applicationContext)

        val backButton = findViewById<ImageButton>(R.id.backButton)
        val editRolesButton = findViewById<TextView>(R.id.editRolesButton)
        statusText = findViewById(R.id.statusText)
        pairCodeText = findViewById(R.id.pairCodeText)
        emptyText = findViewById(R.id.emptyText)
        watchListView = findViewById(R.id.watchListView)

        roles = RoleRepository.getRoles(this)

        backButton.setOnClickListener {
            finish()
        }

        editRolesButton.setOnClickListener {
            showEditRolesDialog()
        }

        pairCodeText.text = "Pair code  ${TabletServerManager.PAIR_CODE}"

        adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            mutableListOf()
        )

        watchListView.adapter = adapter

        watchListView.setOnItemClickListener { _, _, position, _ ->
            val watch = currentWatches[position]
            showAssignRoleDialog(watch)
        }
    }

    override fun onStart() {
        super.onStart()
        TabletServerManager.addListener(watchListener)
        currentWatches = TabletServerManager.getConnectedWatches()
        renderWatchList(currentWatches)
    }

    override fun onStop() {
        super.onStop()
        TabletServerManager.removeListener(watchListener)
    }

    private fun renderWatchList(watches: List<ConnectedWatch>) {
        val count = watches.size
        statusText.text = if (count == 1) {
            "1 watch connected"
        } else {
            "$count watches connected"
        }

        emptyText.visibility = if (watches.isEmpty()) TextView.VISIBLE else TextView.GONE
        watchListView.visibility = if (watches.isEmpty()) ListView.GONE else ListView.VISIBLE

        adapter.clear()
        adapter.addAll(
            watches.map {
                val roleLabel = it.role ?: "Unassigned"
                "${it.watchName}  •  ${it.ipAddress}  •  ${roleLabel}"
            }
        )
        adapter.notifyDataSetChanged()
    }

    private fun showAssignRoleDialog(watch: ConnectedWatch) {
        if (roles.isEmpty()) {
            Toast.makeText(this, "No roles available. Add roles first.", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Assign role to ${watch.watchName}")
            .setItems(roles.toTypedArray()) { _, which ->
                TabletServerManager.assignRole(watch.watchId, roles[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditRolesDialog() {
        val input = EditText(this)
        input.setText(roles.joinToString("\n"))
        input.hint = "Enter one role per line"

        AlertDialog.Builder(this)
            .setTitle("Edit roles")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val updatedRoles = input.text.toString()
                    .lines()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }

                if (updatedRoles.isEmpty()) {
                    Toast.makeText(this, "Role list cannot be empty", Toast.LENGTH_SHORT).show()
                } else {
                    roles = updatedRoles.toMutableList()
                    RoleRepository.saveRoles(this, roles)
                    Toast.makeText(this, "Roles updated", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("Reset") { _, _ ->
                RoleRepository.resetRoles(this)
                roles = RoleRepository.getRoles(this)
                Toast.makeText(this, "Roles reset", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}