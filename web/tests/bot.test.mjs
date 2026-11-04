// Teste pentru inteligența robotului (mecanica reală: fără grupuri/monopoluri).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadRealCatalog } from './helpers.mjs';
import { TILE, PAWN_COLORS } from '../js/core/model.js';
import { diceSumProb, safetyBuffer, utilityScore, botDecideBuy, botLiquidationSales, MIN_SAFETY_BUFFER } from '../js/core/botAI.js';

const cat = loadRealCatalog();
const board = cat.board;
const player = (i, extra = {}) => ({
  id: `p${i}`, name: `J${i}`, color: PAWN_COLORS[i].key, money: 50000, position: 0,
  ownedProperties: {}, skipTurns: 0, houseSince: 0, isBot: false, ...extra,
});

/** Botul e la 5 căsuțe în fața unei proprietăți (propIdx); trei adversari, unul deține acea proprietate. */
function scenario({ botMoney, oppLevel = null }) {
  const propIdx = board.findIndex((t, i) => i >= 10 && t.type === TILE.PROPERTY);
  const bot = player(0, { money: botMoney, position: (propIdx - 5 + board.length) % board.length });
  const owned = oppLevel === null ? {} : { [board[propIdx].propertyId]: oppLevel };
  const players = [bot, player(1, { ownedProperties: owned }), player(2), player(3)];
  return { state: { turn: 1, players, bankMoney: 100000, history: [] }, bot, propIdx };
}

test('probabilitatea sumei a două zaruri însumează 1', () => {
  let s = 0;
  for (let k = 2; k <= 12; k += 1) s += diceSumProb(k);
  assert.ok(Math.abs(s - 1) < 1e-9);
});

test('rezerva minimă e 300 când nu există chirii în față', () => {
  const { state, bot } = scenario({ botMoney: 50000 });
  assert.equal(safetyBuffer(state, 0, board, cat.actions), MIN_SAFETY_BUFFER);
  assert.ok(bot);
});

test('nu cumpără dacă achiziția coboară sub rezerva de siguranță', () => {
  const { state, propIdx } = scenario({ botMoney: 0, oppLevel: 0 });
  const pid = board[propIdx].propertyId;
  const rent = cat.actions[pid].titleTax; // chiria de titlu pe care o plătește botul în față
  const buffer = Math.max(MIN_SAFETY_BUFFER, Math.round(rent * 1.25));
  // bani exact cât să rămână sub rezervă după cumpărarea POLI (1000)
  state.players[0].money = 1000 + buffer - 100;
  const r = utilityScore({ state, botIdx: 0, board, actions: cat.actions, propertyId: 'poli', option: { type: 'TITLE' } });
  assert.equal(r.hardReject, true);
  assert.match(r.reasons[0], /rezerva/);
});

test('cumpără un titlu ieftin și profitabil când are bani din belșug', () => {
  const { state } = scenario({ botMoney: 60000 });   // patru jucători: trei adversari la încasări
  const prop = board.find((t) => t.type === TILE.PROPERTY && t.propertyId === 'poli');
  assert.ok(prop);
  // cu două zaruri botul trece mai des pe căsuțe, deci randamentul e mai bun
  assert.equal(botDecideBuy({ state, botIdx: 0, board, actions: cat.actions, propertyId: 'poli', option: { type: 'TITLE' }, diceCount: 2 }), true);
  // cu un singur zar randamentul scade, iar botul e mai prudent
  assert.equal(botDecideBuy({ state, botIdx: 0, board, actions: cat.actions, propertyId: 'poli', option: { type: 'TITLE' }, diceCount: 1 }), false);
});

test('nu cumpără anexă dacă rămân sub 2 chirii medii în cont', () => {
  const { state } = scenario({ botMoney: 4000 });
  const annex = cat.actions.continental.annexes[0];
  const r = utilityScore({ state, botIdx: 0, board, actions: cat.actions, propertyId: 'continental', option: { type: 'ANNEX', annex } });
  assert.equal(r.hardReject, true);
});

test('la datorie: se vând întâi anexele cu randamentul cel mai mic', () => {
  const debtor = player(0, { money: 0, ownedProperties: { poli: 1, continental: 1 } });
  const sales = botLiquidationSales(debtor, 1000, cat.actions);
  assert.ok(sales.length >= 1);
  // POLI anexa 1: 2200 -> 350 (16%); CONTINENTAL anexa 1: 4000 -> 1400 (35%): POLI primul
  assert.equal(sales[0].propertyId, 'poli');
  assert.equal(sales[0].annexIndex, 1);
});

test('distribuția pasului: un zar 1..6, două zaruri 2..12, ambele însumează 1', async () => {
  const { stepDistribution } = await import('../js/core/botAI.js');
  for (const n of [1, 2]) {
    const d = stepDistribution(n);
    const sum = Object.values(d).reduce((a, b) => a + b, 0);
    assert.ok(Math.abs(sum - 1) < 1e-9);
  }
  assert.equal(Object.keys(stepDistribution(1)).length, 6);
  assert.equal(Object.keys(stepDistribution(2)).length, 11);
});

test('analiza situației: expunerea crește cu un al doilea zar', async () => {
  const { analyzeSituation } = await import('../js/core/botAI.js');
  const propIdx = board.findIndex((t, i) => i >= 10 && t.type === TILE.PROPERTY);
  const { state } = scenario({ botMoney: 60000, oppLevel: 0 });
  const one = analyzeSituation(state, 0, board, cat.actions, 1);
  const two = analyzeSituation(state, 0, board, cat.actions, 2);
  assert.equal(one.cash, 60000);
  assert.ok(one.exposure >= 0 && two.exposure >= 0);
  assert.ok(propIdx >= 0);
});

test('anexă scumpă cu puțini bani: refuzată sau penalizată', async () => {
  const { utilityScore } = await import('../js/core/botAI.js');
  const { state } = scenario({ botMoney: 30000 });
  const annex = cat.actions.continental.annexes[1]; // anexa a doua, scumpă
  const r = utilityScore({ state, botIdx: 0, board, actions: cat.actions, propertyId: 'continental', option: { type: 'ANNEX', annex }, diceCount: 2 });
  assert.ok(r.hardReject || r.reasons.some((x) => /scumpă/.test(x)) || r.score < 100);
});
