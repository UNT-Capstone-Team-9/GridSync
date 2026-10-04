package com.cloud9.gridsync.network

// Formation rules shared by the play designer, the play library and the tablet server.
// The one permanent rule is that a play always has exactly 11 active players.
object PlayFormation {

    const val ACTIVE_PLAYER_COUNT = 11

    const val PLAY_TYPE_PASS = "PASS"
    const val PLAY_TYPE_RUN = "RUN"

    const val DISPLAY_FULL_PLAY = "full_play"
    const val DISPLAY_ROLE_SPECIFIC = "role_specific"

    const val ASSIGNMENT_CUSTOM = "Custom"

    // Route colours used by the Play Library cards and every watch, the way paper playbooks
    // colour receivers. Kept here so the tablet and the watches always agree on a player's colour.
    val routePalette = listOf(
        "#FFD54F", "#4FC3F7", "#F06292", "#81C784", "#FF8A65", "#BA68C8", "#4DD0E1", "#E6EE9C"
    )

    private val linemanTypes = setOf("LT", "LG", "C", "RG", "RT", "OL")

    // The first option of each list is a placeholder that is stored as an empty assignmentType.
    val linemanAssignments = listOf(
        "Default Block",
        "Pass Protect",
        "Run Block",
        "Block Left",
        "Block Right",
        "Pull Left",
        "Pull Right",
        "Double Team",
        ASSIGNMENT_CUSTOM
    )

    val skillAssignments = listOf(
        "None",
        "Route",
        "Option Route",
        "Block",
        "Pass Protect",
        "Ball Carrier",
        ASSIGNMENT_CUSTOM
    )

    val quarterbackAssignments = listOf(
        "Full Play",
        "Pass",
        "Handoff",
        "Keep",
        ASSIGNMENT_CUSTOM
    )

    // options are each player's dashed option branches, keyed the same way as movements.
    data class SwapResult(
        val players: List<PlayerPosition>,
        val movements: Map<String, List<PointData>>,
        val options: Map<String, List<RouteBranch>> = emptyMap()
    )

    data class LoadedFormation(
        val players: List<PlayerPosition>,
        val movements: Map<String, List<PointData>>,
        val droppedRouteKeys: List<String>,
        val options: Map<String, List<RouteBranch>> = emptyMap()
    )

    data class WatchPayload(
        val displayType: String,
        val assignment: String,
        val movements: Map<String, List<PointData>>,
        val players: List<PlayerPosition>,
        val options: Map<String, List<RouteBranch>> = emptyMap(),
        // Route colour per role label, matching the Play Library card.
        val routeColors: Map<String, String> = emptyMap()
    )

    // Each active player with a route gets the next palette colour, left to right, so colours
    // stay stable for the same formation. Returns colours keyed by player id.
    fun routeColors(
        players: List<PlayerPosition>,
        movements: Map<String, List<PointData>>
    ): Map<String, String> {
        val colors = linkedMapOf<String, String>()
        players
            .filter { it.isActive && it.x != null && it.y != null }
            .sortedBy { it.x }
            .forEach { player ->
                if ((movements[player.id]?.size ?: 0) < 2) return@forEach
                colors[player.id] = routePalette[colors.size % routePalette.size]
            }
        return colors
    }

    // Line of scrimmage sits at y = 0.60 and the offense faces the top of the field.
    fun defaultPlayers(): List<PlayerPosition> = listOf(
        active("wr_1", "WR", "WR1", 0.08f, 0.60f),
        active("lt_1", "LT", "LT", 0.38f, 0.60f),
        active("lg_1", "LG", "LG", 0.44f, 0.60f),
        active("c_1", "C", "C", 0.50f, 0.60f),
        active("rg_1", "RG", "RG", 0.56f, 0.60f),
        active("rt_1", "RT", "RT", 0.62f, 0.60f),
        active("te_1", "TE", "TE", 0.68f, 0.60f),
        active("wr_3", "WR", "WR3", 0.80f, 0.66f),
        active("wr_2", "WR", "WR2", 0.92f, 0.60f),
        active("qb_1", "QB", "QB", 0.50f, 0.72f),
        active("rb_1", "RB", "RB", 0.50f, 0.84f),
        bench("rb_2", "RB", "RB2"),
        bench("te_2", "TE", "TE2"),
        bench("wr_4", "WR", "WR4"),
        bench("fb_1", "FB", "FB"),
        bench("hb_1", "HB", "HB"),
        bench("ol_1", "OL", "OL")
    )

    val allRoleLabels: List<String>
        get() = defaultPlayers().map { it.displayLabel }

    fun isLineman(player: PlayerPosition): Boolean {
        return player.positionType.trim().uppercase() in linemanTypes
    }

    fun isQuarterback(player: PlayerPosition): Boolean {
        return player.positionType.trim().equals("QB", ignoreCase = true)
    }

    fun assignmentOptions(player: PlayerPosition): List<String> {
        return when {
            isQuarterback(player) -> quarterbackAssignments
            isLineman(player) -> linemanAssignments
            else -> skillAssignments
        }
    }

