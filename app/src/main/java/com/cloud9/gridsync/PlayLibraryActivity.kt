package com.cloud9.gridsync

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cloud9.gridsync.database.AppDatabase
import com.cloud9.gridsync.database.DefaultPlaySeeder
import com.cloud9.gridsync.database.HurryUpRepository
import com.cloud9.gridsync.network.PlayFormation
import com.cloud9.gridsync.network.PlayMessage
import com.cloud9.gridsync.network.PlayPersonnel
import com.cloud9.gridsync.network.SessionLogManager
import com.cloud9.gridsync.network.TabletServerManager
import com.google.gson.Gson
import kotlin.concurrent.thread

// Visual playbook browser. Every card is drawn from the saved PlayMessage, so a play saved in the
// designer shows up here as soon as the library reloads.
class PlayLibraryActivity : AppCompatActivity() {

    private enum class SortMode(val label: String) { NAME("NAME"), NEWEST("NEWEST"), OLDEST("OLDEST") }
    private enum class TypeFilter { ALL, PASS, RUN }
    private enum class BrowseMode { ALL, FORMATION, PERSONNEL }

    private lateinit var playRecyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var playCountText: TextView
    private lateinit var searchInput: EditText
    private lateinit var sortButton: TextView
    private lateinit var groupBar: LinearLayout
    private lateinit var groupBackButton: TextView
    private lateinit var groupTitleText: TextView

    private lateinit var typeChips: Map<TypeFilter, TextView>
    private lateinit var browseChips: Map<BrowseMode, TextView>

    private lateinit var cardAdapter: PlayCardAdapter
    private lateinit var groupAdapter: PlaybookGroupAdapter
    private lateinit var gridLayoutManager: GridLayoutManager

    private val gson = Gson()
    private var allPlays: List<PlayMessage> = emptyList()

