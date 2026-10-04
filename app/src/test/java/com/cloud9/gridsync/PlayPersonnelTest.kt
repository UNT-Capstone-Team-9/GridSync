package com.cloud9.gridsync

import com.cloud9.gridsync.network.PlayFormation
import com.cloud9.gridsync.network.PlayMessage
import com.cloud9.gridsync.network.PlayPersonnel
import com.cloud9.gridsync.network.PlayerPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayPersonnelTest {

    private fun playWith(players: List<PlayerPosition>) = PlayMessage(
        playName = "Test",
        assignments = emptyMap(),
        movements = emptyMap(),
        players = players
    )

    private fun swap(players: List<PlayerPosition>, benchId: String, activeId: String) =
        PlayFormation.swap(players, emptyMap(), benchId, activeId)!!.players

    @Test
    fun defaultFormationIsElevenPersonnel() {
        val info = PlayPersonnel.describe(playWith(PlayFormation.defaultPlayers()))
        assertEquals("11 Personnel", info.label)
        assertEquals("3 WR · 1 TE · 1 RB", info.composition)
    }

    @Test
    fun secondTightEndForReceiverIsTwelvePersonnel() {
        val players = swap(PlayFormation.defaultPlayers(), "te_2", "wr_3")
        assertEquals("12 Personnel", PlayPersonnel.describe(playWith(players)).label)
    }

    @Test
    fun fullbackForReceiverIsTwentyOnePersonnel() {
        val players = swap(PlayFormation.defaultPlayers(), "fb_1", "wr_3")
        val info = PlayPersonnel.describe(playWith(players))
        assertEquals("21 Personnel", info.label)
        assertEquals("2 WR · 1 TE · 1 RB · 1 FB", info.composition)
    }

    @Test
    fun sixthLinemanUsesCompositionInsteadOfNotation() {
        val players = swap(PlayFormation.defaultPlayers(), "ol_1", "wr_3")
        val info = PlayPersonnel.describe(playWith(players))
        assertNull(info.label)
        assertEquals("2 WR · 1 TE · 1 RB · 6 OL", info.groupName)
    }

    @Test
    fun olderPlayWithoutPlayersIsUnknown() {
        val info = PlayPersonnel.describe(playWith(emptyList()).copy(players = null))
        assertNull(info.label)
        assertEquals(PlayPersonnel.UNKNOWN, info.groupName)
    }
}
