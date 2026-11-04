package com.example.privatizare.data

import com.example.privatizare.data.model.CardEffect
import com.example.privatizare.data.model.LotoKind
import com.example.privatizare.data.model.Tile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Citește fișierele reale din assets (rulează din directorul app/). */
class GameRepositoryTest {

    private fun asset(name: String): String =
        File("src/main/assets/$name").readText(Charsets.UTF_8)

    @Test
    fun tablaAre37Casute() {
        val board = GameRepository.parseBoard(asset("board.json"))
        assertEquals(37, board.size)
        assertEquals(0, board[0].index)
        assertEquals(27, board.count { it is Tile.Property })
        assertEquals(2, board.count { it is Tile.Loto })
        assertEquals(3, board.count { it is Tile.Telex })
        assertTrue(board[10] is Tile.SalaConsfatuiri)
        assertTrue(board[17] is Tile.RechematUrgent)
        assertTrue(board[29] is Tile.CasaDeOdihna)
        assertTrue(board[36] is Tile.BankDividend)
    }

    @Test
    fun pozitiileSuntRelativeSi37() {
        val pos = GameRepository.parsePositions(asset("board.json"))
        assertEquals(37, pos.size)
        assertTrue(pos.all { (x, y) -> x in 0f..1f && y in 0f..1f })
        // Tabla e pe lat: prima căsuță (CONTINENTAL) e în stânga-jos, RECHEMAT URGENT în dreapta-sus
        assertTrue(pos[0].first < 0.3f && pos[0].second > 0.8f)
        assertTrue(pos[17].first > 0.85f && pos[17].second < 0.2f)
    }

    @Test
    fun casetelePachetelorSuntInTabla() {
        val decks = GameRepository.parseDecks(asset("board.json"))
        assertEquals(setOf("loto", "telex"), decks.keys)
        for (d in decks.values) {
            assertTrue(d.x in 0f..1f && d.y in 0f..1f && d.x + d.w <= 1f && d.y + d.h <= 1f && d.w > 0f && d.h > 0f)
        }
    }

    @Test
    fun actiuniAu27ProprietatiSi71Anexe() {
        val actions = GameRepository.parseActions(asset("actions.json"))
        assertEquals(27, actions.size)
        assertEquals(71, actions.values.sumOf { it.annexes.size })
        val poli = actions.getValue("poli")
        assertEquals(1000L, poli.titlePrice)
        assertEquals(200L, poli.titleTax)
        assertEquals(listOf(1, 2, 3), poli.annexes.map { it.index })
        assertEquals(350L, poli.annexes[0].profit)
    }

    @Test
    fun cartonaseleAuFormaCorecta() {
        val cards = org.json.JSONObject(asset("cards.json"))
        val loto = GameRepository.parseLoto(cards.getJSONArray("loto"))
        val telex = GameRepository.parseTelex(cards.getJSONArray("telex"))
        assertEquals(36, loto.size)
        assertEquals(60, telex.size)
        assertEquals(16, loto.count { it.kind == LotoKind.WIN })
        assertEquals(10, loto.count { it.kind == LotoKind.LOSE })
        assertEquals(10, loto.count { it.kind == LotoKind.NOTHING })
        // Efectele de plată către proprietar au proprietate validă
        val owners = telex.flatMap { it.effects }.filterIsInstance<CardEffect.PayOwnerOrBank>()
        assertEquals(34, owners.size)
        assertTrue(owners.all { it.propertyId.isNotBlank() })
    }
}
