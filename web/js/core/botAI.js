// Inteligența robotului: scor de utilitate (0-100), rezervă de siguranță dinamică,
// bonus de trafic și regula anexelor. Folosește doar datele din catalog (board, actions).
//
// Mecanica reală (regulamentul „Privatizare”): nu există grupuri sau monopoluri. Se evaluează
// randamentul fiecărei acțiuni (titlu sau anexă) și traficul căsuței.

import { calculateRent, sellBackValue } from './actions.js';
import { TILE } from './model.js';

export const BUY_THRESHOLD = 50;
export const MIN_SAFETY_BUFFER = 300;
export const TRAFFIC_BONUS = 15;
export const TRAFFIC_MIN_PROB = 5 / 36;   // căsuță atinsă cu cel puțin 5/36 dintr-o aruncare
const HORIZON_TURNS = 10;                  // orizontul pentru randamentul așteptat al unei acțiuni
const BASE_SCORE = 20;
const ROI_WEIGHT = 40;
const ANNEX_MIN_AVG_RENTS = 2;            // nu investește în anexe sub 2 chirii medii în cont

/** Probabilitatea sumei a două zaruri (2..12). */
export function diceSumProb(sum) {
  if (sum < 2 || sum > 12) return 0;
  return (6 - Math.abs(7 - sum)) / 36;
}

/** Cea mai mare chirie pe care botul ar putea-o plăti în următoarea aruncare (sumele 2..12). */
export function maxPotentialRent(state, botIdx, board, actions) {
  const me = state.players[botIdx];
  let max = 0;
  for (let sum = 2; sum <= 12; sum += 1) {
    const tile = board[(me.position + sum) % board.length];
    if (tile.type !== TILE.PROPERTY) continue;
    const owner = state.players.find((p) => p.id !== me.id && p.ownedProperties[tile.propertyId] !== undefined);
    if (!owner) continue;
    const rent = calculateRent(actions[tile.propertyId], owner.ownedProperties[tile.propertyId]);
    if (rent > max) max = rent;
  }
  return max;
}

/** Rezerva minimă de siguranță: max(300, cea mai mare chirie potențială * 1.25). */
export function safetyBuffer(state, botIdx, board, actions) {
  return Math.max(MIN_SAFETY_BUFFER, Math.round(maxPotentialRent(state, botIdx, board, actions) * 1.25));
}

/** Media chiriilor de bază (doar titlu), folosită la regula anexelor. */
export function averageBaseRent(actions) {
  const props = Object.values(actions);
  return props.reduce((s, p) => s + calculateRent(p, 0), 0) / props.length;
}

/**
 * Scorul de utilitate (0-100) al cumpărării unei acțiuni.
 * Returnează { score, hardReject, reasons } ca să poată fi explicat în jurnal sau în teste.
 */
export function utilityScore({ state, botIdx, board, actions, propertyId, option }) {
  const me = state.players[botIdx];
  const prop = actions[propertyId];
  const price = option.type === 'TITLE' ? prop.titlePrice : option.annex.price;
  const reasons = [];

  if (me.money < price) return { score: 0, hardReject: true, reasons: ['fără bani'] };

  const afterMoney = me.money - price;
  if (option.type === 'ANNEX' && afterMoney < ANNEX_MIN_AVG_RENTS * averageBaseRent(actions)) {
    return { score: 0, hardReject: true, reasons: ['anexă sub 2 chirii medii în cont'] };
  }

  const opponents = state.players.filter((p) => p.id !== me.id).length;
  // Chiria încasată cândva de la adversari, cu probabilitatea unei vizite pe tură (medie 7/37 pe căsuță).
  const gainPerTurn = option.type === 'TITLE'
    ? prop.titleTax
    : option.annex.profit;
  const visitProb = 7 / board.length;
  const expectedReturn = gainPerTurn * visitProb * opponents * HORIZON_TURNS;
  const roi = expectedReturn / price;
  let score = BASE_SCORE + ROI_WEIGHT * Math.min(1, roi);
  reasons.push(`randament ${roi.toFixed(2)}`);

  const tileIndex = board.findIndex((t) => t.type === TILE.PROPERTY && t.propertyId === propertyId);
  const distance = (tileIndex - me.position + board.length) % board.length;
  if (distance >= 2 && diceSumProb(distance) >= TRAFFIC_MIN_PROB) {
    score += TRAFFIC_BONUS;
    reasons.push('trafic');
  }

  if (afterMoney < safetyBuffer(state, botIdx, board, actions)) {
    return { score: 0, hardReject: true, reasons: ['sub rezerva de siguranță'] };
  }

  return { score: Math.max(0, Math.min(100, Math.round(score))), hardReject: false, reasons };
}

/** Robotul cumpără doar dacă scorul depășește pragul. */
export function botDecideBuy({ state, botIdx, board, actions, propertyId, option }) {
  const r = utilityScore({ state, botIdx, board, actions, propertyId, option });
  return !r.hardReject && r.score >= BUY_THRESHOLD;
}

/**
 * Vânzările pentru o datorie, în ordine: anexele cele mai puțin rentabile (profit/preț),
 * apoi titlurile cu chiria cea mai mică. Se oprește când acoperă datoria.
 * Fiecare anexă se vinde de la cea mai înaltă, ca să rămână lanț valid.
 */
export function botLiquidationSales(debtor, debt, actions) {
  if (debtor.money >= debt) return [];
  let money = debtor.money;
  const levels = { ...debtor.ownedProperties };
  const sales = [];

  // 1. Anexe: alege mereu anexa de la nivelul cel mai înalt cu cel mai mic randament.
  while (money < debt) {
    let best = null;
    for (const [propId, lvl] of Object.entries(levels)) {
      if (lvl <= 0) continue;
      const annex = actions[propId].annexes.find((a) => a.index === lvl);
      const yieldRatio = annex.profit / annex.price;
      if (!best || yieldRatio < best.yieldRatio) best = { propId, lvl, annex, yieldRatio };
    }
    if (!best) break;
    sales.push({ propertyId: best.propId, annexIndex: best.lvl });
    money += sellBackValue(best.annex.price);
    levels[best.propId] -= 1;
  }

  // 2. Titluri: cele cu chiria cea mai mică întâi (cele izolate, fără monopol).
  const titles = Object.keys(levels)
    .filter((propId) => levels[propId] === 0)
    .sort((a, b) => actions[a].titleTax - actions[b].titleTax);
  for (const propId of titles) {
    if (money >= debt) break;
    sales.push({ propertyId: propId, annexIndex: null });
    money += sellBackValue(actions[propId].titlePrice);
  }
  return sales;
}
