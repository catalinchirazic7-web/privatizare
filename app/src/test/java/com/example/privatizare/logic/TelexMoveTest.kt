package com.example.privatizare.logic

import com.example.privatizare.data.model.Annex
import com.example.privatizare.data.model.CardEffect
import com.example.privatizare.data.model.GameState
import com.example.privatizare.data.model.LotoCard
import com.example.privatizare.data.model.LotoKind
import com.example.privatizare.data.model.PawnColor
import com.example.privatizare.data.model.Player
import com.example.privatizare.data.model.PropertyAction
import com.example.privatizare.data.model.TelexCard
import com.example.privatizare.data.model.Tile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

/** Cartonașele TELEX "înapoi 4" și "înainte 4": pionul stă pe TELEX, apoi se mută după confirmare. */
class TelexMoveTest {

    private val poli = PropertyAction("poli", "POLI", 1000, 200, listOf(Annex(1, 2200, 350)))

    // 10 = TELEX, 6 = POLI (4 înapoi din 10), 14 = POLI (4 înainte din 10), restul START
    private val board: List<Tile> = List(37) { i ->
        when (i) {
            10 -> Tile.Telex(i)
            6, 14 -> Tile.Property(i, "poli")
            else -> Tile.Start(i)
        }
    }

    private fun engine(steps: Int) = TurnEngine(
        board = board,
        actions = mapOf("poli" to poli),
        loto = listOf(LotoCard("l", LotoKind.NOTHING, 0, "nimic")),
        telex = listOf(TelexCard("m", "mută", listOf(CardEffect.Move(steps)))),
        random = Random(1),
    )

    private fun game(pos: Int = 7) = GameState(
        players = listOf(
            Player("A", "Ana", PawnColor.ROSU, 10_000, pos),
            Player("B", "Bogdan", PawnColor.ALBASTRU, 10_000),
        ),
        bankMoney = 0,
    )

    @Test
    fun inapoi4PionulStaPeTelexPanaLaConfirmare() {
        val e = engine(-4)
        val landed = e.playTurn(game(), 0, listOf(3))      // 7 + 3 = 10 (TELEX): doar marchează pachetul
        assertEquals("TELEX", landed.pendingDraw)
        assertNull(landed.card)
        val r = e.drawCard(landed.state, 0, "TELEX")
        assertEquals(10, r.state.players[0].position)
        assertEquals(-4, r.pendingMove)
        assertNotNull(r.card)
    }

    @Test
    fun inapoi4MutaPionulCuPatruCasuteSiRezolvaCasuta() {
        val e = engine(-4)
        val r = e.drawCard(e.playTurn(game(), 0, listOf(3)).state, 0, "TELEX")
        val moved = e.moveBy(r.state, 0, r.pendingMove!!)
        assertEquals(6, moved.state.players[0].position)                    // 10 - 4
        assertEquals("poli", moved.offer?.propertyId)                       // a ajuns pe o proprietate liberă: ofertă
    }

    @Test
    fun inainte4MutaPionulCuPatruCasute() {
        val e = engine(4)
        val r = e.drawCard(e.playTurn(game(), 0, listOf(3)).state, 0, "TELEX")
        assertEquals(4, r.pendingMove)
        val moved = e.moveBy(r.state, 0, 4)
        assertEquals(14, moved.state.players[0].position)                   // 10 + 4
        assertEquals("poli", moved.offer?.propertyId)
    }

    @Test
    fun mutareaInapoiNuDaBonusDeStart() {
        val e = engine(-4)
        val moved = e.moveBy(game(pos = 2), 0, -4)                          // 2 - 4 = 36 (se înfășoară)
        assertEquals(35, moved.state.players[0].position)
        assertEquals(10_000L, moved.state.players[0].money)                 // fără bonus
    }

    @Test
    fun mutareaInapoiPeProprietateaAltuiaDaChirieDatorata() {
        val e = engine(-4)
        val state = game().let { g ->
            g.copy(players = listOf(g.players[0], g.players[1].copy(ownedProperties = mapOf("poli" to 0))))
        }
        val r = e.drawCard(e.playTurn(state, 0, listOf(3)).state, 0, "TELEX")
        val moved = e.moveBy(r.state, 0, -4)
        assertNull(moved.offer)
        assertEquals(200L, moved.rentDue?.amount)                           // chiria titlului POLI
    }
}
