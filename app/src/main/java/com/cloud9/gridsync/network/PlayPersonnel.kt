package com.cloud9.gridsync.network

// Describes who is on the field for a saved play. Everything is derived from the active players,
// so nothing extra is stored on the play.
object PlayPersonnel {

    const val UNKNOWN = "Personnel not saved"

    private val backTypes = setOf("RB", "HB", "FB")
    private val linemanTypes = setOf("LT", "LG", "C", "RG", "RT", "OL")

    data class Info(
        // "11 Personnel" when standard notation fits, otherwise null.
        val label: String?,
        // Always the actual makeup, for example "3 WR · 1 TE · 1 RB".
        val composition: String
    ) {
        val groupName: String
            get() = label ?: composition
    }

    fun describe(play: PlayMessage): Info {
        val active = play.players?.filterNotNull()?.filter { it.isActive }.orEmpty()
        if (active.isEmpty()) return Info(null, UNKNOWN)

        val counts = active.groupingBy { it.positionType.trim().uppercase() }.eachCount()
        fun count(type: String) = counts[type] ?: 0

        val wr = count("WR")
        val te = count("TE")
        val backs = backTypes.sumOf { count(it) }
        val linemen = linemanTypes.sumOf { count(it) }
        val qb = count("QB")

        val parts = mutableListOf("$wr WR", "$te TE", "${count("RB")} RB")
        if (count("HB") > 0) parts.add("${count("HB")} HB")
        if (count("FB") > 0) parts.add("${count("FB")} FB")
        if (linemen != 5) parts.add("$linemen OL")
        if (qb != 1) parts.add("$qb QB")

        // Standard notation only describes one QB, five linemen and five eligible skill players.
        val isStandard = qb == 1 && linemen == 5 && wr + te + backs == 5 && backs <= 9 && te <= 9
        val label = if (isStandard) "$backs$te Personnel" else null

        return Info(label, parts.joinToString(" · "))
    }
}
