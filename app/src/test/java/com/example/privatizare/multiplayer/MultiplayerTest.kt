package com.example.privatizare.multiplayer

import com.example.privatizare.data.GameRepository
import com.example.privatizare.data.model.Annex
import com.example.privatizare.data.model.LotoCard
import com.example.privatizare.data.model.LotoKind
import com.example.privatizare.data.model.PawnColor
import com.example.privatizare.data.model.PropertyAction
import com.example.privatizare.data.model.TelexCard
import com.example.privatizare.data.model.Tile
import com.example.privatizare.ui.game.GameViewModel
import com.example.privatizare.ui.game.PlayerSetup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiplayerTest {

    private val poli = PropertyAction(
        id = "poli", name = "POLI", titlePrice = 1000, titleTax = 200,
        annexes = listOf(Annex(1, 2200, 350)),
    )

    private val catalog = GameRepository.Catalog(
        board = List(37) { i -> if (i == 1) Tile.Property(i, "poli") else Tile.Start(i) },
        actions = mapOf("poli" to poli),
        loto = listOf(LotoCard("l", LotoKind.NOTHING, 0, "nimic")),
        telex = listOf(TelexCard("t", "test", emptyList())),
        positions = List(37) { 0.5f to 0.5f },
    )

    private fun vm(): GameViewModel = GameViewModel(catalog).also {
        it.startGame(listOf(PlayerSetup("Ana", PawnColor.ROSU), PlayerSetup("Bogdan", PawnColor.ALBASTRU)))
    }

    @Test
    fun codulCamereiAreFormatCorect() {
        repeat(50) {
            val code = RoomCode.generate()
            assertEquals(4, code.length)
            assertTrue(RoomCode.isValid(code))
        }
        assertFalse(RoomCode.isValid("ab1"))
        assertFalse(RoomCode.isValid("ABC0")) // 0 nu face parte din alfabet
    }

    @Test
    fun canalulLocalLivreazaEvenimentulInapoi() {
        val bridge = LocalPassAndPlayBridge()
        val received = mutableListOf<GameEvent>()
        bridge.setListener { received += it }
        val event = GameEvent.RollDiceEvent("p0", 3)
        bridge.send(event)
        assertEquals(listOf(event), received)
    }

    @Test
    fun evenimentulDeLaJucatorulGresitEsteRespins() {
        val vm = vm()
        // E rândul lui Ana (p0); evenimentul vine de la Bogdan
        assertFalse(applyEvent(vm, GameEvent.RollDiceEvent("p1", 2)))
        assertEquals(-1, vm.game!!.players[1].position)
    }

    @Test
    fun zarulDeLaJucatorulLaRandSeAplica() {
        val vm = vm()
        assertTrue(applyEvent(vm, GameEvent.RollDiceEvent("p0", 2)))
        assertTrue(vm.activeDialog is com.example.privatizare.ui.game.TurnDialog.BuyDecisionPending)
    }

    @Test
    fun cumparareaPrinEvenimentAreAcelasiEfectCaLaButon() {
        val vm = vm()
        applyEvent(vm, GameEvent.RollDiceEvent("p0", 2))
        assertTrue(applyEvent(vm, GameEvent.BuyPropertyEvent("p0", 1)))
        assertEquals(249_000L, vm.game!!.players[0].money)
        assertEquals(0, vm.game!!.players[0].ownedProperties["poli"])
    }

    @Test
    fun cumparareaCuCasaGresitaEsteRespinsa() {
        val vm = vm()
        applyEvent(vm, GameEvent.RollDiceEvent("p0", 2))
        assertFalse(applyEvent(vm, GameEvent.BuyPropertyEvent("p0", 5)))
        assertEquals(250_000L, vm.game!!.players[0].money)
    }
}
