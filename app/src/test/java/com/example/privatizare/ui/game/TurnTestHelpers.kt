package com.example.privatizare.ui.game

/** [TERMINĂ TURA] urmat de predarea telefonului („Sunt gata”), ca în joc. */
internal fun GameViewModel.passTurn() {
    endTurn()
    if (activeDialog is TurnDialog.HandoverPending) acknowledgeHandover()
}
