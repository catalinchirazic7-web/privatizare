package com.example.privatizare.ui.game

import com.example.privatizare.data.GameRepository
import com.example.privatizare.data.model.Annex
import com.example.privatizare.data.model.LotoCard
import com.example.privatizare.data.model.LotoKind
import com.example.privatizare.data.model.PawnColor
import com.example.privatizare.data.model.PropertyAction
import com.example.privatizare.data.model.TelexCard
import com.example.privatizare.data.model.Tile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameViewModelTest {

    private val poli = PropertyAction(
        id = "poli", name = "POLI", titlePrice = 1000, titleTax = 200,
        annexes = listOf(Annex(1, 2200, 350)),
    )

    private val catalog = GameRepository.Catalog(
        board = List(37) { i -> if (i == 1) Tile.Property(i, "poli") else Tile.Start(i) },
        actions = mapOf("poli" to poli),
        loto = listOf(LotoCard("l", LotoKind.NOTHING, 0, "nimic")),
        telex = listOf(TelexCard("t", "x", emptyList())),
        positions = List(37) { 0.5f to 0.5f },
    )

    private fun vmWithTwoPlayers(): GameViewModel = GameViewModel(catalog).also {
        it.startGame(listOf(PlayerSetup("Ana", PawnColor.ROSU), PlayerSetup("Bogdan", PawnColor.ALBASTRU)))
    }

    @Test
    fun zarulEsteBlocatCatTimpCeExistaUnDialog() {
        val vm = vmWithTwoPlayers()
        vm.roll(2) // Ana aterizează pe POLI liber
        assertTrue(vm.activeDialog is TurnDialog.BuyDecisionPending)
        val before = vm.game
        vm.roll(5) // încercare de a arunca din nou
        assertEquals(before, vm.game)
        assertEquals(0, vm.currentIndex)
    }

    @Test
    fun refuzulTitluluiDeschideBalonulSiOkAplicaTaxa() {
        val vm = vmWithTwoPlayers()
        vm.roll(2)
        vm.refuseOffer()
        assertTrue(vm.activeDialog is TurnDialog.BankFeePending)
        assertEquals(250000L, vm.game!!.players[0].money) // taxa încă neplătită
        vm.acknowledgeBankFee()
        assertNull(vm.activeDialog)
        assertEquals(249800L, vm.game!!.players[0].money)
        assertEquals(0, vm.currentIndex) // tura e încă deschisă
        vm.passTurn()
        assertEquals(1, vm.currentIndex) // tura a trecut la Bogdan
    }

    @Test
    fun cumparareaDeschideTitluluiSiTuraTrece() {
        val vm = vmWithTwoPlayers()
        vm.roll(2)
        vm.acceptOffer()
        assertEquals(249000L, vm.game!!.players[0].money)
        assertEquals(0, vm.game!!.players[0].ownedProperties["poli"])
        assertNull(vm.activeDialog)
        assertEquals(0, vm.currentIndex) // tura e încă deschisă
        vm.passTurn()
        assertEquals(1, vm.currentIndex)
    }

    @Test
    fun chiriaSePlateșteLaButonAposAsteptare() {
        val vm = vmWithTwoPlayers()
        vm.roll(2)
        vm.acceptOffer() // Ana cumpără POLI
        vm.passTurn() // acum Bogdan (index 1) e la rând
        vm.roll(2) // Bogdan aterizează pe POLI
        val dialog = vm.activeDialog
        assertTrue(dialog is TurnDialog.RentDuePending)
        assertEquals(200L, (dialog as TurnDialog.RentDuePending).rentAmount)
        assertEquals(250000L, vm.game!!.players[1].money) // nimic plătit încă
        vm.payRent()
        // Ana: 125000 - 1000 (titlu POLI) + 200 (chirie) = 124200
        assertEquals(249200L, vm.game!!.players[0].money)
        assertEquals(249800L, vm.game!!.players[1].money)
        assertNull(vm.activeDialog)
    }
}
