package com.cloud9.gridsync

import com.cloud9.gridsync.network.PlayFormation
import com.cloud9.gridsync.network.PlayMessage
import com.cloud9.gridsync.network.PlayerPosition
import com.cloud9.gridsync.network.PointData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// End to end check of the "Trips Right Test" play: routes drawn per player on the tablet, saved to
// Room as JSON, reopened in the editor, saved again, then turned into each watch's payload and
// decoded the same way WatchClientManager decodes it.
class WatchDeliveryTest {

    private val gson = Gson()

    // Hand drawn routes, starting at each player's spot in the default formation.
    private val wr1Route = listOf(
        PointData(0.08f, 0.60f), PointData(0.08f, 0.45f), PointData(0.085f, 0.32f), PointData(0.16f, 0.24f), PointData(0.26f, 0.20f)
    )
    private val wr2Route = listOf(
        PointData(0.92f, 0.60f), PointData(0.92f, 0.45f), PointData(0.915f, 0.32f), PointData(0.96f, 0.26f), PointData(0.99f, 0.24f)
    )
    private val teRoute = listOf(
        PointData(0.68f, 0.60f), PointData(0.68f, 0.42f), PointData(0.76f, 0.30f), PointData(0.86f, 0.22f)
    )
    private val rbRoute = listOf(
        PointData(0.50f, 0.84f), PointData(0.40f, 0.80f), PointData(0.25f, 0.70f), PointData(0.15f, 0.68f)
    )

    private data class WatchView(
        val displayType: String,
        val assignment: String,
        val movements: Map<String, List<PointData>>,
        val players: List<PlayerPosition>
    )

    private fun buildTripsRightTest(players: List<PlayerPosition> = PlayFormation.defaultPlayers()): PlayMessage {
        val withAssignments = players.map {
            when (it.id) {
                "rg_1" -> it.copy(assignmentType = "Pull Right", instruction = "Block the edge defender")
                "lt_1" -> it.copy(assignmentType = "Pass Protect")
                else -> it
            }
        }

        // CoachDrawingView keys every route by the player's unique id.
        val movements = mapOf(
            "wr_1" to wr1Route,
            "wr_2" to wr2Route,
            "te_1" to teRoute,
            "rb_1" to rbRoute
        ).filterKeys { id -> withAssignments.any { it.id == id && it.isActive } }

        return PlayFormation.buildPlay(
            playName = "Trips Right Test",
            formationName = "Trips Right",
            playType = PlayFormation.PLAY_TYPE_PASS,
            players = withAssignments,
            movements = movements
        )
    }

    // Save to Room, close, reopen in the editor and save again without changes.
    private fun saveAndReopen(play: PlayMessage): PlayMessage {
        val stored = gson.fromJson(gson.toJson(play), PlayMessage::class.java)
        val loaded = PlayFormation.fromSavedPlay(stored)
        assertTrue(loaded.droppedRouteKeys.isEmpty())

        val resaved = PlayFormation.buildPlay(
            playName = stored.playName,
            formationName = stored.formationName.orEmpty(),
            playType = stored.playType.orEmpty(),
            players = loaded.players,
            movements = loaded.movements
        )
        return gson.fromJson(gson.toJson(resaved), PlayMessage::class.java)
    }

    // What sendPlayToAssigned writes for one role, decoded as WatchClientManager decodes it.
    private fun deliver(play: PlayMessage, role: String): WatchView? {
        val payload = PlayFormation.buildWatchPayload(play, role) ?: return null

        val movementsJson = gson.toJson(payload.movements)
        val playersJson = gson.toJson(payload.players)

        val movementType = object : TypeToken<Map<String, List<PointData>>>() {}.type
        val playersType = object : TypeToken<List<PlayerPosition>>() {}.type

        return WatchView(
            displayType = payload.displayType,
            assignment = payload.assignment,
            movements = gson.fromJson(movementsJson, movementType),
            players = gson.fromJson(playersJson, playersType)
        )
    }

