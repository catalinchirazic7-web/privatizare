package com.example.privatizare.ui.game

import com.example.privatizare.data.GameRepository
import com.example.privatizare.data.model.LotoCard
import com.example.privatizare.data.model.LotoKind
import com.example.privatizare.data.model.PawnColor
import com.example.privatizare.data.model.TelexCard
import com.example.privatizare.data.model.Tile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cartonașele se trag manual: aterizarea pe LOTO/TELEX nu schimbă banii până nu atingi pachetul. */
class ManualDrawTest {

    private val catalog = GameRepository.Catalog(
        board = List(37) { i ->
            when (i) {
                0 -> Tile.Loto(i)
                1 -> Tile.Telex(i)
                else -> Tile.Start(i)
            }
        },
        actions = emptyMap(),
        loto = listOf(LotoCard("w", LotoKind.WIN, 500, "Câștigați 500")),
        telex = listOf(TelexCard("t", "plătește", listOf(com.example.privatizare.data.model.CardEffect.PayBank(300)))),
        positions = List(37) { 0.5f to 0.5f },
    )

    private fun vm(): GameViewModel = GameViewModel(catalog).also {
        it.startGame(listOf(PlayerSetup("Ana", PawnColor.ROSU), PlayerSetup("Bogdan", PawnColor.ALBASTRU)))
    }

    @Test
    fun peLotoNuSeTrageSingurSiTuraNuTrece() {
        val vm = vm()
        vm.roll(1)
        assertEquals("LOTO", (vm.activeDialog as TurnDialog.DrawPending).kind)
        assertEquals("LOTO", vm.pendingDraw)
        assertEquals(250_000L, vm.game!!.players[0].money)
        vm.passTurn()   // [TERMINĂ TURA] nu merge cât pachetul așteaptă să fie tras
        assertEquals(0, vm.currentIndex)
        vm.roll(3)      // nici zarul nu merge
        assertEquals(0, vm.game!!.players[0].position)
    }

    @Test
    fun atingereaPachetuluiTrageCartonasulSiAplicaEfectul() {
        val vm = vm()
        vm.roll(2)      // TELEX
        vm.drawCard()
        assertNull(vm.pendingDraw)
        assertTrue(vm.activeDialog is TurnDialog.CardPending)
        assertEquals(249_700L, vm.game!!.players[0].money)
        vm.dismissCard()
        assertNull(vm.activeDialog)
        assertTrue(vm.turnAwaitingEnd)
    }

    @Test
    fun robotulTrageSingurCartonasul() {
        val vm = GameViewModel(catalog, botDie = { 1 }).also {
            it.startGame(listOf(PlayerSetup("Om", PawnColor.ROSU), PlayerSetup("Robot", PawnColor.ALBASTRU, isBot = true)))
        }
        vm.roll(3); vm.passTurn()          // omul pe START, rândul robotului
        vm.botNextAction()                 // robotul aruncă 1 -> LOTO
        assertTrue(vm.activeDialog is TurnDialog.DrawPending)
        vm.botNextAction()                 // robotul trage
        assertTrue(vm.activeDialog is TurnDialog.CardPending)
        assertEquals(250_500L, vm.game!!.players[1].money)
    }
}
