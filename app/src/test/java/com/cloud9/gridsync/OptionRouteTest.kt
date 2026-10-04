package com.cloud9.gridsync

import com.cloud9.gridsync.network.PlayFormation
import com.cloud9.gridsync.network.PlayMessage
import com.cloud9.gridsync.network.PointData
import com.cloud9.gridsync.network.RouteBranch
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// The "Option Route Test" play: WR1 runs a solid vertical with a dashed diagonal right option from
// halfway up. Checks the option survives save, reopen, swap and delivery to each watch.
class OptionRouteTest {

    private val gson = Gson()

    private val wr1Route = listOf(PointData(0.08f, 0.60f), PointData(0.08f, 0.40f), PointData(0.08f, 0.20f))
    private val wr1Option = RouteBranch(listOf(PointData(0.08f, 0.40f), PointData(0.14f, 0.34f), PointData(0.22f, 0.26f)))
    private val wr2Route = listOf(PointData(0.92f, 0.60f), PointData(0.92f, 0.20f))
    private val teRoute = listOf(PointData(0.68f, 0.60f), PointData(0.68f, 0.40f), PointData(0.85f, 0.28f))
    private val rbRoute = listOf(PointData(0.50f, 0.84f), PointData(0.30f, 0.78f), PointData(0.15f, 0.76f))

    private fun buildOptionRouteTest(): PlayMessage {
        val players = PlayFormation.defaultPlayers().map {
            when (it.id) {
                "rg_1" -> it.copy(assignmentType = "Pull Right")
                "wr_1" -> it.copy(
                    assignmentType = "Option Route",
                    instruction = "Continue vertical or break right based on coverage."
                )
                else -> it
            }
        }

        return PlayFormation.buildPlay(
            playName = "Option Route Test",
            formationName = "Spread",
            playType = PlayFormation.PLAY_TYPE_PASS,
            players = players,
            movements = mapOf("wr_1" to wr1Route, "wr_2" to wr2Route, "te_1" to teRoute, "rb_1" to rbRoute),
            options = mapOf("wr_1" to listOf(wr1Option))
        )
    }

    private fun saveAndReopen(play: PlayMessage): PlayMessage {
        val stored = gson.fromJson(gson.toJson(play), PlayMessage::class.java)
        val loaded = PlayFormation.fromSavedPlay(stored)
        val resaved = PlayFormation.buildPlay(
            stored.playName, stored.formationName.orEmpty(), stored.playType.orEmpty(),
            loaded.players, loaded.movements, options = loaded.options
        )
        return gson.fromJson(gson.toJson(resaved), PlayMessage::class.java)
    }

    // Decoded the same way WatchClientManager decodes the routeOptions field.
    private fun deliverOptions(play: PlayMessage, role: String): Map<String, List<RouteBranch>>? {
        val payload = PlayFormation.buildWatchPayload(play, role) ?: return null
        val type = object : TypeToken<Map<String, List<RouteBranch>>>() {}.type
        return gson.fromJson(gson.toJson(payload.options), type)
    }

    @Test
    fun optionSurvivesSaveAndReopen() {
        val play = saveAndReopen(buildOptionRouteTest())
        val loaded = PlayFormation.fromSavedPlay(play)

        assertEquals(wr1Route, loaded.movements["wr_1"])
        assertEquals(listOf(wr1Option), loaded.options["wr_1"])
        assertEquals(setOf("wr_1"), loaded.options.keys)

        val wr1 = loaded.players.first { it.id == "wr_1" }
        assertEquals("Option Route", wr1.assignmentType)
        assertEquals("Continue vertical or break right based on coverage.", wr1.instruction)
    }

    @Test
    fun onlyWr1AndQbSeeWr1Option() {
        val play = saveAndReopen(buildOptionRouteTest())

        assertEquals(mapOf("WR1" to listOf(wr1Option)), deliverOptions(play, "WR1"))
        listOf("WR2", "TE", "RB", "RG").forEach { role ->
            assertTrue("$role must not get WR1's option", deliverOptions(play, role)!!.isEmpty())
        }
        assertEquals(mapOf("WR1" to listOf(wr1Option)), deliverOptions(play, "QB"))

        assertEquals("Pull Right", PlayFormation.buildWatchPayload(play, "RG")!!.assignment)
    }

    @Test
    fun hurryUpDeliversTheSameOptions() {
        val stored = gson.toJson(saveAndReopen(buildOptionRouteTest()))
        val fromLibrary = gson.fromJson(stored, PlayMessage::class.java)
        val fromHurryUp = gson.fromJson(stored, PlayMessage::class.java)

        listOf("QB", "WR1", "WR2", "TE", "RB", "RG").forEach { role ->
            assertEquals(deliverOptions(fromLibrary, role), deliverOptions(fromHurryUp, role))
        }
    }

    @Test
    fun playsSavedBeforeOptionsStillLoad() {
        val json = """
            {"playName":"Old","assignments":{"WR1":"Go"},
             "movements":{"WR1":[{"x":0.1,"y":0.6},{"x":0.1,"y":0.2}]}}
        """.trimIndent()
        val play = gson.fromJson(json, PlayMessage::class.java)

        assertNull(play.routeOptions)
        val loaded = PlayFormation.fromSavedPlay(play)
        assertTrue(loaded.movements.containsKey("wr_1"))
        assertTrue(loaded.options.isEmpty())
        assertTrue(PlayFormation.buildWatchPayload(play, "WR1")!!.options.isEmpty())
    }

    @Test
    fun swapDoesNotHandTheOptionToTheIncomingPlayer() {
        val play = buildOptionRouteTest()
        val result = PlayFormation.swap(
            play.players!!, play.movements, benchId = "wr_4", activeId = "wr_1",
            options = play.routeOptions!!
        )!!

        assertTrue(result.options.isEmpty())
        assertTrue(result.movements.keys.none { it == "wr_1" || it == "wr_4" })
    }

    @Test
    fun eachWatchGetsTheSameRouteColourAsThePlayLibrary() {
        val play = saveAndReopen(buildOptionRouteTest())
        val library = PlayFormation.routeColors(play.players!!, play.movements)
        val labelById = play.players!!.associate { it.id to it.displayLabel }

        // Left to right: WR1, TE, RB, WR2 each get their own colour.
        assertEquals(4, library.values.toSet().size)

        library.forEach { (id, color) ->
            val label = labelById.getValue(id)
            assertEquals(mapOf(label to color), PlayFormation.buildWatchPayload(play, label)!!.routeColors)
        }

        assertEquals(library.mapKeys { labelById.getValue(it.key) }, PlayFormation.buildWatchPayload(play, "QB")!!.routeColors)
        assertTrue(PlayFormation.buildWatchPayload(play, "RG")!!.routeColors.isEmpty())
    }

    @Test
    fun playWithoutOptionsSavesNoOptionsField() {
        val play = PlayFormation.buildPlay(
            "P", "F", "PASS", PlayFormation.defaultPlayers(), mapOf("wr_1" to wr1Route)
        )
        assertNull(play.routeOptions)
    }
}
