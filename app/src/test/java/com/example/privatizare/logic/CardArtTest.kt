package com.example.privatizare.logic

import com.example.privatizare.data.GameRepository
import com.example.privatizare.data.model.GameState
import com.example.privatizare.data.model.PawnColor
import com.example.privatizare.data.model.Player
import com.example.privatizare.data.model.Tile
import com.example.privatizare.multiplayer.CardSnap
import com.example.privatizare.multiplayer.GameSnapshot
import com.example.privatizare.multiplayer.SnapshotCodec
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * Fotografia cartonașului tras trebuie găsită după id: de la motor, prin snapshot, până la fișier.
 * Textul cartonașului nu poate fi cheie: zece cartonașe LOTO au exact același text.
 */
class CardArtTest {

    private val cards = JSONObject(File("src/main/assets/cards.json").readText(Charsets.UTF_8))
    private val loto = GameRepository.parseLoto(cards.getJSONArray("loto"))
    private val telex = GameRepository.parseTelex(cards.getJSONArray("telex"))

    @Test
    fun fiecareCartonasAreFotografieProprieInAssets() {
        assertEquals(36, loto.size)
        assertEquals(60, telex.size)
        for (id in loto.map { it.id } + telex.map { it.id }) {
            val f = File("src/main/assets/carti/$id.webp")
            assertTrue("lipsă fotografia $id.webp", f.exists())
            assertTrue("fotografie goală $id.webp", f.length() > 1000)
        }
    }

    @Test
    fun hartaFotografiilorAreCateOImaginePentruFiecareCartonas() {
        val map = GameRepository.cardArtMap(loto, telex)
        assertEquals("nicio suprapunere de id", 96, map.size)
        for (id in loto.map { it.id } + telex.map { it.id }) {
            assertEquals(id, "carti/$id.webp", map[id])
        }
    }

    @Test
    fun cartonasulTrasatPastreazaIdul() {
        val e = TurnEngine(
            board = List(37) { Tile.Start(it) },
            actions = emptyMap(),
            loto = loto,
            telex = telex,
            random = Random(7),
        )
        val st = GameState(
            players = listOf(Player("A", "Ana", PawnColor.ROSU, 20_000, 0)),
            bankMoney = 100_000,
        )
        val r = e.drawCard(st, 0, "LOTO")
        assertNotNull(r.card!!.cardId)
        assertTrue(r.card!!.cardId!! in loto.map { it.id })
        val r2 = e.drawCard(r.state, 0, "TELEX")
        assertNotNull(r2.card!!.cardId)
        assertTrue(r2.card!!.cardId!! in telex.map { it.id })
    }

    @Test
    fun idulCartonasuluiSupravietuiesteSnapshotuluiOnline() {
        val snap = GameSnapshot(
            state = GameState(
                players = listOf(Player("A", "Ana", PawnColor.ROSU, 20_000, 0)),
                bankMoney = 100_000,
            ),
            currentIndex = 0,
            lastDice = listOf(3),
            winnerName = null,
            turnAwaitingEnd = false,
            skipBanner = null,
            offer = null,
            feeOffer = null,
            rentDue = null,
            debt = null,
            card = CardSnap("TELEX", "Nu ați câștigat, dar nici nu ați pierdut", "NEUTRAL", null, null, "telex_07_1"),
        )
        val back = SnapshotCodec.decode(SnapshotCodec.encode(snap))
        assertNotNull(back.card)
        assertEquals("telex_07_1", back.card!!.cardId)
    }
}
