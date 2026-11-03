// Execută o intenție primită exact ca apăsarea butonului corespunzător. Port din MultiplayerBridge/ActionDispatcher.kt.
// Respinge evenimentele care nu corespund stării curente.

import { EV } from './protocol.js';

/**
 * @param {import('../game/game.js').Game} vm
 * @param {object} event   eveniment decodat (vezi protocol.js)
 * @param {(e: object) => number} dieFor  zarul: gazda își trage singură zarul
 * @returns {boolean} true dacă evenimentul a fost aplicat
 */
export function applyEvent(vm, event, dieFor = (e) => e.value) {
  const dialog = vm.activeDialog;
  const current = vm.currentPlayer;

  // Datornicul lichidează chiar dacă nu e la rând (ex. contribuție TELEX primită de la el).
  const isDebtorAction = (event.t === EV.SELL || event.t === EV.BANKRUPT);
  if (isDebtorAction) {
    if (dialog?.type !== 'DEBT' || dialog.debt.debtorId !== event.playerId) return false;
  } else {
    if (!current || event.playerId !== current.id) return false;
  }

  switch (event.t) {
    case EV.ROLL: {
      if (dialog || vm.turnAwaitingEnd) return false;
      vm.roll(dieFor(event));
      return true;
    }
    case EV.BUY: {
      const offer = vm.pendingOffer;
      if (!offer || vm.propertyAtTile(event.tile) !== offer.propertyId) return false;
      vm.acceptOffer();
      return true;
    }
    case EV.DECLINE: {
      const offer = vm.pendingOffer;
      if (!offer || vm.propertyAtTile(event.tile) !== offer.propertyId) return false;
      vm.refuseOffer();
      return true;
    }
    case EV.ANNEX: {
      const propId = vm.propertyAtTile(event.tile);
      if (!propId || !vm.turnAwaitingEnd || dialog) return false;
      vm.buyNextAnnex(propId);
      return true;
    }
    case EV.END: {
      if (dialog || !vm.turnAwaitingEnd) return false;
      vm.endTurn();
      return true;
    }
    case EV.RENT: {
      if (dialog?.type !== 'RENT') return false;
      vm.payRent();
      return true;
    }
    case EV.ACK: {
      if (dialog?.type === 'BANK_FEE') {
        vm.acknowledgeBankFee();
        return true;
      }
      if (dialog?.type === 'CARD') {
        vm.dismissCard();
        return true;
      }
      return false;
    }
    case EV.SELL: {
      vm.settleDebt([{ propertyId: event.propertyId, annexIndex: event.annexIndex ?? null }]);
      return true;
    }
    case EV.DRAW: {
      if (dialog?.type !== 'DRAW') return false;
      vm.drawCard();
      return true;
    }
    case EV.BANKRUPT: {
      vm.settleDebt([]);
      return true;
    }
    default:
      return false;
  }
}
