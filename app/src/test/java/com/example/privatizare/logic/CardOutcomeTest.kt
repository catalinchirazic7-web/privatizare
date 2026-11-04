package com.example.privatizare.logic

import com.example.privatizare.data.model.CardEffect
import com.example.privatizare.data.model.LotoCard
import com.example.privatizare.data.model.LotoKind
import org.junit.Assert.assertEquals
import org.junit.Test

class CardOutcomeTest {

    @Test
    fun lotoCastigPierdereNeutru() {
        assertEquals(CardResult(CardOutcome.GAIN, 2000), lotoResult(LotoCard("a", LotoKind.WIN, 2000, "")))
        assertEquals(CardResult(CardOutcome.LOSS, 3000), lotoResult(LotoCard("b", LotoKind.LOSE, 3000, "")))
        assertEquals(CardResult(CardOutcome.NEUTRAL, null), lotoResult(LotoCard("c", LotoKind.NOTHING, 0, "")))
    }

    @Test
    fun telexIncasareEsteCastig() {
        assertEquals(CardResult(CardOutcome.GAIN, 10_000), telexResult(listOf(CardEffect.ReceiveBank(10_000))))
        assertEquals(CardResult(CardOutcome.GAIN, 2_000), telexResult(listOf(CardEffect.ReceiveFromEachPlayer(2_000))))
    }

    @Test
    fun telexAvansareGratuitaEsteCastigFaraSuma() {
        assertEquals(CardResult(CardOutcome.GAIN, null), telexResult(listOf(CardEffect.Move(4))))
    }

    @Test
    fun telexPlataAmendaSauTuraPierduta() {
        assertEquals(CardResult(CardOutcome.LOSS, 3_000), telexResult(listOf(CardEffect.PayBank(3_000))))
        assertEquals(CardResult(CardOutcome.LOSS, 2_000), telexResult(listOf(CardEffect.PayOwnerOrBank(2_000, "poli"))))
        assertEquals(CardResult(CardOutcome.LOSS, null), telexResult(listOf(CardEffect.Move(-4))))
        // pierderea are prioritate: tură pierdută + plată
        assertEquals(CardResult(CardOutcome.LOSS, 3_000), telexResult(listOf(CardEffect.SkipTurns(2), CardEffect.PayBank(3_000))))
    }

    @Test
    fun telexFaraEfecteEsteNeutru() {
        assertEquals(CardResult(CardOutcome.NEUTRAL, null), telexResult(emptyList()))
    }
}
