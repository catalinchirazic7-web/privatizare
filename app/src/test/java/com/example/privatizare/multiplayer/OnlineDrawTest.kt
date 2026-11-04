package com.example.privatizare.multiplayer

import com.example.privatizare.data.GameRepository
import com.example.privatizare.data.model.LotoCard
import com.example.privatizare.data.model.LotoKind
import com.example.privatizare.data.model.PawnColor
import com.example.privatizare.data.model.TelexCard
import com.example.privatizare.data.model.Tile
import com.example.privatizare.ui.game.GameViewModel
import com.example.privatizare.ui.game.TurnDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineDrawTest {

    private val catalog = GameRepository.Catalog(
        board = List(37) { i -> if (i == 1) Tile.Loto(i) else Tile.Start(i) },
        actions = emptyMap(),
        loto = listOf(LotoCard("w", LotoKind.WIN, 500, "Câștigați 500")),
        telex = listOf(TelexCard("t", "x", emptyList())),
        positions = List(37) { 0.5f to 0.5f },
    )

    @Test
    fun oaspeteleTrageCartonasulPrinIntentie() {
        val backend = InMemoryRoomBackend()
        val hostVm = GameViewModel(catalog)
        val guestVm = GameViewModel(catalog)
        val host = OnlineController(backend, hostVm, hostDie = { 2 }, newCode = { "ABCD" })
        val guest = OnlineController(backend, guestVm)
        host.createRoom("Gazda", PawnColor.ROSU)
        guest.joinRoom("ABCD", "Oaspete", PawnColor.ALBASTRU)
        host.startMatch()

        hostVm.roll(1); host.publishNow()          // gazda: tura ei, fără efect
        hostVm.endTurn(); host.publishNow()
        guestVm.roll(6)                             // oaspetele aruncă; gazda trage 2 -> LOTO
        assertTrue(guestVm.activeDialog is TurnDialog.DrawPending)       // pachetul pulsează și la oaspete
        assertEquals("LOTO", guestVm.pendingDraw)
        assertEquals(250_000L, guestVm.game!!.players[1].money)

        guestVm.drawCard()                          // oaspetele atinge pachetul
        assertTrue(hostVm.activeDialog is TurnDialog.CardPending)
        assertTrue(guestVm.activeDialog is TurnDialog.CardPending)
        assertEquals(250_500L, hostVm.game!!.players[1].money)
        assertEquals(250_500L, guestVm.game!!.players[1].money)
    }

    @Test
    fun trageDoarJucatorulLaRand() {
        val vm = GameViewModel(catalog).also {
            it.startGame(listOf(com.example.privatizare.ui.game.PlayerSetup("A", PawnColor.ROSU), com.example.privatizare.ui.game.PlayerSetup("B", PawnColor.ALBASTRU)))
        }
        vm.roll(2)
        assertTrue(applyEvent(vm, GameEvent.DrawCardEvent("p0")))
        assertEquals(false, applyEvent(vm, GameEvent.DrawCardEvent("p1")))
    }
}
