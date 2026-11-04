package com.example.privatizare.multiplayer

import com.example.privatizare.logic.Sale
import com.example.privatizare.ui.game.GameViewModel
import com.example.privatizare.ui.game.TurnDialog

/**
 * Execută un eveniment primit exact ca apăsarea butonului corespunzător.
 * Respinge evenimentele care nu vin de la jucătorul la rând sau nu corespund stării curente.
 * [dieFor] decide zarul: gazda își trage singură zarul (valoarea trimisă de oaspete se ignoră online),
 * iar testele pot da o valoare fixă. Returnează true dacă evenimentul a fost aplicat.
 */
fun applyEvent(vm: GameViewModel, event: GameEvent, dieFor: (GameEvent.RollDiceEvent) -> Int = { it.value }): Boolean {
    val current = vm.currentPlayer ?: return false
    if (event.playerId != current.id) return false
    val dialog = vm.activeDialog

    return when (event) {
        is GameEvent.RollDiceEvent -> {
            if (dialog != null || vm.turnAwaitingEnd) return false
            vm.roll(dieFor(event))
            true
        }
        is GameEvent.BuyPropertyEvent -> {
            val offer = vm.pendingOffer ?: return false
            if (vm.propertyAtTile(event.tileIndex) != offer.propertyId) return false
            vm.acceptOffer()
            true
        }
        is GameEvent.DeclinePropertyEvent -> {
            val offer = vm.pendingOffer ?: return false
            if (vm.propertyAtTile(event.tileIndex) != offer.propertyId) return false
            vm.refuseOffer()
            true
        }
        is GameEvent.BuyAnnexEvent -> {
            val propId = vm.propertyAtTile(event.tileIndex) ?: return false
            if (!vm.turnAwaitingEnd || dialog != null) return false
            vm.buyNextAnnex(propId)
            true
        }
        is GameEvent.EndTurnEvent -> {
            if (dialog != null || !vm.turnAwaitingEnd) return false
            vm.endTurn()
            true
        }
        is GameEvent.PayRentEvent -> {
            if (dialog !is TurnDialog.RentDuePending) return false
            vm.payRent()
            true
        }
        is GameEvent.AcknowledgeEvent -> when (dialog) {
            is TurnDialog.BankFeePending -> { vm.acknowledgeBankFee(); true }
            is TurnDialog.CardPending -> { vm.dismissCard(); true }
            else -> false
        }
        is GameEvent.SellEvent -> {
            if (dialog !is TurnDialog.DebtPending || dialog.debt.debtorId != event.playerId) return false
            vm.settleDebt(listOf(Sale(event.propertyId, event.annexIndex)))
            true
        }
        is GameEvent.DrawCardEvent -> {
            if (dialog !is TurnDialog.DrawPending) return false
            vm.drawCard()
            true
        }
        is GameEvent.BankruptEvent -> {
            if (dialog !is TurnDialog.DebtPending || dialog.debt.debtorId != event.playerId) return false
            vm.settleDebt(emptyList())
            true
        }
    }
}
