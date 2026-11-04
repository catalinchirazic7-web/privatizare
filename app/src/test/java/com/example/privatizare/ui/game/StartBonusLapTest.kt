package com.example.privatizare.ui.game

import com.example.privatizare.data.GameRepository
import com.example.privatizare.data.model.GameState
import com.example.privatizare.data.model.LotoCard
import com.example.privatizare.data.model.LotoKind
import com.example.privatizare.data.model.PawnColor
import com.example.privatizare.data.model.Player
import com.example.privatizare.data.model.TelexCard
import com.example.privatizare.data.model.Tile
import com.example.privatizare.logic.TurnEngine
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** Verifică bonusul de 3000 ECU de la bancă la fiecare tur complet al tablei. */
class StartBonusLapTest {

    private fun asset(name: String) = File("src/main/assets/$name").readText(Charsets.UTF_8)

    @Test
    fun fiecareTurCompletAduceExact3000EcuDeLaBanca() {
        // Tablă fără efecte (38 de căsuțe neutre), ca singura mișcare de bani să fie bonusul
        val neutral = GameRepository.Catalog(
            board = List(37) { i -> Tile.Start(i) },
            actions = emptyMap(),
            loto = listOf(LotoCard("l", LotoKind.NOTHING, 0, "nimic")),
            telex = listOf(TelexCard("t", "test", emptyList())),
            positions = List(37) { 0.5f to 0.5f },
        )
        val vm = GameViewModel(neutral).also {
            it.startGame(listOf(PlayerSetup("Ana", PawnColor.ROSU), PlayerSetup("Bogdan", PawnColor.ALBASTRU)))
        }
        val start = vm.game!!.players[0].money
        val bankStart = vm.game!!.bankMoney

        val turns = 40 // fiecare jucător aruncă 6 de 40 de ori: 240 de căsuțe = 6 ture complete + 12
        repeat(turns) {
            vm.roll(6); vm.passTurn()   // Ana
            vm.roll(6); vm.passTurn()   // Bogdan
        }

        val laps = (6 * turns) / 38     // 6 ture complete
        assertEquals(6, laps)
        for (p in vm.game!!.players) {
            assertEquals("${p.name}: bonus de ture", start + 3000L * laps, p.money)
        }
        // Banca a plătit exact aceleași bonusuri celor doi jucători
        assertEquals(bankStart - 3000L * laps * 2, vm.game!!.bankMoney)
        // Fiecare bonus apare în jurnal
        val bonusLines = vm.game!!.history.count { it.contains("3000 de la bancă (trecere prin START)") }
        assertEquals(laps * 2, bonusLines)
    }

    @Test
    fun peTablaRealaTrecerePrinStartDaExact3000() {
        val cards = JSONObject(asset("cards.json"))
        val board = GameRepository.parseBoard(asset("board.json"))
        val engine = TurnEngine(
            board = board,
            actions = GameRepository.parseActions(asset("actions.json")),
            loto = GameRepository.parseLoto(cards.getJSONArray("loto")),
            telex = GameRepository.parseTelex(cards.getJSONArray("telex")),
            random = Random(1),
        )
        fun state(pos: Int) = GameState(
            players = listOf(
                Player("A", "Ana", PawnColor.ROSU, money = 100_000, position = pos),
                Player("B", "Bogdan", PawnColor.ALBASTRU, money = 100_000),
            ),
            bankMoney = 300_000,
        )

        // 36 + 1 = 37 -> tur complet: +3000, apoi căsuța 0 (CONTINENTAL, titlu liber)
        val onStart = engine.playTurn(state(36), 0, listOf(1)).state.players[0]
        assertEquals(0, onStart.position)
        assertEquals(103_000L, onStart.money)

        // 36 + 2 = 38 -> 38 - 37 = căsuța 1 (LOTO): +3000, plus cartonașul tras
        val past = engine.playTurn(state(36), 0, listOf(2))
        assertEquals(1, past.state.players[0].position)
    }

    @Test
    fun rechematUrgentNuAcordaBonus() {
        val cards = JSONObject(asset("cards.json"))
        val board = GameRepository.parseBoard(asset("board.json"))
        val engine = TurnEngine(
            board = board,
            actions = GameRepository.parseActions(asset("actions.json")),
            loto = GameRepository.parseLoto(cards.getJSONArray("loto")),
            telex = GameRepository.parseTelex(cards.getJSONArray("telex")),
            random = Random(1),
        )
        // 16 + 1 = 17 -> RECHEMAT URGENT: pionul ajunge la START, dar fără bonus
        val s = GameState(
            players = listOf(
                Player("A", "Ana", PawnColor.ROSU, money = 100_000, position = 16),
                Player("B", "Bogdan", PawnColor.ALBASTRU, money = 100_000),
            ),
            bankMoney = 300_000,
        )
        val after = engine.playTurn(s, 0, listOf(1)).state.players[0]
        assertEquals(-1, after.position)   // înapoi pe săgeata de START
        assertEquals(100_000L, after.money)
    }
}
