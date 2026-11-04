package com.example.privatizare.logic

import com.example.privatizare.data.GameRepository
import com.example.privatizare.data.model.BeneficiaryType
import com.example.privatizare.data.model.CardEffect
import com.example.privatizare.data.model.GameState
import com.example.privatizare.data.model.LotoCard
import com.example.privatizare.data.model.LotoKind
import com.example.privatizare.data.model.PawnColor
import com.example.privatizare.data.model.Player
import com.example.privatizare.data.model.PropertyAction
import com.example.privatizare.data.model.TelexCard
import com.example.privatizare.data.model.Tile
import com.example.privatizare.data.model.Annex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** Cartonașele TELEX cu destinație (notele de pe cartonașe): banii merg la proprietarul companiei, altfel la bancă. */
class TelexBeneficiaryTest {

    private val poli = PropertyAction("poli", "POLI S.R.L.", 1000, 200, listOf(Annex(1, 2200, 350)))
    private val board: List<Tile> = List(37) { if (it == 10) Tile.Telex(it) else Tile.Start(it) }

    private fun engine() = TurnEngine(
        board = board,
        actions = mapOf("poli" to poli),
        loto = listOf(LotoCard("l", LotoKind.NOTHING, 0, "nimic")),
        telex = listOf(
            TelexCard(
                "s", "Sponsorizați clubul POLI", listOf(CardEffect.PayOwnerOrBank(6000, "poli")),
                BeneficiaryType.COMPANY_OWNER, "poli",
            ),
        ),
        random = Random(1),
    )

    /** Motor cu un cartonaș TELEX al cărui nume de companie a fost scris cu pixul. */
    private fun engineHandwritten() = TurnEngine(
        board = board,
        actions = mapOf("poli" to poli),
        loto = listOf(LotoCard("l", LotoKind.NOTHING, 0, "nimic")),
        telex = listOf(
            TelexCard(
                "s", "Sponsorizați clubul POLI", listOf(CardEffect.PayOwnerOrBank(6000, "poli")),
                BeneficiaryType.COMPANY_OWNER, "poli", handwritten = true,
            ),
        ),
        random = Random(1),
    )

    private fun game(poliOwner: Int?) = GameState(
        players = listOf(
            Player("A", "Ana", PawnColor.ROSU, 20_000, 10, if (poliOwner == 0) mapOf("poli" to 0) else emptyMap()),
            Player("B", "Bogdan", PawnColor.ALBASTRU, 20_000, 0, if (poliOwner == 1) mapOf("poli" to 0) else emptyMap()),
        ),
        bankMoney = 100_000,
    )

    @Test
    fun companiaAreProprietar_banii_merg_la_acel_jucator() {
        val r = engine().drawCard(game(poliOwner = 1), 0, "TELEX")
        assertEquals(14_000L, r.state.players[0].money)
        assertEquals(26_000L, r.state.players[1].money)
        assertEquals(100_000L, r.state.bankMoney)
        assertEquals("Banii se achită către: Bogdan (proprietar POLI S.R.L.)", r.card!!.destination)
    }

    @Test
    fun companiaNeprivatizata_banii_merg_la_banca() {
        val r = engine().drawCard(game(poliOwner = null), 0, "TELEX")
        assertEquals(14_000L, r.state.players[0].money)
        assertEquals(20_000L, r.state.players[1].money)
        assertEquals(106_000L, r.state.bankMoney)
        assertEquals("Banii se achită către: Banca (POLI S.R.L. nu e privatizată)", r.card!!.destination)
    }

    @Test
    fun trageProprietarulInsusi_platesteBancii() {
        val r = engine().drawCard(game(poliOwner = 0), 0, "TELEX")
        assertEquals(14_000L, r.state.players[0].money)
        assertEquals(20_000L, r.state.players[1].money)
        assertEquals(106_000L, r.state.bankMoney)
        assertTrue(r.card!!.destination!!.startsWith("Banii se achită către: Banca"))
    }

    // --- Regula casei: pe cartonașele scrise cu pixul, proprietarul nu plătește nimic ---

    @Test
    fun scrisCuPixul_proprietarulInsusi_nuPlatesteNimic() {
        val r = engineHandwritten().drawCard(game(poliOwner = 0), 0, "TELEX")
        assertEquals(20_000L, r.state.players[0].money)
        assertEquals(20_000L, r.state.players[1].money)
        assertEquals(100_000L, r.state.bankMoney)
        assertEquals("Nu plătești nimic: POLI S.R.L. îți aparține", r.card!!.destination)
    }

