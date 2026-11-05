// CatalinRobot: decizia de cumpărare prin analiză pe orizont, ca la șah.
// Pentru fiecare opțiune se compară averea botului peste următoarele ture, cu distribuția reală
// a zarului (inclusiv norocul de 25%) și cu lanțul de poziții al botului, ca să se știe cât va plăti
// în chirii înainte să poată conta pe venitul din anexe și titluri.
// Clasicul rămâne în botAI.js, neschimbat.

import { calculateRent, wholeSaleValue } from './actions.js';
import { rentOnTile, visitRate, safetyBuffer, MIN_SAFETY_BUFFER } from './botAI.js';
import { CATALIN_LUCK } from './dice.js';

export const CATALIN_HORIZON = 10;   // ture înainte pentru care se calculează câștigul și plățile

/** Distribuția pasului robotului: fiecare zar cu noroc (best-of-two cu șansa [luck]), suma pe [diceCount] zaruri. */
export function botStepDistribution(diceCount = 1, luck = CATALIN_LUCK) {
  const die = {};
  for (let k = 1; k <= 6; k += 1) die[k] = (1 - luck) / 6 + luck * (2 * k - 1) / 36;
  let dist = { 0: 1 };
  for (let d = 0; d < diceCount; d += 1) {
    const next = {};
    for (const [s, p] of Object.entries(dist)) {
      for (const [k, q] of Object.entries(die)) {
        const sum = Number(s) + Number(k);
        next[sum] = (next[sum] ?? 0) + p * q;
      }
    }
    dist = next;
  }
  return dist;
}

/** Chiria așteptată pe care o plătește robotul în următoarele [horizon] ture, din lanțul de poziții. */
export function expectedRentPaid(state, botIdx, board, actions, diceCount = 1, horizon = CATALIN_HORIZON) {
  const me = state.players[botIdx];
  const dist = botStepDistribution(diceCount);
  let pos = new Array(board.length).fill(0);
  pos[me.position] = 1;
  let paid = 0;
  for (let t = 0; t < horizon; t += 1) {
    const next = new Array(board.length).fill(0);
    for (let i = 0; i < board.length; i += 1) {
      if (pos[i] === 0) continue;
      for (const [s, p] of Object.entries(dist)) next[(i + Number(s)) % board.length] += pos[i] * p;
    }
    for (let i = 0; i < board.length; i += 1) paid += next[i] * rentOnTile(state, botIdx, board, actions, i).rent;
    pos = next;
  }
  return paid;
}

/**
 * Câștigul net al unei cumpărări pe orizont: venitul din chiriile adversarilor minus pierderea
 * la revânzare (20%). Pozitiv înseamnă că investiția se plătește în orizontul ales.
 */
export function horizonGain({ state, botIdx, board, actions, propertyId, option, diceCount = 1, horizon = CATALIN_HORIZON }) {
  const me = state.players[botIdx];
  const prop = actions[propertyId];
  const owned = me.ownedProperties[propertyId];
  const oldLevel = owned ?? -1;               // -1: nu are titlul
  const newLevel = option.type === 'TITLE' ? 0 : option.annex.index;
  const price = option.type === 'TITLE' ? prop.titlePrice : option.annex.price;
  const oldRent = oldLevel < 0 ? 0 : calculateRent(prop, oldLevel);
  const newRent = calculateRent(prop, newLevel);
  const oldSale = oldLevel < 0 ? 0 : wholeSaleValue(prop, oldLevel);
  const newSale = wholeSaleValue(prop, newLevel);
  const rate = visitRate(state, botIdx, board, propertyId, diceCount);
  const extraRent = (newRent - oldRent) * rate * horizon;
  const lostValue = price - (newSale - oldSale);  // ce pierde la revânzare imediat
  return { gain: extraRent - lostValue, price, extraRent, lostValue };
}

/**
 * Decizia CatalinRobot. Cumpără dacă:
 *  - are banii și, după cumpărare, rămâne peste rezerva ce acoperă chiriile așteptate pe orizont;
 *  - câștigul pe orizont este pozitiv (venitul bate pierderea la revânzare).
 * Anexele apar doar după titlu (nextAnnex), deci ordinea titlu → anexă e respectată.
 */
export function catalinDecideBuy({ state, botIdx, board, actions, propertyId, option, diceCount = 1 }) {
  const me = state.players[botIdx];
  const { gain, price } = horizonGain({ state, botIdx, board, actions, propertyId, option, diceCount });
  if (me.money < price) return false;
  const after = me.money - price;
  const reserve = Math.max(
    MIN_SAFETY_BUFFER,
    Math.round(expectedRentPaid(state, botIdx, board, actions, diceCount)),
    safetyBuffer(state, botIdx, board, actions, diceCount),
  );
  if (after < reserve) return false;
  return gain > 0;
}
