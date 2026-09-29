package com.cloud9.gridsync

import android.os.Bundle
import android.text.InputFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.cloud9.gridsync.network.ConnectedWatch
import com.cloud9.gridsync.network.RoleRepository
import com.cloud9.gridsync.network.TabletServerManager
import com.cloud9.gridsync.network.WatchNameStore

class AssignWatchesActivity : SwipeBackActivity() {

    private lateinit var statusText: TextView
    private lateinit var pairCodeText: TextView
    private lateinit var emptyText: TextView
    private lateinit var watchListView: ListView

    private lateinit var adapter: WatchListAdapter

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
            goBack()
        }

        roles = RoleRepository.getRoles(this).map { it.trim() }.toMutableList()

        adapter = WatchListAdapter()
        watchListView.adapter = adapter

        pairCodeText.text = "Pair code ${TabletServerManager.PAIR_CODE}"
        statusText.text = "Waiting for watches"

        watchListView.setOnItemClickListener { _, _, position, _ ->
            if (position in currentWatches.indices) {
                showWatchOptionsDialog(currentWatches[position])
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
        adapter.submit(watches)

        if (watches.isEmpty()) {
            statusText.text = "Waiting for watches"
            emptyText.text = "No connected watches"
            emptyText.visibility = View.VISIBLE
            watchListView.visibility = View.GONE
        } else {
            statusText.text = "Tap a watch to assign a role or rename it"
            emptyText.visibility = View.GONE
            watchListView.visibility = View.VISIBLE
        }
    }

    private fun showWatchOptionsDialog(watch: ConnectedWatch) {
        val hasCustomName = TabletServerManager.getCustomWatchName(watch.watchId) != null

        val options = mutableListOf("Assign / change role", "Rename watch")
        if (hasCustomName) options.add("Reset to device name")

        AlertDialog.Builder(this)
            .setTitle(watch.watchName)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Assign / change role" -> showAssignRoleDialog(watch)
                    "Rename watch" -> showRenameDialog(watch)
                    else -> {
                        TabletServerManager.renameWatch(watch.watchId, "")
                        Toast.makeText(
                            this,
                            "Name reset to ${watch.deviceName}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRenameDialog(watch: ConnectedWatch) {
        val input = EditText(this).apply {
            setSingleLine(true)
            hint = watch.deviceName
            filters = arrayOf(InputFilter.LengthFilter(WatchNameStore.MAX_NAME_LENGTH))
            setText(TabletServerManager.getCustomWatchName(watch.watchId) ?: "")
            setSelection(text.length)
        }

        val padding = dpToPx(24)
        val container = FrameLayout(this).apply {
            setPadding(padding, dpToPx(8), padding, 0)
            addView(
                input,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        AlertDialog.Builder(this)
            .setTitle("Rename watch")
            .setMessage("Device: ${watch.deviceName}\nExample: QB Watch")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                TabletServerManager.renameWatch(watch.watchId, newName)
                Toast.makeText(
                    this,
                    if (newName.isBlank()) "Name reset to ${watch.deviceName}" else "Renamed to $newName",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
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

private class WatchListAdapter : android.widget.BaseAdapter() {

    private val items = mutableListOf<ConnectedWatch>()

    fun submit(watches: List<ConnectedWatch>) {
        items.clear()
        items.addAll(watches)
        notifyDataSetChanged()
    }

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): ConnectedWatch = items[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(parent.context)
            .inflate(R.layout.item_watch, parent, false)

        val watch = items[position]
        val roleText = watch.role ?: "Unassigned"

        view.findViewById<TextView>(R.id.watchNameText).text = watch.watchName

        val details = StringBuilder("ID ${watch.watchId}  \u2022  Role $roleText")
        if (watch.deviceName != watch.watchName) {
            details.append("  \u2022  ${watch.deviceName}")
        }
        view.findViewById<TextView>(R.id.watchDetailText).text = details.toString()

        return view
    }
}