    // A substitution is always a swap, so the active count never changes. The incoming player
    // takes the outgoing player's spot but none of their route, options, assignment or instruction.
    fun swap(
        players: List<PlayerPosition>,
        movements: Map<String, List<PointData>>,
        benchId: String,
        activeId: String,
        options: Map<String, List<RouteBranch>> = emptyMap()
    ): SwapResult? {
        val benchIndex = players.indexOfFirst { it.id == benchId && !it.isActive }
        val activeIndex = players.indexOfFirst { it.id == activeId && it.isActive }
        if (benchIndex < 0 || activeIndex < 0) return null

        val outgoing = players[activeIndex]
        val incoming = players[benchIndex]

        val updated = players.toMutableList()
        updated[activeIndex] = incoming.copy(
            isActive = true,
            x = outgoing.x,
            y = outgoing.y,
            assignmentType = "",
            instruction = ""
        )
        updated[benchIndex] = outgoing.copy(
            isActive = false,
            x = null,
            y = null,
            assignmentType = "",
            instruction = ""
        )

        return SwapResult(
            players = updated,
            movements = movements - setOf(benchId, activeId),
            options = options - setOf(benchId, activeId)
        )
    }

    // Returns a message describing the first problem, or null when the formation can be saved.
    fun validate(players: List<PlayerPosition>): String? {
        val activePlayers = players.filter { it.isActive }

        if (activePlayers.size != ACTIVE_PLAYER_COUNT) {
            return "A play needs exactly $ACTIVE_PLAYER_COUNT active players (found ${activePlayers.size})"
        }

        if (players.any { it.id.isBlank() }) {
            return "Every player needs an id"
        }

        if (players.map { it.id }.toSet().size != players.size) {
            return "Player ids must be unique"
        }

        if (players.map { it.displayLabel.trim().uppercase() }.toSet().size != players.size) {
            return "Player labels must be unique"
        }

        val badPlayer = activePlayers.firstOrNull { player ->
            val x = player.x
            val y = player.y
            x == null || y == null || x.isNaN() || y.isNaN() || x !in 0f..1f || y !in 0f..1f
        }

        if (badPlayer != null) {
            return "${badPlayer.displayLabel} has an invalid field position"
        }

        return null
    }

    // Restores a saved play into editor state. Older plays have no players and key their
    // routes by role label, so they get the default formation and their routes are matched
    // to the player with that label.
    fun fromSavedPlay(play: PlayMessage): LoadedFormation {
        val savedPlayers = play.players
            ?.filterNotNull()
            ?.map { it.copy(assignmentType = it.assignmentType.orEmpty(), instruction = it.instruction.orEmpty()) }

        val players = if (savedPlayers != null && validate(savedPlayers) == null) {
            savedPlayers
        } else {
            defaultPlayers()
        }

        val movements = mutableMapOf<String, List<PointData>>()
        val dropped = mutableListOf<String>()

        fun findPlayer(rawKey: String): PlayerPosition? {
            val key = rawKey.trim()
            return players.firstOrNull { it.id == key }
                ?: players.firstOrNull { it.displayLabel.equals(key, ignoreCase = true) }
        }

        play.movements.orEmpty().forEach { (rawKey, points) ->
            val player = findPlayer(rawKey)
            val cleanPoints = cleanRoute(points)

            if (player != null && player.isActive && cleanPoints.size >= 2) {
                movements[player.id] = cleanPoints
            } else if (cleanPoints.size >= 2) {
                dropped.add(rawKey.trim())
            }
        }

        // An option only means something next to its main route, so options without one are dropped.
        val options = mutableMapOf<String, List<RouteBranch>>()
        play.routeOptions.orEmpty().forEach { (rawKey, branches) ->
            val player = findPlayer(rawKey) ?: return@forEach
            if (!movements.containsKey(player.id)) return@forEach

            val cleanBranches = cleanBranches(branches)
            if (cleanBranches.isNotEmpty()) options[player.id] = cleanBranches
        }

        return LoadedFormation(players, movements, dropped, options)
    }

    // Gson can leave nulls inside lists from hand edited or older JSON, so they are filtered here.
    private fun cleanRoute(points: List<PointData>?): List<PointData> {
        return points.orEmpty().filterNotNull().map {
            PointData(it.x.coerceIn(0f, 1f), it.y.coerceIn(0f, 1f))
        }
    }

    private fun cleanBranches(branches: List<RouteBranch>?): List<RouteBranch> {
        return branches.orEmpty().filterNotNull()
            .map { RouteBranch(cleanRoute(it.points)) }
            .filter { it.points.size >= 2 }
    }

