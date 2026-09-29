package com.cloud9.gridsync

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cloud9.gridsync.database.AppDatabase
import com.cloud9.gridsync.database.DefaultPlaySeeder
import com.cloud9.gridsync.network.HurryUpRepository
import com.cloud9.gridsync.network.PlayMessage
import com.cloud9.gridsync.network.SessionLogManager
import com.cloud9.gridsync.network.TabletServerManager
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Hurry-Up Play Package screen (Product Design wireframe "Hurry Up Play").
 *
 *  - The coach keeps a short list of plays picked from the Play Library.
 *  - Tapping a play sends it right away to every assigned watch. Each watch
 *    receives only its own role-specific assignment (same path as the Play Library).
 *  - Long-pressing a play offers "Remove from Hurry-Up".
 *  - The list icon in the top bar opens a picker of Play Library plays so the
 *    coach can add or remove plays.
 */
class HurryUpActivity : SwipeBackActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var hintText: TextView
    private lateinit var statusText: TextView
    private lateinit var adapter: HurryUpAdapter

    private val gson = Gson()
    private val timeFormat = SimpleDateFormat("h:mm:ss a", Locale.US)

    /** Every play in the library, by name (trashed plays excluded). */
    private var libraryPlays: List<PlayMessage> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hurry_up)

        recyclerView = findViewById(R.id.hurryUpRecyclerView)
        emptyStateText = findViewById(R.id.emptyStateText)
        hintText = findViewById(R.id.hintText)
        statusText = findViewById(R.id.statusText)

        findViewById<ImageButton>(R.id.backButton).setOnClickListener { goBack() }
        findViewById<ImageButton>(R.id.editPackageButton).setOnClickListener { showPlayPicker() }

        adapter = HurryUpAdapter(
            onPlayTapped = { play -> sendPlay(play) },
            onPlayLongPressed = { play, anchor -> showRemoveMenu(play, anchor) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        loadPlays()
    }

    // ---- Loading -----------------------------------------------------------

    private fun loadPlays() {
        val context = applicationContext

        thread {
            DefaultPlaySeeder.seedDefaultsIfMissing(context)

            val dao = AppDatabase.getDatabase(context).playDao()
            val plays = dao.getAllPlays().mapNotNull { entity ->
                try {
                    gson.fromJson(entity.dataJson, PlayMessage::class.java)
                } catch (_: Exception) {
                    null
                }
            }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                libraryPlays = plays
                refreshList()
            }
        }
    }

    private fun packagePlays(): List<PlayMessage> {
        val byName = libraryPlays.associateBy { it.playName }
        // Keep the coach's order; skip plays that were deleted or trashed.
        return HurryUpRepository.getPlayNames(this).mapNotNull { byName[it] }
    }

    private fun refreshList() {
        val plays = packagePlays()
        adapter.submit(plays)

        val isEmpty = plays.isEmpty()
        emptyStateText.visibility = if (isEmpty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE

        hintText.text = when {
            isEmpty -> "Build your package by adding plays from the Play Library."
            plays.size < 2 -> "Add at least 2 plays to make a Hurry-Up Package. Tap a play to send it."
            else -> "Tap a play to send it instantly to every assigned watch."
        }
    }

    // ---- Sending -----------------------------------------------------------

    private fun sendPlay(play: PlayMessage) {
        val started = System.currentTimeMillis()
        val watchCount = TabletServerManager.sendPlayToAssigned(play)

        if (watchCount == 0) {
            SessionLogManager.addEntry("Hurry-Up play ${play.playName} not sent - no assigned watches connected")
            showStatus("Not sent - no assigned watches are connected", success = false)
            Toast.makeText(this, "No assigned watches are connected", Toast.LENGTH_SHORT).show()
            return
        }

        val watchWord = if (watchCount == 1) "watch" else "watches"
        SessionLogManager.addEntry("Hurry-Up play ${play.playName} sent to $watchCount $watchWord")
        showStatus(
            "Sent \"${play.playName}\" to $watchCount $watchWord at ${timeFormat.format(Date(started))}",
            success = true
        )
    }

    private fun showStatus(message: String, success: Boolean) {
        statusText.text = message
        statusText.setTextColor(if (success) 0xFF0F9D58.toInt() else 0xFFB3261E.toInt())
        statusText.visibility = View.VISIBLE
    }

    // ---- Editing the package -------------------------------------------------

    private fun showRemoveMenu(play: PlayMessage, anchor: View) {
        val menu = PopupMenu(this, anchor)
        menu.menu.add("Remove from Hurry-Up")
        menu.setOnMenuItemClickListener {
            HurryUpRepository.removePlay(this, play.playName)
            SessionLogManager.addEntry("Removed ${play.playName} from Hurry-Up Package")
            refreshList()
            true
        }
        menu.show()
    }

    private fun showPlayPicker() {
        val available = libraryPlays.map { it.playName }.sortedBy { it.lowercase() }

        if (available.isEmpty()) {
            Toast.makeText(this, "Your Play Library is empty", Toast.LENGTH_SHORT).show()
            return
        }

        val current = HurryUpRepository.getPlayNames(this)
        val checked = BooleanArray(available.size) { available[it] in current }

        AlertDialog.Builder(this)
            .setTitle("Choose Hurry-Up plays")
            .setMultiChoiceItems(available.toTypedArray(), checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Save") { _, _ ->
                val selected = available.filterIndexed { index, _ -> checked[index] }.toSet()

                // Keep the coach's existing order, then add newly picked plays.
                val ordered = current.filter { it in selected } +
                        selected.filter { it !in current }

                HurryUpRepository.setPlayNames(this, ordered)
                SessionLogManager.addEntry("Hurry-Up Package updated (${ordered.size} plays)")
                refreshList()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

private class HurryUpAdapter(
    private val onPlayTapped: (PlayMessage) -> Unit,
    private val onPlayLongPressed: (PlayMessage, View) -> Unit
) : RecyclerView.Adapter<HurryUpAdapter.ViewHolder>() {

    private val plays = mutableListOf<PlayMessage>()

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val number: TextView = itemView.findViewById(R.id.hurryUpNumber)
        val name: TextView = itemView.findViewById(R.id.hurryUpPlayName)
    }

    fun submit(newPlays: List<PlayMessage>) {
        plays.clear()
        plays.addAll(newPlays)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_hurry_up_play, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val play = plays[position]
        holder.number.text = (position + 1).toString()
        holder.name.text = play.playName

        holder.itemView.setOnClickListener { onPlayTapped(play) }
        holder.itemView.setOnLongClickListener { view ->
            onPlayLongPressed(play, view)
            true
        }
    }

    override fun getItemCount(): Int = plays.size
}
