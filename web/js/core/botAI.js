// Inteligența robotului. Analizează situația (poziție, acțiuni deținute, bani, raza de aruncare
// a zarelor, chiriile adversarilor din față) și apoi decide cumpărarea sau vânzarea.
// Mecanica reală a „Privatizării”: fără grupuri/monopoluri. Un zar sau două (suma lor = pasul).

import { calculateRent, sellBackValue } from './actions.js';
import { TILE } from './model.js';

export const BUY_THRESHOLD = 50;
export const MIN_SAFETY_BUFFER = 300;
export const TRAFFIC_BONUS = 15;
const HORIZON_TURNS = 10;      // orizontul pentru randamentul așteptat al unei acțiuni
const BASE_SCORE = 20;
const ROI_WEIGHT = 40;
const ANNEX_MIN_AVG_RENTS = 2; // nu investește în anexe sub 2 chirii medii în cont
const CASH_PRESSURE_PENALTY = 15;
const EXPENSIVE_ANNEX_SHARE = 0.4; // o anexă care ia peste 40% din bani e scumpă pentru bot

/* ---------- Zaruri ---------- */

/** Probabilitatea sumei a două zaruri (2..12). */
export function diceSumProb(sum) {
  if (sum < 2 || sum > 12) return 0;
  return (6 - Math.abs(7 - sum)) / 36;
}

/** Distribuția pasului: { pas: probabilitate } pentru un zar (1..6) sau două (2..12). */
export function stepDistribution(diceCount) {
  const dist = {};
  if (diceCount === 2) {
    for (let s = 2; s <= 12; s += 1) dist[s] = diceSumProb(s);
  } else {
    for (let s = 1; s <= 6; s += 1) dist[s] = 1 / 6;
  }
  return dist;
}

/** Pasul mediu: 3.5 pentru un zar, 7 pentru două. */
export function expectedSteps(diceCount) {
  return diceCount === 2 ? 7 : 3.5;
}

/* ---------- Analiza situației ---------- */

/** Chiria pe care o plătește cine intră pe căsuța [tileIndex] (0 dacă nu e plătibilă). */
function rentOnTile(state, botIdx, board, actions, tileIndex) {
  const tile = board[tileIndex];
  if (tile.type !== TILE.PROPERTY) return { rent: 0, ownerId: null };
  const me = state.players[botIdx];
  const owner = state.players.find((p) => p.id !== me.id && p.ownedProperties[tile.propertyId] !== undefined);
  if (!owner) return { rent: 0, ownerId: null };
  return { rent: calculateRent(actions[tile.propertyId], owner.ownedProperties[tile.propertyId]), ownerId: owner.id };
}

/** Cea mai mare chirie pe care botul o poate plăti în raza aruncării. */
export function maxPotentialRent(state, botIdx, board, actions, diceCount = 1) {
  const me = state.players[botIdx];
  let max = 0;
  for (const step of Object.keys(stepDistribution(diceCount))) {
    const idx = (me.position + Number(step)) % board.length;
    max = Math.max(max, rentOnTile(state, botIdx, board, actions, idx).rent);
  }
  return max;
}

/** Rezerva minimă: max(300, cea mai mare chirie potențială * 1.25). */
export function safetyBuffer(state, botIdx, board, actions, diceCount = 1) {
  return Math.max(MIN_SAFETY_BUFFER, Math.round(maxPotentialRent(state, botIdx, board, actions, diceCount) * 1.25));
}

/** Media chiriilor de bază (doar titlu). */
export function averageBaseRent(actions) {
  const props = Object.values(actions);
  return props.reduce((s, p) => s + calculateRent(p, 0), 0) / props.length;
}

/**
 * Situația botului, calculată o dată per decizie:
 * bani, poziție, titluri și anexe deținute, expunerea (chiria așteptată la următoarea aruncare),
 * rezerva de siguranță, chiria medie și numărul de adversari.
 */
