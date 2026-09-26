package com.cloud9.gridsync

import com.cloud9.gridsync.network.PlayFormation
import com.cloud9.gridsync.network.PlayMessage
import com.cloud9.gridsync.network.PointData
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayFormationTest {

    private val gson = Gson()
    private val route = listOf(PointData(0.8f, 0.66f), PointData(0.8f, 0.3f))

    @Test
    fun defaultFormationHasElevenUniqueActivePlayers() {
        val players = PlayFormation.defaultPlayers()

        assertEquals(11, players.count { it.isActive })
        assertEquals(players.size, players.map { it.id }.toSet().size)
        assertNull(PlayFormation.validate(players))
    }

    @Test
    fun swapKeepsElevenAndMovesOutgoingPlayerToBench() {
        val players = PlayFormation.defaultPlayers()
        val wr3 = players.first { it.id == "wr_3" }
        val movements = mapOf("wr_3" to route, "te_2" to route)

        val result = PlayFormation.swap(players, movements, benchId = "te_2", activeId = "wr_3")!!

        val te2 = result.players.first { it.id == "te_2" }
        val benchedWr3 = result.players.first { it.id == "wr_3" }

        assertEquals(11, result.players.count { it.isActive })
        assertTrue(te2.isActive)
        assertEquals(wr3.x, te2.x)
        assertEquals(wr3.y, te2.y)
        assertFalse(benchedWr3.isActive)
        assertNull(benchedWr3.x)
        assertFalse(result.movements.containsKey("te_2"))
        assertFalse(result.movements.containsKey("wr_3"))
    }

    @Test
    fun swapIsRejectedWhenBothPlayersAreActive() {
        val players = PlayFormation.defaultPlayers()
        assertNull(PlayFormation.swap(players, emptyMap(), benchId = "wr_1", activeId = "wr_3"))
    }

    @Test
    fun validateRejectsTwelveActivePlayers() {
        val players = PlayFormation.defaultPlayers().map {
            if (it.id == "te_2") it.copy(isActive = true, x = 0.7f, y = 0.6f) else it
        }
        assertNotNull(PlayFormation.validate(players))
    }

    @Test
    fun legacyPlayJsonLoadsWithDefaultFormationAndMatchedRoutes() {
        val legacyJson = """
            {"playName":"OLD","assignments":{"WR1":"Follow the drawn route for WR1"},
             "movements":{"WR1":[{"x":0.1,"y":0.6},{"x":0.1,"y":0.2}],"FB":[{"x":0.5,"y":0.8},{"x":0.4,"y":0.7}]},
             "imageResourceName":""}
        """.trimIndent()

        val play = gson.fromJson(legacyJson, PlayMessage::class.java)
        val loaded = PlayFormation.fromSavedPlay(play)

        assertNull(play.players)
        assertEquals(11, loaded.players.count { it.isActive })
        assertTrue(loaded.movements.containsKey("wr_1"))
        assertEquals(listOf("FB"), loaded.droppedRouteKeys)
    }

    @Test
    fun savedPlayRoundTripsThroughGson() {
        val players = PlayFormation.defaultPlayers().map {
            if (it.id == "rg_1") it.copy(assignmentType = "Pull Right", instruction = "Block the linebacker") else it
        }
        val play = PlayFormation.buildPlay("TRIPS", "Trips Right", "PASS", players, mapOf("wr_3" to route))

        val restored = PlayFormation.fromSavedPlay(gson.fromJson(gson.toJson(play), PlayMessage::class.java))
        val rg = restored.players.first { it.id == "rg_1" }

        assertEquals("Pull Right", rg.assignmentType)
        assertEquals("Block the linebacker", rg.instruction)
        assertEquals(route, restored.movements["wr_3"])
        assertEquals("Pull Right - Block the linebacker", play.assignments["RG"])
    }

    @Test
    fun quarterbackGetsFullPlayAndOthersGetOnlyTheirOwnRoute() {
        val swapped = PlayFormation.swap(
            PlayFormation.defaultPlayers(),
            emptyMap(),
            benchId = "te_2",
            activeId = "wr_3"
        )!!
        val movements = mapOf("te_2" to route, "wr_1" to route)
        val play = PlayFormation.buildPlay("P", "F", "PASS", swapped.players, movements)

        val qb = PlayFormation.buildWatchPayload(play, "QB")!!
        assertEquals(PlayFormation.DISPLAY_FULL_PLAY, qb.displayType)
        assertEquals(11, qb.players.size)
        assertEquals(setOf("TE2", "WR1"), qb.movements.keys)

        val te2 = PlayFormation.buildWatchPayload(play, "TE2")!!
        assertEquals(PlayFormation.DISPLAY_ROLE_SPECIFIC, te2.displayType)
        assertEquals(setOf("TE2"), te2.movements.keys)
        assertEquals(listOf("te_2"), te2.players.map { it.id })

        assertNull(PlayFormation.buildWatchPayload(play, "WR3"))
    }

    @Test
    fun legacyPlayStillSendsToEveryRole() {
        val play = PlayMessage(
            playName = "OLD",
            assignments = mapOf("WR1" to "Go"),
            movements = mapOf("WR1" to route, "TE" to route)
        )

        val wr1 = PlayFormation.buildWatchPayload(play, "wr1")!!
        assertEquals("Go", wr1.assignment)
        assertEquals(setOf("WR1"), wr1.movements.keys)

        val qb = PlayFormation.buildWatchPayload(play, "QB")!!
        assertEquals(2, qb.movements.size)
    }
}
