// Teste pentru inteligența robotului (mecanica reală: fără grupuri/monopoluri).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadRealCatalog } from './helpers.mjs';
import { TILE, PAWN_COLORS } from '../js/core/model.js';
import {
  diceSumProb, stepDistribution, analyzeSituation, safetyBuffer, utilityScore, botDecideBuy,
  botLiquidationSales, MIN_SAFETY_BUFFER,
} from '../js/core/botAI.js';

const cat = loadRealCatalog();
const board = cat.board;
const player = (i, extra = {}) => ({
  id: `p${i}`, name: `J${i}`, color: PAWN_COLORS[i].key, money: 50000, position: 0,
  ownedProperties: {}, skipTurns: 0, houseSince: 0, isBot: false, ...extra,
});

/** Botul e la 5 căsuțe în fața unei proprietăți; trei adversari, unul deține acea proprietate. */
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

test('distribuția pasului: un zar 1..6, două zaruri 2..12, ambele însumează 1', () => {
  for (const n of [1, 2]) {
    const sum = Object.values(stepDistribution(n)).reduce((a, b) => a + b, 0);
    assert.ok(Math.abs(sum - 1) < 1e-9);
  }
  assert.equal(Object.keys(stepDistribution(1)).length, 6);
  assert.equal(Object.keys(stepDistribution(2)).length, 11);
});

test('rezerva minimă e 300 când nu există chirii în față', () => {
  const { state } = scenario({ botMoney: 50000 });
  assert.equal(safetyBuffer(state, 0, board, cat.actions), MIN_SAFETY_BUFFER);
});

test('analiza situației: are banii, titlurile și expunerea', () => {
  const { state } = scenario({ botMoney: 60000, oppLevel: 0 });
  const sit = analyzeSituation(state, 0, board, cat.actions, 2);
  assert.equal(sit.cash, 60000);
  assert.equal(sit.opponents, 3);
  assert.ok(sit.exposure >= 0);
});

test('anexă: nu o cumpără dacă achiziția coboară sub rezerva de siguranță', () => {
  const { state, propIdx } = scenario({ botMoney: 0, oppLevel: 0 });
  const rent = cat.actions[board[propIdx].propertyId].titleTax;
  const buffer = Math.max(MIN_SAFETY_BUFFER, Math.round(rent * 1.25));
  const annex = cat.actions.poli.annexes[0];
  state.players[0].ownedProperties.poli = 0;
  state.players[0].money = annex.price + buffer - 100;
  const r = utilityScore({ state, botIdx: 0, board, actions: cat.actions, propertyId: 'poli', option: { type: 'ANNEX', annex } });
  assert.equal(r.hardReject, true);
  assert.match(r.reasons[0], /rezerva/);
});

test('titlu: îl încearcă dacă are bani, chiar dacă rămâne sub rezervă', () => {
  const { state, propIdx } = scenario({ botMoney: 0, oppLevel: 0 });
  const rent = cat.actions[board[propIdx].propertyId].titleTax;
  const buffer = Math.max(MIN_SAFETY_BUFFER, Math.round(rent * 1.25));
  state.players[0].money = cat.actions.poli.titlePrice + buffer - 100;
  assert.equal(botDecideBuy({ state, botIdx: 0, board, actions: cat.actions, propertyId: 'poli', option: { type: 'TITLE' } }), true);
  state.players[0].money = cat.actions.poli.titlePrice - 1;
  assert.equal(botDecideBuy({ state, botIdx: 0, board, actions: cat.actions, propertyId: 'poli', option: { type: 'TITLE' } }), false);
});

test('anexă scumpă cu puțini bani: refuzată sau penalizată', () => {
  const { state } = scenario({ botMoney: 30000 });
  const annex = cat.actions.continental.annexes[1];
  const r = utilityScore({ state, botIdx: 0, board, actions: cat.actions, propertyId: 'continental', option: { type: 'ANNEX', annex }, diceCount: 2 });
  assert.ok(r.hardReject || r.reasons.some((x) => /scumpă/.test(x)) || r.score < 100);
});

test('la datorie: vinde o acțiune întreagă (titlu + anexe) când e nevoie', () => {
  const debtor = { id: 'p0', money: 0, ownedProperties: { poli: 1, continental: 1 } };
  const state = { turn: 1, players: [debtor, player(1)], bankMoney: 100000, history: [] };
  const sales = botLiquidationSales(debtor, 1000, cat.actions, state, board, 2);
  assert.ok(sales.length >= 1);
  assert.equal(sales[0].annexIndex, null);
});

test('motorul înregistrează vizitele adversarilor și chiriile încasate pe proprietate', async () => {
  const { TurnEngine } = await import('../js/core/turnEngine.js');
  const engine = new TurnEngine(cat, () => 0.5);
  const polIdx = board.findIndex((t) => t.propertyId === 'poli');
  const owner = player(0, { ownedProperties: { poli: 0 } });
  const visitor = player(1, { position: (polIdx - 3 + board.length) % board.length, money: 50000 });
  const state = { turn: 1, players: [owner, visitor], bankMoney: 100000, history: [], stats: {} };
  const r = engine.playTurn(state, 1, [3]);
  assert.ok(r.rentDue, 'aterizarea pe POLI trebuie să creeze o chirie');
  assert.equal(r.state.stats.poli.visits.p1, 1);
  const paid = engine.payRent(r.state, r.rentDue);
  assert.equal(paid.state.stats.poli.collected, cat.actions.poli.titleTax);
});