export function analyzeSituation(state, botIdx, board, actions, diceCount = 1) {
  const me = state.players[botIdx];
  let exposure = 0;
  for (const [step, prob] of Object.entries(stepDistribution(diceCount))) {
    const idx = (me.position + Number(step)) % board.length;
    exposure += prob * rentOnTile(state, botIdx, board, actions, idx).rent;
  }
  return {
    cash: me.money,
    position: me.position,
    titles: Object.keys(me.ownedProperties).length,
    annexes: Object.values(me.ownedProperties).reduce((s, lvl) => s + lvl, 0),
    exposure: Math.round(exposure),
    buffer: safetyBuffer(state, botIdx, board, actions, diceCount),
    avgRent: averageBaseRent(actions),
    opponents: state.players.filter((p) => p.id !== me.id).length,
  };
}

/* ---------- Decizia de cumpărare ---------- */

/**
 * Scorul (0-100) al unei cumpărări, cu motivele. hardReject=true înseamnă refuz fără discuție.
 * option: { type: 'TITLE' } sau { type: 'ANNEX', annex }.
 */
export function utilityScore({ state, botIdx, board, actions, propertyId, option, diceCount = 1 }) {
  const me = state.players[botIdx];
  const prop = actions[propertyId];
  const price = option.type === 'TITLE' ? prop.titlePrice : option.annex.price;
  const sit = analyzeSituation(state, botIdx, board, actions, diceCount);
  const reject = (why) => ({ score: 0, hardReject: true, reasons: [why], situation: sit });

  if (me.money < price) return reject('fără bani');
  const after = me.money - price;
  if (after < sit.buffer) return reject('sub rezerva de siguranță');
  if (option.type === 'ANNEX' && after < ANNEX_MIN_AVG_RENTS * sit.avgRent) {
    return reject('anexă sub 2 chirii medii în cont');
  }

  const reasons = [];

  // Randament: câștigul de la fiecare adversar care trece pe căsuță, în orizontul de câteva ture.
  const gainPerVisit = option.type === 'TITLE' ? prop.titleTax : option.annex.profit;
  const visitProb = expectedSteps(diceCount) / board.length;
  const roi = (gainPerVisit * visitProb * sit.opponents * HORIZON_TURNS) / price;
  let score = BASE_SCORE + ROI_WEIGHT * Math.min(1, roi);
  reasons.push(`randament ${roi.toFixed(2)}`);

  // Trafic: cât de probabil e să pice pe căsuța asta la următoarea aruncare a botului.
  const tileIndex = board.findIndex((t) => t.type === TILE.PROPERTY && t.propertyId === propertyId);
  const distance = (tileIndex - me.position + board.length) % board.length;
  const dist = stepDistribution(diceCount);
  const reachProb = dist[distance] ?? 0;
  if (reachProb > 0) {
    score += Math.round(TRAFFIC_BONUS * (reachProb / Math.max(...Object.values(dist))));
    reasons.push(`trafic ${(reachProb * 100).toFixed(0)}%`);
  }

  // Lichiditate: după cumpărare, banii rămași sunt mici față de expunerea la chirie.
  if (after < 2 * sit.exposure) {
    score -= CASH_PRESSURE_PENALTY;
    reasons.push('lichiditate sub presiune');
  }

  // Anexă scumpă față de banii pe care îi are: se preferă cele ieftine și rentabile.
  if (option.type === 'ANNEX' && price > EXPENSIVE_ANNEX_SHARE * me.money) {
    const penalty = Math.min(30, Math.round((price / me.money - EXPENSIVE_ANNEX_SHARE) * 60));
    score -= penalty;
    reasons.push(`anexă scumpă (-${penalty})`);
  }

  return { score: Math.max(0, Math.min(100, Math.round(score))), hardReject: false, reasons, situation: sit };
}

/** Robotul cumpără doar dacă scorul depășește pragul și nu există refuz ferm. */
export function botDecideBuy(args) {
  const r = utilityScore(args);
  return !r.hardReject && r.score >= BUY_THRESHOLD;
}

/* ---------- Vânzare la datorie ---------- */

/**
 * Vânzările pentru o datorie: întâi anexele cu randamentul cel mai mic (profit/preț),
 * apoi titlurile cu chiria cea mai mică. Fiecare anexă se vinde de la nivelul cel mai înalt.
 */
export function botLiquidationSales(debtor, debt, actions) {
  if (debtor.money >= debt) return [];
  let money = debtor.money;
  const levels = { ...debtor.ownedProperties };
  const sales = [];

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
