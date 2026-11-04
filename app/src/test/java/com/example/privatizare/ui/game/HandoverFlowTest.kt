package com.example.privatizare.ui.game

import com.example.privatizare.data.GameRepository
import com.example.privatizare.data.model.LotoCard
import com.example.privatizare.data.model.LotoKind
import com.example.privatizare.data.model.Annex
import com.example.privatizare.data.model.PropertyAction
import com.example.privatizare.data.model.PawnColor
import com.example.privatizare.data.model.TelexCard
import com.example.privatizare.data.model.Tile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Predarea telefonului între jucători: zarul rămâne blocat până la „Sunt gata”. */
class HandoverFlowTest {

    private val catalog = GameRepository.Catalog(
        board = List(37) { i -> Tile.Start(i) },
        actions = emptyMap(),
        loto = listOf(LotoCard("l", LotoKind.NOTHING, 0, "nimic")),
        telex = listOf(TelexCard("t", "test", emptyList())),
        positions = List(37) { 0.5f to 0.5f },
    )

    private fun vm(): GameViewModel = GameViewModel(catalog).also {
        it.startGame(listOf(PlayerSetup("Ana", PawnColor.ROSU), PlayerSetup("Bogdan", PawnColor.ALBASTRU)))
    }

    @Test
    fun dupaTerminaTuraApareEcranulDePredare() {
        val vm = vm()
        vm.roll(1)
        vm.endTurn()
        val dialog = vm.activeDialog
        assertTrue(dialog is TurnDialog.HandoverPending)
        assertEquals("Bogdan", (dialog as TurnDialog.HandoverPending).playerName)
    }

    @Test
    fun zarulEsteBlocatPanaLaSuntGata() {
        val vm = vm()
        vm.roll(1)
        vm.endTurn()
        vm.roll(3) // Bogdan încă nu a preluat telefonul
        assertEquals(-1, vm.game!!.players[1].position)

        vm.acknowledgeHandover()
        assertEquals(null, vm.activeDialog)
        vm.roll(3)
        assertEquals(2, vm.game!!.players[1].position)
    }

    @Test
    fun laInceputulPartiideNuExistaPredare() {
        val vm = vm()
        assertEquals(null, vm.activeDialog)
        vm.roll(1) // Ana joacă primul fără ecran de predare
        assertEquals(0, vm.game!!.players[0].position)
    }

    @Test
    fun inainteaDeLichidareSePredaDispozitivul() {
        // POLI cu taxă de 300 000: Bogdan (250 000) nu poate plăti chiria -> datorie
        val poliBig = PropertyAction(
            id = "poli", name = "POLI", titlePrice = 1000, titleTax = 300_000,
            annexes = listOf(Annex(1, 2200, 350)),
        )
        val liquidationCatalog = GameRepository.Catalog(
            board = List(37) { i -> if (i == 1) Tile.Property(i, "poli") else Tile.Start(i) },
            actions = mapOf("poli" to poliBig),
            loto = listOf(LotoCard("l", LotoKind.NOTHING, 0, "nimic")),
            telex = listOf(TelexCard("t", "test", emptyList())),
            positions = List(37) { 0.5f to 0.5f },
        )
        val vm = GameViewModel(liquidationCatalog).also {
            it.startGame(listOf(PlayerSetup("Ana", PawnColor.ROSU), PlayerSetup("Bogdan", PawnColor.ALBASTRU)))
        }
        vm.roll(2)
        vm.acceptOffer()
        vm.passTurn()
        vm.roll(2) // Bogdan aterizează pe POLI
        vm.payRent() // nu are fonduri -> datorie

        val first = vm.activeDialog
        assertTrue(first is TurnDialog.LiquidationHandover)
        assertEquals("Bogdan", (first as TurnDialog.LiquidationHandover).debtorName)

        vm.acknowledgeLiquidation()
        assertTrue(vm.activeDialog is TurnDialog.DebtPending)
    }
}