    @Test
    fun eachReceiverWatchGetsOnlyItsOwnExactRoute() {
        val play = saveAndReopen(buildTripsRightTest())

        listOf("WR1" to wr1Route, "WR2" to wr2Route, "TE" to teRoute, "RB" to rbRoute).forEach { (role, route) ->
            val view = deliver(play, role)!!

            assertEquals(PlayFormation.DISPLAY_ROLE_SPECIFIC, view.displayType)
            assertEquals("$role should only get its own route", setOf(role), view.movements.keys)
            assertEquals("$role route changed on the way to the watch", route, view.movements[role])
            assertEquals(listOf(role), view.players.map { it.displayLabel })
        }
    }

    @Test
    fun wr1AndWr2RoutesStayDifferent() {
        val play = saveAndReopen(buildTripsRightTest())

        val wr1 = deliver(play, "WR1")!!.movements.getValue("WR1")
        val wr2 = deliver(play, "WR2")!!.movements.getValue("WR2")

        // WR1 breaks inside (toward the middle), WR2 breaks outside (toward the sideline).
        assertTrue(wr1.last().x > wr1.first().x)
        assertTrue(wr2.last().x > wr2.first().x && wr2.first().x > 0.5f)
        assertTrue(wr1 != wr2)
    }

    @Test
    fun linemenGetTheirOwnBlockingAssignment() {
        val play = saveAndReopen(buildTripsRightTest())

        val rg = deliver(play, "RG")!!
        assertEquals(PlayFormation.DISPLAY_ROLE_SPECIFIC, rg.displayType)
        assertEquals("Pull Right - Block the edge defender", rg.assignment)
        assertTrue(rg.movements.isEmpty())
        assertEquals(listOf("RG"), rg.players.map { it.displayLabel })

        assertEquals("Pass Protect", deliver(play, "LT")!!.assignment)
    }

    @Test
    fun quarterbackGetsTheCompletePlay() {
        val play = saveAndReopen(buildTripsRightTest())
        val qb = deliver(play, "QB")!!

        assertEquals(PlayFormation.DISPLAY_FULL_PLAY, qb.displayType)
        assertEquals(11, qb.players.size)
        assertTrue(qb.players.all { it.isActive && it.x != null && it.y != null })
        assertEquals(
            mapOf("WR1" to wr1Route, "WR2" to wr2Route, "TE" to teRoute, "RB" to rbRoute),
            qb.movements
        )
        assertTrue(qb.assignment.startsWith("Trips Right | PASS"))

        // The QB's formation carries every player's own assignment as well.
        val rg = qb.players.first { it.displayLabel == "RG" }
        assertEquals("Pull Right", rg.assignmentType)
    }

    @Test
    fun substitutedInPlayerReceivesAndBenchedPlayerGetsNothing() {
        val swapped = PlayFormation.swap(
            PlayFormation.defaultPlayers(), emptyMap(), benchId = "te_2", activeId = "wr_3"
        )!!.players
        val play = buildTripsRightTest(swapped).let { base ->
            // Coach draws a route for TE2 after the swap.
            val te2Route = listOf(PointData(0.80f, 0.66f), PointData(0.80f, 0.40f), PointData(0.70f, 0.30f))
            PlayFormation.buildPlay(
                base.playName, base.formationName!!, base.playType!!, base.players!!,
                base.movements + ("te_2" to te2Route)
            )
        }.let { saveAndReopen(it) }

        assertNull(deliver(play, "WR3"))

        val te2 = deliver(play, "TE2")!!
        assertEquals(setOf("TE2"), te2.movements.keys)

        val qb = deliver(play, "QB")!!
        assertTrue(qb.players.any { it.displayLabel == "TE2" })
        assertTrue(qb.players.none { it.displayLabel == "WR3" })
    }

    @Test
    fun hurryUpSendsTheSamePayloadAsThePlayLibrary() {
        // Both screens load the play from the same Room row and call sendPlayToAssigned.
        val stored = gson.toJson(saveAndReopen(buildTripsRightTest()))
        val fromLibrary = gson.fromJson(stored, PlayMessage::class.java)
        val fromHurryUp = gson.fromJson(stored, PlayMessage::class.java)

        listOf("QB", "WR1", "WR2", "TE", "RB", "RG", "LT").forEach { role ->
            assertNotNull(deliver(fromLibrary, role))
            assertEquals(deliver(fromLibrary, role), deliver(fromHurryUp, role))
        }
    }
}
