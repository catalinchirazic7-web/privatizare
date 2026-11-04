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

/** Robotul joacă o tură întreagă: zar, decizie de cumpărare și [TERMINĂ TURA]. Zarul e fix (2). */
class BotTurnTest {

    private fun catalog(titlePrice: Long) = GameRepository.Catalog(
        board = List(37) { i -> if (i == 1) Tile.Property(i, "poli") else Tile.Start(i) },
        actions = mapOf(
            "poli" to PropertyAction(
                id = "poli", name = "POLI", titlePrice = titlePrice, titleTax = 200,
                annexes = listOf(Annex(1, 2200, 350)),
            ),
        ),
        loto = listOf(LotoCard("l", LotoKind.NOTHING, 0, "nimic")),
        telex = listOf(TelexCard("t", "test", emptyList())),
        positions = List(37) { 0.5f to 0.5f },
    )

    private fun vm(titlePrice: Long = 1000): GameViewModel =
        GameViewModel(catalog(titlePrice), botDie = { 2 }).also {
            it.startGame(listOf(
                PlayerSetup("Om", PawnColor.ROSU, isBot = false),
                PlayerSetup("Robot", PawnColor.ALBASTRU, isBot = true),
            ))
        }

    @Test
    fun robotulCumparaTitluLaCareAjungeCandAreBani() {
        val vm = vm(titlePrice = 1000)
        vm.roll(1) // omul aruncă și ajunge pe START, fără efect
        vm.passTurn() // apoi încheie tura și predă robotului
        assertTrue(vm.botNextAction())   // robotul aruncă 2 și aterizează pe POLI
        assertTrue(vm.activeDialog is TurnDialog.BuyDecisionPending)
        assertTrue(vm.botNextAction())   // cumpără: are bani
        assertEquals(1, vm.game!!.players[1].ownedProperties.size)
        assertEquals("p1", vm.game!!.ownerOf("poli")!!.id)
        assertTrue(vm.botNextAction())   // încheie tura
        assertEquals(0, vm.currentIndex)
    }

    @Test
    fun robotulRefuzaDoarCandNuAreBani() {
        val vm = vm(titlePrice = 300_000) // prețul depășește soldul de 250 000
        vm.roll(1)
        vm.passTurn()
        vm.botNextAction() // aruncă și aterizează
        vm.botNextAction() // nu are bani -> refuză
        assertTrue(vm.activeDialog is TurnDialog.BankFeePending)
        vm.botNextAction() // plătește taxa Băncii
        assertNull(vm.game!!.ownerOf("poli"))
        assertEquals(249_800L, vm.game!!.players[1].money)
    }
}