    fun buildAssignmentText(
        player: PlayerPosition,
        hasRoute: Boolean,
        playType: String?
    ): String {
        val type = player.assignmentType.orEmpty().trim()
        val instruction = player.instruction.orEmpty().trim()

        val base = when {
            type.isNotBlank() && type != ASSIGNMENT_CUSTOM -> type
            type == ASSIGNMENT_CUSTOM -> ""
            isQuarterback(player) -> "Full play"
            isLineman(player) -> if (playType == PLAY_TYPE_RUN) "Run Block" else "Pass Protect"
            hasRoute -> "Run your route"
            else -> ""
        }

        val text = listOf(base, instruction).filter { it.isNotBlank() }.joinToString(" - ")
        return text.ifBlank { "No assignment" }
    }

    fun buildPlay(
        playName: String,
        formationName: String,
        playType: String,
        players: List<PlayerPosition>,
        movements: Map<String, List<PointData>>,
        imageResourceName: String = "",
        options: Map<String, List<RouteBranch>> = emptyMap()
    ): PlayMessage {
        val activePlayers = players.filter { it.isActive }
        val activeIds = activePlayers.map { it.id }.toSet()

        val savedMovements = movements.filter { (id, points) ->
            id in activeIds && points.size >= 2
        }

        val savedOptions = options
            .filterKeys { it in savedMovements }
            .mapValues { cleanBranches(it.value) }
            .filterValues { it.isNotEmpty() }

        val assignments = activePlayers.associate { player ->
            player.displayLabel to buildAssignmentText(
                player,
                savedMovements.containsKey(player.id),
                playType
            )
        }

        return PlayMessage(
            playName = playName,
            assignments = assignments,
            movements = savedMovements,
            imageResourceName = imageResourceName,
            formationName = formationName,
            playType = playType,
            players = players,
            routeOptions = savedOptions.takeIf { it.isNotEmpty() }
        )
    }

    // What one watch receives for a play. Returns null when the role is not on the field for
    // this play (for example WR3 after being swapped out), so that watch gets nothing.
    fun buildWatchPayload(play: PlayMessage, role: String): WatchPayload? {
        val cleanRole = role.trim()
        val isQbRole = cleanRole.equals("QB", ignoreCase = true)
        val players = play.players?.filterNotNull()
        val movements = play.movements.orEmpty()
        val options = play.routeOptions.orEmpty()

        if (players.isNullOrEmpty()) {
            val assignment = play.assignments.orEmpty().entries.firstOrNull {
                it.key.trim().equals(cleanRole, ignoreCase = true)
            }?.value ?: "Follow your assigned route"

            val legacyMovements = if (isQbRole) {
                movements
            } else {
                movements.filterKeys { it.trim().equals(cleanRole, ignoreCase = true) }
            }

            return WatchPayload(
                displayType = if (isQbRole) DISPLAY_FULL_PLAY else DISPLAY_ROLE_SPECIFIC,
                assignment = assignment,
                movements = legacyMovements,
                players = emptyList()
            )
        }

        val activePlayers = players.filter { it.isActive }
        val target = activePlayers.firstOrNull {
            it.displayLabel.trim().equals(cleanRole, ignoreCase = true)
        } ?: return null

        val routesByLabel = activePlayers.mapNotNull { player ->
            movements[player.id]?.takeIf { it.size >= 2 }?.let { player.displayLabel to it }
        }.toMap()

        // Colours are worked out from the whole play, so a single receiver still gets the colour
        // the Play Library shows for them.
        val colorsByLabel = routeColors(activePlayers, movements).mapNotNull { (id, color) ->
            activePlayers.firstOrNull { it.id == id }?.let { it.displayLabel to color }
        }.toMap()

        // Options travel with their main route, keyed by the same watch role label.
        val optionsByLabel = activePlayers.mapNotNull { player ->
            if (!routesByLabel.containsKey(player.displayLabel)) return@mapNotNull null
            cleanBranches(options[player.id]).takeIf { it.isNotEmpty() }?.let { player.displayLabel to it }
        }.toMap()

        val assignment = play.assignments.orEmpty()[target.displayLabel]
            ?: buildAssignmentText(target, movements.containsKey(target.id), play.playType)

        return if (isQuarterback(target)) {
            val header = listOfNotNull(
                play.formationName?.takeIf { it.isNotBlank() },
                play.playType?.takeIf { it.isNotBlank() }
            ).joinToString(" | ")

            WatchPayload(
                displayType = DISPLAY_FULL_PLAY,
                assignment = listOf(header, assignment).filter { it.isNotBlank() }.joinToString("\n"),
                movements = routesByLabel,
                players = activePlayers,
                options = optionsByLabel,
                routeColors = colorsByLabel
            )
        } else {
            WatchPayload(
                displayType = DISPLAY_ROLE_SPECIFIC,
                assignment = assignment,
                movements = routesByLabel.filterKeys { it == target.displayLabel },
                players = listOf(target),
                options = optionsByLabel.filterKeys { it == target.displayLabel },
                routeColors = colorsByLabel.filterKeys { it == target.displayLabel }
            )
        }
    }

    private fun active(id: String, type: String, label: String, x: Float, y: Float) =
        PlayerPosition(id, type, label, isActive = true, x = x, y = y)

    private fun bench(id: String, type: String, label: String) =
        PlayerPosition(id, type, label, isActive = false, x = null, y = null)
}