    @Test
    fun scrisCuPixul_proprietarulInsusi_cartonasulNuEPierdere() {
        val r = engineHandwritten().drawCard(game(poliOwner = 0), 0, "TELEX")
        assertEquals(CardOutcome.NEUTRAL, r.card!!.outcome)
        assertNull(r.card!!.amount)
    }

    @Test
    fun scrisCuPixul_proprietarulAltcuiva_platesteProprietarului() {
        val r = engineHandwritten().drawCard(game(poliOwner = 1), 0, "TELEX")
        assertEquals(14_000L, r.state.players[0].money)
        assertEquals(26_000L, r.state.players[1].money)
        assertEquals(100_000L, r.state.bankMoney)
        assertEquals("Banii se achită către: Bogdan (proprietar POLI S.R.L.)", r.card!!.destination)
    }

    @Test
    fun scrisCuPixul_nuEPrivatizata_platesteBancii() {
        val r = engineHandwritten().drawCard(game(poliOwner = null), 0, "TELEX")
        assertEquals(14_000L, r.state.players[0].money)
        assertEquals(20_000L, r.state.players[1].money)
        assertEquals(106_000L, r.state.bankMoney)
        assertEquals("Banii se achită către: Banca (POLI S.R.L. nu e privatizată)", r.card!!.destination)
    }

    @Test
    fun cartonasFaraNumeDeCompanie_platesteBancii() {
        val e = TurnEngine(
            board, mapOf("poli" to poli),
            listOf(LotoCard("l", LotoKind.NOTHING, 0, "nimic")),
            listOf(TelexCard("b", "Amânat investiție", listOf(CardEffect.PayBank(4000)), BeneficiaryType.BANK)),
            Random(1),
        )
        val r = e.drawCard(game(poliOwner = 1), 0, "TELEX")
        assertEquals(16_000L, r.state.players[0].money)
        assertEquals(20_000L, r.state.players[1].money)
        assertEquals("Banii se achită către: Banca", r.card!!.destination)
    }

    @Test
    fun cardsJson_campurileExpliciteSuntCoerenteCuEfectele() {
        val cards = org.json.JSONObject(File("src/main/assets/cards.json").readText(Charsets.UTF_8))
        val telex = GameRepository.parseTelex(cards.getJSONArray("telex"))
        assertEquals(60, telex.size)
        val ids = org.json.JSONObject(File("src/main/assets/actions.json").readText(Charsets.UTF_8)).getJSONArray("properties")
            .let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("id") }.toSet() }
        for (c in telex) {
            val oob = c.effects.filterIsInstance<CardEffect.PayOwnerOrBank>().firstOrNull()
            when {
                oob != null -> {
                    assertEquals(c.id, BeneficiaryType.COMPANY_OWNER, c.beneficiaryType)
                    assertEquals(c.id, oob.propertyId, c.targetCompanyId)
                    assertTrue(c.id, oob.propertyId in ids)
                }
                c.effects.any { it is CardEffect.PayBank } -> {
                    assertEquals(c.id, BeneficiaryType.BANK, c.beneficiaryType)
                    assertEquals(c.id, null, c.targetCompanyId)
                }
                else -> assertEquals(c.id, null, c.beneficiaryType)
            }
        }
        assertNotNull(telex.first { it.id == "telex_03_1" }.targetCompanyId)
        assertEquals("poli", telex.first { it.id == "telex_04_3" }.targetCompanyId)
        assertEquals("timisoreana", telex.first { it.id == "telex_15_1" }.targetCompanyId)
    }

    @Test
    fun cardsJson_cartonaseleScriseCuPixulAuCompanieSiPlata() {
        val cards = org.json.JSONObject(File("src/main/assets/cards.json").readText(Charsets.UTF_8))
        val telex = GameRepository.parseTelex(cards.getJSONArray("telex"))
        val scrise = telex.filter { it.handwritten }
        assertEquals(34, scrise.size)
        for (c in scrise) {
            // fiecare cartonaș scris cu pixul cere plata unei companii anume
            val plata = c.effects.filterIsInstance<CardEffect.PayOwnerOrBank>().singleOrNull()
            assertNotNull(c.id, plata)
            assertEquals(c.id, c.targetCompanyId, plata!!.propertyId)
            assertEquals(c.id, BeneficiaryType.COMPANY_OWNER, c.beneficiaryType)
            // și nu are alte efecte: scutirea anulează întreaga cartelă
            assertEquals(c.id, 1, c.effects.size)
        }
        // cele două cartonașe „BANCA” / „BANCA PLATEȘTE” nu sunt companii: nu se scutește
        assertEquals(false, telex.first { it.id == "telex_05_2" }.handwritten)
        assertEquals(false, telex.first { it.id == "telex_09_1" }.handwritten)
    }
}
