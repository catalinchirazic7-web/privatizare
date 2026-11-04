package com.example.privatizare.multiplayer

import com.example.privatizare.data.model.GameState
import com.example.privatizare.data.model.PawnColor
import com.example.privatizare.data.model.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodecTest {

    @Test
    fun fiecareIntentieSeDecodeazaLaFel() {
        val events = listOf(
            GameEvent.RollDiceEvent("p1", 4),
            GameEvent.BuyPropertyEvent("p1", 2),
            GameEvent.DeclinePropertyEvent("p2", 9),
            GameEvent.BuyAnnexEvent("p1", 2),
            GameEvent.EndTurnEvent("p3"),
            GameEvent.PayRentEvent("p1"),
            GameEvent.AcknowledgeEvent("p1"),
            GameEvent.SellEvent("p1", "poli", 2),
            GameEvent.SellEvent("p1", "poli", null),
            GameEvent.BankruptEvent("p2"),
        )
        for (e in events) assertEquals(e, IntentCodec.decode(IntentCodec.encode(e)))
    }

    @Test
    fun instantaneulSeSerializeazaFaraPierderi() {
        val players = listOf(
            Player("p0", "Ana", PawnColor.ROSU, 12_345, 7, mapOf("poli" to 2, "dura" to 0), skipTurns = 1),
            Player("p1", "Robot 1", PawnColor.NEGRU, 99, 30, isBot = true),
        )
        val snap = GameSnapshot(
            state = GameState(turn = 3, players = players, bankMoney = 4_700, history = listOf("a", "b")),
            currentIndex = 1,
            lastDice = listOf(5),
            winnerName = null,
            turnAwaitingEnd = true,
            skipBanner = "banner",
            offer = OfferSnap("p1", "poli", null),
            feeOffer = OfferSnap("p1", "dura", 1),
            rentDue = RentSnap("p1", "p0", "poli", 550),
            debt = DebtSnap("p1", 550, null, "chirie"),
            card = CardSnap("TELEX", "text cu diacritice: ăîșțâ"),
        )
        assertEquals(snap, SnapshotCodec.decode(SnapshotCodec.encode(snap)))
    }

    @Test
    fun campurileGoaleRamanGoale() {
        val snap = GameSnapshot(
            state = GameState(players = listOf(Player("p0", "A", PawnColor.VERDE, 1)), bankMoney = 0),
            currentIndex = 0, lastDice = emptyList(), winnerName = "A", turnAwaitingEnd = false,
            skipBanner = null, offer = null, feeOffer = null, rentDue = null, debt = null, card = null,
        )
        val back = SnapshotCodec.decode(SnapshotCodec.encode(snap))
        assertEquals(snap, back)
        assertNull(back.debt)
    }

    @Test
    fun istoriculSeLimiteaza() {
        val long = (1..50).map { "m$it" }
        val snap = GameSnapshot(
            state = GameState(players = listOf(Player("p0", "A", PawnColor.VERDE, 1)), bankMoney = 0, history = long),
            currentIndex = 0, lastDice = emptyList(), winnerName = null, turnAwaitingEnd = false,
            skipBanner = null, offer = null, feeOffer = null, rentDue = null, debt = null, card = null,
        )
        val back = SnapshotCodec.decode(SnapshotCodec.encode(snap))
        assertEquals(SnapshotCodec.MAX_HISTORY, back.state.history.size)
        assertEquals("m50", back.state.history.last())
    }

    @Test
    fun hartaCameriiSeCitesteSiSeScrie() {
        val info = RoomInfo(
            RoomStage.PLAYING, "p0",
            listOf(
                RoomPlayer("p0", "Gazda", PawnColor.ROSU, RoomRole.HOST),
                RoomPlayer("p2", "Robot 1", PawnColor.MOV, RoomRole.BOT),
            ),
        )
        assertEquals(info, RoomRules.fromMap(RoomRules.toMap(info)))
        assertNull(RoomRules.fromMap(null))
        assertNull(RoomRules.fromMap(mapOf("stage" to "NECUNOSCUT", "hostId" to "p0")))
    }
}
