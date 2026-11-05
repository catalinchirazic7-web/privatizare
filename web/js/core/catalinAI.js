// CatalinRobot: decizia de cumpărare prin analiză pe orizont, ca la șah.
// Pentru fiecare opțiune se compară averea botului peste următoarele ture, cu distribuția reală
// a zarului (inclusiv norocul de 25%) și cu lanțul de poziții al botului, ca să se știe cât va plăti
// în chirii înainte să poată conta pe venitul din anexe și titluri.
// Clasicul rămâne în botAI.js, neschimbat.

import { calculateRent, wholeSaleValue, netWorth } from './actions.js';
import { rentOnTile, visitRate, ANNEX_KEEP } from './botAI.js';
import { CATALIN_LUCK, CATALIN_CONTROL, rollBotDie } from './dice.js';
import { TILE } from './model.js';

export { CATALIN_CONTROL };
export const CATALIN_HORIZON = 10;   // ture înainte pentru care se calculează câștigul și plățile
export const OUTLOOK_TURNS = 3;      // ture analizate exact (toate căile zarului) pentru riscul de lichiditate

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
 * Toate căile zarului pe [horizon] ture, din poziția botului: distribuția chiriilor totale plătite
 * și cheltuiala așteptată pe titluri (căsuțe libere pe care ajunge, la prețul titlului).
 * Fiecare stare e (poziție, chirie acumulată) cu probabilitatea ei; stările identice se unesc.
 */
export function pathOutlook(state, botIdx, board, actions, diceCount = 1, horizon = OUTLOOK_TURNS) {
  const me = state.players[botIdx];
  const dist = botStepDistribution(diceCount);
  let states = new Map([[`${me.position}|0`, { pos: me.position, rent: 0, p: 1 }]]);
  let titleSpend = 0;
  for (let t = 0; t < horizon; t += 1) {
    const next = new Map();
    for (const { pos, rent, p } of states.values()) {
      for (const [s, q] of Object.entries(dist)) {
        const idx = (pos + Number(s)) % board.length;
        const tile = board[idx];
        const { rent: r, ownerId } = rentOnTile(state, botIdx, board, actions, idx);
        const pp = p * q;
        if (tile.type === TILE.PROPERTY && ownerId === null && me.ownedProperties[tile.propertyId] === undefined) {
          titleSpend += pp * actions[tile.propertyId].titlePrice;
        }
        const total = rent + r;
        const key = `${idx}|${total}`;
        const cur = next.get(key);
        if (cur) cur.p += pp;
        else next.set(key, { pos: idx, rent: total, p: pp });
      }
    }
    states = next;
  }
  const rentDist = new Map();
  for (const { rent, p } of states.values()) rentDist.set(rent, (rentDist.get(rent) ?? 0) + p);
  return { rentDist, titleSpend };
}

/** Valoarea sub care rămâne cel puțin [q] din probabilitate (ex. q = 0.9: rareori se plătește mai mult). */
export function rentQuantile(rentDist, q) {
  const entries = [...rentDist.entries()].sort((a, b) => a[0] - b[0]);
  let acc = 0;
  for (const [r, p] of entries) {
    acc += p;
    if (acc >= q) return r;
  }
  return entries.length ? entries[entries.length - 1][0] : 0;
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
 * Decizia de cumpărare (la fel pentru toți roboții). Titlul: cumpără dacă are banii. Anexa: cumpără
 * doar dacă, după plată, rămâne ANNEX_KEEP (15%) din averea totală (bani + acțiuni la 80%).
 * Rezerva se recalculează la fiecare decizie, deci scade odată cu averea.
 * Anexele apar doar după titlu (nextAnnex), deci ordinea titlu → anexă e respectată.
 */
export function catalinDecideBuy({ state, botIdx, option, actions, propertyId }) {
  const me = state.players[botIdx];
  if (option.type === 'TITLE') return me.money >= actions[propertyId].titlePrice;
  if (me.annexRecovery) return false;   // a vândut anexe pentru datorie: nu mai cumpără până își reface rezerva
  const price = option.annex.price;
  if (me.money < price) return false;
  return me.money - price >= ANNEX_KEEP * netWorth(me, actions);
}

/**
 * Tot ce cântărește robotul când alege o căsuță: un titlu liber pe care și-l poate permite e bun,
 * o chirie plătită unui adversar e rău, iar o căsuță a lui sau fără efect e neutră.
 */
export function tileScore(state, botIdx, board, actions, idx) {
  const tile = board[idx];
  if (tile.type !== TILE.PROPERTY) return 0;
  const me = state.players[botIdx];
  const { rent, ownerId } = rentOnTile(state, botIdx, board, actions, idx);
  if (ownerId !== null) return -rent / 500;
  if (me.ownedProperties[tile.propertyId] !== undefined) return 0.5;
  return me.money >= actions[tile.propertyId].titlePrice ? 3 : 0.5;
}

/**
 * Zarul CatalinRobot: cu probabilitatea CATALIN_CONTROL își alege fața (sau suma celor două zaruri)
 * care duce pe cea mai bună căsuță; altfel aruncă cu noroc, ca un zar obișnuit.
 */
export function catalinDice(state, botIdx, board, actions, diceCount = 1, rng = Math.random) {
  if (rng() >= CATALIN_CONTROL) return Array.from({ length: diceCount }, () => rollBotDie(rng, CATALIN_LUCK));
  const pos = state.players[botIdx].position;
  const score = (step) => tileScore(state, botIdx, board, actions, (pos + step) % board.length);
  if (diceCount === 1) {
    let best = 1;
    for (let f = 2; f <= 6; f += 1) if (score(f) >= score(best)) best = f;   // la egalitate: fața mai mare
    return [best];
  }
  let bestSum = 2;
  for (let s = 3; s <= 12; s += 1) if (score(s) >= score(bestSum)) bestSum = s;   // la egalitate: suma mai mare
  const lo = Math.max(1, bestSum - 6);
  const hi = Math.min(6, bestSum - 1);
  const a = lo + Math.floor(rng() * (hi - lo + 1));
  return [a, bestSum - a];
}
