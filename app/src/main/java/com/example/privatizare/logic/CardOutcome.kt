package com.example.privatizare.logic

import com.example.privatizare.data.model.CardEffect
import com.example.privatizare.data.model.LotoCard
import com.example.privatizare.data.model.LotoKind

/** Ce înseamnă un cartonaș pentru jucătorul care l-a tras: folosit la efectele vizuale. */
enum class CardOutcome { GAIN, LOSS, NEUTRAL }

/** Rezultatul și suma (în ECU, fără semn) afișate pe cartonaș; suma e null dacă nu e o sumă de bani. */
data class CardResult(val outcome: CardOutcome, val amount: Long?)

fun lotoResult(card: LotoCard): CardResult = when (card.kind) {
    LotoKind.WIN -> CardResult(CardOutcome.GAIN, card.amount)
    LotoKind.LOSE -> CardResult(CardOutcome.LOSS, card.amount)
    LotoKind.NOTHING -> CardResult(CardOutcome.NEUTRAL, null)
}

/**
 * Cartonaș TELEX: orice pierdere (plată, amendă, tură pierdută, mers înapoi) îl face "pierdere",
 * altfel orice câștig (încasare, avansare) îl face "câștig". Suma e totalul banilor, cu semnul rezultatului.
 */
fun telexResult(effects: List<CardEffect>): CardResult {
    var gain = 0L
    var loss = 0L
    var anyGain = false
    var anyLoss = false
    for (e in effects) {
        when (e) {
            is CardEffect.PayBank -> { loss += e.amount; anyLoss = true }
            is CardEffect.PayOwnerOrBank -> { loss += e.amount; anyLoss = true }
            is CardEffect.ReceiveBank -> { gain += e.amount; anyGain = true }
            is CardEffect.ReceiveFromEachPlayer -> { gain += e.amount; anyGain = true }
            is CardEffect.Move -> if (e.steps > 0) anyGain = true else anyLoss = true
            is CardEffect.SkipTurns -> anyLoss = true
        }
    }
    return when {
        anyLoss -> CardResult(CardOutcome.LOSS, loss.takeIf { it > 0 })
        anyGain -> CardResult(CardOutcome.GAIN, gain.takeIf { it > 0 })
        else -> CardResult(CardOutcome.NEUTRAL, null)
    }
}