    private var sortMode = SortMode.NAME
    private var typeFilter = TypeFilter.ALL
    private var browseMode = BrowseMode.ALL
    private var selectedGroupKey: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_play_library)

        playRecyclerView = findViewById(R.id.playRecyclerView)
        emptyStateText = findViewById(R.id.emptyStateText)
        playCountText = findViewById(R.id.playCountText)
        searchInput = findViewById(R.id.searchInput)
        sortButton = findViewById(R.id.sortButton)
        groupBar = findViewById(R.id.groupBar)
        groupBackButton = findViewById(R.id.groupBackButton)
        groupTitleText = findViewById(R.id.groupTitleText)

        findViewById<ImageButton>(R.id.backButton).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.trashButton).setOnClickListener {
            startActivity(Intent(this, TrashActivity::class.java))
        }

        typeChips = mapOf(
            TypeFilter.ALL to findViewById(R.id.typeAllChip),
            TypeFilter.PASS to findViewById(R.id.typePassChip),
            TypeFilter.RUN to findViewById(R.id.typeRunChip)
        )
        typeChips.forEach { (filter, chip) ->
            chip.setOnClickListener {
                typeFilter = filter
                render()
            }
        }

        browseChips = mapOf(
            BrowseMode.ALL to findViewById(R.id.browseAllChip),
            BrowseMode.FORMATION to findViewById(R.id.browseFormationChip),
            BrowseMode.PERSONNEL to findViewById(R.id.browsePersonnelChip)
        )
        browseChips.forEach { (mode, chip) ->
            chip.setOnClickListener {
                browseMode = mode
                selectedGroupKey = null
                render()
            }
        }

        groupBackButton.setOnClickListener {
            selectedGroupKey = null
            render()
        }

        sortButton.setOnClickListener { showSortMenu() }

        cardAdapter = PlayCardAdapter(
            showEdit = true,
            onOpen = { PlayDetailDialog.show(this, it, onSend = ::sendPlay, onEdit = ::editPlay) },
            onSend = { sendPlay(it) },
            onEdit = { editPlay(it) },
            onMenu = { view, play -> showCardMenu(view, play) }
        )

        groupAdapter = PlaybookGroupAdapter { group ->
            selectedGroupKey = group.name.lowercase()
            render()
        }

        gridLayoutManager = GridLayoutManager(this, cardColumnCount())
        playRecyclerView.layoutManager = gridLayoutManager

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable?) {
                render()
            }
        })

        render()
    }

    override fun onResume() {
        super.onResume()
        loadPlaysFromDatabase()
    }

    private fun loadPlaysFromDatabase() {
        thread {
            DefaultPlaySeeder.removeUntouchedDefaults(applicationContext)

            val dao = AppDatabase.getDatabase(applicationContext).playDao()
            val thirtyDaysMillis = 30L * 24L * 60L * 60L * 1000L
            val cutoffTime = System.currentTimeMillis() - thirtyDaysMillis
            dao.deleteExpiredTrash(cutoffTime)

            val savedPlays = when (sortMode) {
                SortMode.NEWEST -> dao.getAllPlaysNewestFirst()
                SortMode.OLDEST -> dao.getAllPlaysOldestFirst()
                SortMode.NAME -> dao.getAllPlays()
            }

            val plays = savedPlays.mapNotNull { entity ->
                try {
                    gson.fromJson(entity.dataJson, PlayMessage::class.java)
                } catch (_: Exception) {
                    null
                }
            }

            runOnUiThread {
                allPlays = plays
                render()
            }
        }
    }

    // Search and play type apply first, so the group counts always match the cards inside them.
    private fun render() {
        typeChips.forEach { (filter, chip) -> chip.isSelected = filter == typeFilter }
        browseChips.forEach { (mode, chip) -> chip.isSelected = mode == browseMode }
        sortButton.text = "SORT: ${sortMode.label} ▾"
        playCountText.text = if (allPlays.size == 1) "1 saved play" else "${allPlays.size} saved plays"

        val query = searchInput.text.toString().trim().lowercase()
        val filtered = allPlays.filter { play ->
            val matchesType = when (typeFilter) {
                TypeFilter.ALL -> true
                TypeFilter.PASS -> play.playType == PlayFormation.PLAY_TYPE_PASS
                TypeFilter.RUN -> play.playType == PlayFormation.PLAY_TYPE_RUN
            }
            matchesType && (query.isBlank() || play.playName.lowercase().contains(query))
        }

        val groupKey = selectedGroupKey
        when {
            browseMode == BrowseMode.ALL -> {
                groupBar.visibility = View.GONE
                showCards(filtered, if (allPlays.isEmpty()) EMPTY_LIBRARY else "No plays match your filters")
            }

            groupKey == null -> {
                groupBar.visibility = View.GONE
                val groups = filtered
                    .groupBy { groupName(it).lowercase() }
                    .map { (_, plays) -> PlaybookGroupAdapter.Group(groupName(plays.first()), plays.size) }
                    .sortedBy { it.name.lowercase() }
                showGroups(groups)
            }

            else -> {
                val inGroup = filtered.filter { groupName(it).lowercase() == groupKey }
                groupBar.visibility = View.VISIBLE
                groupBackButton.text = if (browseMode == BrowseMode.FORMATION) "‹ ALL FORMATIONS" else "‹ ALL PERSONNEL"
                groupTitleText.text = allPlays.firstOrNull { groupName(it).lowercase() == groupKey }
                    ?.let { groupName(it) } ?: groupKey
                showCards(inGroup, "No plays in this group match your filters")
            }
        }
    }

    private fun groupName(play: PlayMessage): String {
        return when (browseMode) {
            BrowseMode.PERSONNEL -> PlayPersonnel.describe(play).groupName
            else -> PlayCardAdapter.formationLabel(play)
        }
    }

    private fun showCards(plays: List<PlayMessage>, emptyMessage: String) {
        if (playRecyclerView.adapter !== cardAdapter) {
            gridLayoutManager.spanCount = cardColumnCount()
            playRecyclerView.adapter = cardAdapter
        }
        cardAdapter.replaceAll(plays)
        setEmptyState(plays.isEmpty(), emptyMessage)
    }

    private fun showGroups(groups: List<PlaybookGroupAdapter.Group>) {
        if (playRecyclerView.adapter !== groupAdapter) {
            gridLayoutManager.spanCount = groupColumnCount()
            playRecyclerView.adapter = groupAdapter
        }
        groupAdapter.replaceAll(groups)

        val message = when {
            allPlays.isEmpty() -> EMPTY_LIBRARY
            browseMode == BrowseMode.FORMATION -> "No formations match your filters"
            else -> "No personnel groups match your filters"
        }
        setEmptyState(groups.isEmpty(), message)
    }

    private fun setEmptyState(isEmpty: Boolean, message: String) {
        emptyStateText.text = message
        emptyStateText.visibility = if (isEmpty) View.VISIBLE else View.GONE
        playRecyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    // About three cards across on a landscape tablet, one on a phone.
    private fun cardColumnCount(): Int = (screenWidthDp() / 340).coerceIn(1, 4)

    private fun groupColumnCount(): Int = (screenWidthDp() / 300).coerceIn(1, 4)

    private fun screenWidthDp(): Int = resources.configuration.screenWidthDp

    private fun showSortMenu() {
        val popup = PopupMenu(this, sortButton)
        SortMode.values().forEach { popup.menu.add(it.label) }
        popup.setOnMenuItemClickListener { item ->
            sortMode = SortMode.values().first { it.label == item.title.toString() }
            loadPlaysFromDatabase()
            true
        }
        popup.show()
    }

    // Same send path as before: the QB gets the full play and every other watch its own assignment.
    private fun sendPlay(play: PlayMessage) {
        TabletServerManager.sendPlayToAssigned(play)
        SessionLogManager.addEntry("Play sent ${play.playName}")

        val message = if (TabletServerManager.getConnectedRoles().isEmpty()) {
            "Sent ${play.playName} (no assigned watches are connected)"
        } else {
            "Play sent ${play.playName}"
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun editPlay(play: PlayMessage) {
        val intent = Intent(this, CreatePlayActivity::class.java)
        intent.putExtra("edit_play_json", gson.toJson(play))
        startActivity(intent)
    }

    private fun showCardMenu(anchor: View, play: PlayMessage) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(MENU_ADD_TO_HURRY_UP)
        popup.menu.add(MENU_TRASH)
        popup.setOnMenuItemClickListener { item ->
            when (item.title.toString()) {
                MENU_ADD_TO_HURRY_UP -> showAddToHurryUpDialog(play)
                MENU_TRASH -> showMoveToTrashConfirmation(play)
            }
            true
        }
        popup.show()
    }

    // Lists the coach's own packages. The play is linked by name, never copied.
    private fun showAddToHurryUpDialog(play: PlayMessage) {
        thread {
            val packages = HurryUpRepository.getSummaries(applicationContext)

            runOnUiThread {
                val createLabel = "+ New Package"
                val options = packages.map { it.packageName } + createLabel

                AlertDialog.Builder(this@PlayLibraryActivity)
                    .setTitle("Add \"${play.playName}\" to:")
                    .setItems(options.toTypedArray()) { _, which ->
                        if (which == packages.size) {
                            HurryUpDialogs.startCreatePackageFlow(
                                this@PlayLibraryActivity,
                                preselectedPlays = setOf(play.playName)
                            ) { _, _ -> }
                        } else {
                            addPlayToPackage(play, packages[which].packageId, packages[which].packageName)
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    private fun addPlayToPackage(play: PlayMessage, packageId: Long, packageName: String) {
        thread {
            val result = HurryUpRepository.addPlays(applicationContext, packageId, listOf(play.playName))
            runOnUiThread {
                if (result.added.isEmpty()) {
                    Toast.makeText(this@PlayLibraryActivity, HurryUpRepository.DUPLICATE_PLAY_MESSAGE, Toast.LENGTH_LONG).show()
                } else {
                    SessionLogManager.addEntry("Play \"${play.playName}\" added to \"$packageName\"")
                    Toast.makeText(this@PlayLibraryActivity, "Added to \"$packageName\"", Toast.LENGTH_SHORT).show()
                }
            }
        }
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

    companion object {
        private const val MENU_ADD_TO_HURRY_UP = "Add to Hurry-Up Package"
        private const val MENU_TRASH = "Move to Trash"
        private const val EMPTY_LIBRARY = "No saved plays yet. Create a play and save it to see it here."
    }
}
