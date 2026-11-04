package com.example.privatizare.logic

import com.example.privatizare.data.model.Annex
import com.example.privatizare.data.model.GameState
import com.example.privatizare.data.model.LotoCard
import com.example.privatizare.data.model.LotoKind
import com.example.privatizare.data.model.PawnColor
import com.example.privatizare.data.model.Player
import com.example.privatizare.data.model.PropertyAction
import com.example.privatizare.data.model.TelexCard
import com.example.privatizare.data.model.Tile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BotAITest {

    private val poli = PropertyAction(
        id = "poli", name = "POLI", titlePrice = 1000, titleTax = 200,
        annexes = listOf(Annex(1, 2200, 350), Annex(2, 3000, 800), Annex(3, 5500, 1250)),
    )
    private val dura = PropertyAction(
        id = "dura", name = "DURA", titlePrice = 2500, titleTax = 350,
        annexes = listOf(Annex(1, 2000, 600)),
    )
    private val actions = mapOf("poli" to poli, "dura" to dura)

    // 37 de căsuțe; POLI pe 2, DURA pe 9, restul START (fără efect)
    private val board: List<Tile> = List(37) { i ->
        when (i) {
            2 -> Tile.Property(i, "poli")
            9 -> Tile.Property(i, "dura")
            else -> Tile.Start(i)
        }
    }

    private fun player(id: String, money: Long, position: Int = 0, owned: Map<String, Int> = emptyMap()) =
        Player(id, id, PawnColor.VERDE, money, position = position, ownedProperties = owned, isBot = id == "bot")

    private fun state(vararg players: Player, stats: Map<String, com.example.privatizare.data.model.PropertyStats> = emptyMap()) =
        GameState(players = players.toList(), bankMoney = 500_000, stats = stats)

    @Test
    fun titluLiberSeIncercaDacaAreBani() {
        val s = state(player("bot", 1000), player("ana", 0))
        assertTrue(BotAI.decideBuy(s, "bot", board, actions, "poli", BuyOption.Title(1000)))
        assertFalse(BotAI.decideBuy(s.copy(players = listOf(player("bot", 999), player("ana", 0))), "bot", board, actions, "poli", BuyOption.Title(1000)))
    }

    @Test
    fun anexaRefuzataCandSubRezerva() {
        // după anexa 1 (2200) botul ar rămâne cu mai puțin decât rezerva de 300 + chiriile din față
        val s = state(player("bot", 2200 + 100, position = 0), player("ana", 0, owned = mapOf("poli" to 0)), player("c", 0))
        val r = BotAI.utilityScore(s, "bot", board, actions, "poli", BuyOption.AnnexOption(poli.annexes[0]))
        assertTrue(r.hardReject)
    }

    @Test
    fun laDatorieSeVindeOActiuneIntreaga() {
        val debtor = player("bot", 0, owned = mapOf("poli" to 1, "dura" to 0))
        val s = state(debtor, player("ana", 0))
        val sales = BotAI.liquidationSales("bot", 1000, s, board, actions)
        assertTrue(sales.isNotEmpty())
        assertTrue(sales.all { it.annexIndex == null })
    }

    @Test
    fun nuVindeFaraDatorie() {
        val s = state(player("bot", 5000, owned = mapOf("poli" to 1)), player("ana", 0))
        assertTrue(BotAI.liquidationSales("bot", 1000, s, board, actions).isEmpty())
    }

    @Test
    fun visitRateFoloseșteIstoricul() {
        val base = state(player("bot", 0, owned = mapOf("poli" to 0)), player("ana", 0))
        val withVisits = base.copy(
            turn = 20,
            stats = mapOf("poli" to com.example.privatizare.data.model.PropertyStats(mapOf("ana" to 12), 2400)),
        )
        val fresh = BotAI.visitRate(base, "bot", board.size, "poli")
        val seen = BotAI.visitRate(withVisits, "bot", board.size, "poli")
        assertTrue(seen > fresh)
    }

    @Test
    fun motorulInregistreazaVizitaSiIncasarea() {
        val engine = TurnEngine(
            board = board,
            actions = actions,
            loto = listOf(LotoCard("l1", LotoKind.NOTHING, 0, "nimic")),
            telex = listOf(TelexCard("t0", "test", emptyList())),
            random = Random(1),
        )
        val s = state(
            player("bot", 0, owned = mapOf("poli" to 0)),
            player("ana", 50_000, position = 0),
        )
        val r = engine.playTurn(s, 1, listOf(2))
        val due = r.rentDue
        assertTrue(due != null)
        assertEquals(1, r.state.stats["poli"]!!.visits["ana"])
        val paid = engine.payRent(r.state, due!!)
        assertEquals(poli.titleTax, paid.state.stats["poli"]!!.collected)
    }
}
