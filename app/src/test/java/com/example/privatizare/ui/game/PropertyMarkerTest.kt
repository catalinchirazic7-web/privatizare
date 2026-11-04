package com.example.privatizare.ui.game

import com.example.privatizare.data.model.PawnColor
import com.example.privatizare.data.model.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PropertyMarkerTest {

    private val tiles = mapOf("poli" to 2, "dura" to 9)

    private fun player(id: String, color: PawnColor, owned: Map<String, Int>) =
        Player(id, id, color, money = 0, ownedProperties = owned)

    @Test
    fun caselieLibereNuAuMarcaj() {
        val markers = propertyMarkers(listOf(player("A", PawnColor.ROSU, emptyMap())), tiles)
        assertTrue(markers.isEmpty())
    }

    @Test
    fun ownerulUneiProprietatiAreMarcajInCuloareaSa() {
        val markers = propertyMarkers(listOf(player("A", PawnColor.ROSU, mapOf("poli" to 1))), tiles)
        assertEquals(1, markers.size)
        assertEquals(2, markers[0].tileIndex)
        assertEquals(PawnColor.ROSU, markers[0].color)
    }

    @Test
    fun marcajulNuDezvaluieAnexele() {
        // Confidențialitate: titlu simplu și titlu cu anexe dau același marcaj public
        val titleOnly = propertyMarkers(listOf(player("A", PawnColor.ROSU, mapOf("poli" to 0))), tiles)
        val withAnnexes = propertyMarkers(listOf(player("A", PawnColor.ROSU, mapOf("poli" to 2))), tiles)
        assertEquals(titleOnly, withAnnexes)
    }

    @Test
    fun doiJucatoriPeProprietatiDiferite() {
        val markers = propertyMarkers(
            listOf(
                player("A", PawnColor.ROSU, mapOf("poli" to 0)),
                player("B", PawnColor.ALBASTRU, mapOf("dura" to 2)),
            ),
            tiles,
        )
        assertEquals(setOf(2, 9), markers.map { it.tileIndex }.toSet())
        assertEquals(PawnColor.ALBASTRU, markers.first { it.tileIndex == 9 }.color)
    }

    @Test
    fun lichidareaScoateMarcajulCaTitluluiRevineLaBanca() {
        val before = listOf(player("A", PawnColor.ROSU, mapOf("poli" to 0)))
        assertEquals(1, propertyMarkers(before, tiles).size)
        val after = listOf(player("A", PawnColor.ROSU, emptyMap()))
        assertTrue(propertyMarkers(after, tiles).isEmpty())
    }

    @Test
    fun vanzareaUneiAnexeNuSchimbaMarcajulPublic() {
        val before = propertyMarkers(listOf(player("A", PawnColor.VERDE, mapOf("poli" to 2))), tiles)
        val after = propertyMarkers(listOf(player("A", PawnColor.VERDE, mapOf("poli" to 1))), tiles)
        assertEquals(before, after)
    }
}
