// CatalinRobot: zarul robotului cu noroc (best-of-two cu șansa CATALIN_LUCK) și decizia pe orizont.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { rollDie, rollBotDie, CATALIN_LUCK } from '../js/core/dice.js';
import { botStepDistribution, horizonGain, catalinDecideBuy, expectedRentPaid, catalinDice } from '../js/core/catalinAI.js';
import { ANNEX_KEEP } from '../js/core/botAI.js';
import { loadRealCatalog } from './helpers.mjs';
import { PAWN_COLORS, TILE } from '../js/core/model.js';

const cat = loadRealCatalog();
const board = cat.board;
const playerAt = (i, extra = {}) => ({
  id: `p${i}`, name: `J${i}`, color: PAWN_COLORS[i].key, money: 50000, position: 0,
  ownedProperties: {}, skipTurns: 0, houseSince: 0, isBot: false, ...extra,
});
/** Robotul (p0) stă la o căsuță de proprietatea [propIdx]; ceilalți trei adversari sunt liberi. */
function state(botMoney, extraBot = {}) {
  const bot = playerAt(0, { money: botMoney, ...extraBot });
  return { turn: 1, players: [bot, playerAt(1), playerAt(2), playerAt(3)], bankMoney: 100000, history: [], stats: {} };
}
const firstProperty = board.findIndex((t) => t.type === TILE.PROPERTY);
const propId = board[firstProperty].propertyId;
const titleOption = { type: 'TITLE' };

/** Generator determinist (LCG), ca testele să fie reproductibile. */
function seeded(seed) {
  let s = seed >>> 0;
  return () => {
    s = (Math.imul(s, 1664525) + 1013904223) >>> 0;
    return s / 4294967296;
  };
}

/** Probabilitatea feței k cu zarul cu noroc: (1-p)/6 + p·(2k-1)/36. */
const expectedFace = (k) => (1 - CATALIN_LUCK) / 6 + CATALIN_LUCK * (2 * k - 1) / 36;

test('CATALIN_LUCK este 25%', () => {
  assert.equal(CATALIN_LUCK, 0.25);
});

test('fără noroc, rollBotDie dă exact aceleași fețe ca rollDie', () => {
  const a = seeded(7);
  const b = seeded(7);
  for (let i = 0; i < 1000; i += 1) {
    assert.equal(rollBotDie(a, 0), rollDie(b));
  }
});

test('fețele rămân în 1..6', () => {
  const rng = seeded(11);
  for (let i = 0; i < 10000; i += 1) {
    const v = rollBotDie(rng, CATALIN_LUCK);
    assert.ok(v >= 1 && v <= 6, `față invalidă ${v}`);
  }
});

test('distribuția pasului robotului: suma 1, un zar și două zaruri', () => {
  const one = botStepDistribution(1);
  const sum1 = Object.values(one).reduce((a, b) => a + b, 0);
  assert.ok(Math.abs(sum1 - 1) < 1e-9);
  assert.ok(Math.abs(one[6] - expectedFace(6)) < 1e-9);
  const two = botStepDistribution(2);
  const sum2 = Object.values(two).reduce((a, b) => a + b, 0);
  assert.ok(Math.abs(sum2 - 1) < 1e-9);
  assert.ok(Math.min(...Object.keys(two).map(Number)) === 2 && Math.max(...Object.keys(two).map(Number)) === 12);
});

test('fără bani suficienți, CatalinRobot refuză', () => {
  const s = state(10);
  assert.equal(catalinDecideBuy({ state: s, botIdx: 0, board, actions: cat.actions, propertyId: propId, option: titleOption }), false);
});

test('titlul: cumpără orice titlu pe care ajunge, chiar cu bani puțini rămași', () => {
  const price = cat.actions[propId].titlePrice;
  const s = state(price + 1);
  assert.equal(catalinDecideBuy({ state: s, botIdx: 0, board, actions: cat.actions, propertyId: propId, option: titleOption }), true);
});

test('anexa: rămâne rezerva (chiriile așteptate pe orizont) și câștigul pe orizont', () => {
  const prop = cat.actions[propId];
  const annex = prop.annexes[0];
  const s = state(annex.price + 1);
  const annexOption = { type: 'ANNEX', annex };
  assert.equal(catalinDecideBuy({ state: s, botIdx: 0, board, actions: cat.actions, propertyId: propId, option: annexOption }), false);
});

test('chiria așteptată plătită de robot este nenegativă și zero fără proprietăți ale adversarilor', () => {
  const s = state(1e9);
  assert.equal(expectedRentPaid(s, 0, board, cat.actions), 0);
});

test('distribuția fețelor cu noroc se potrivește cu formula (200k aruncări)', () => {
  const rng = seeded(12345);
  const N = 200000;
  const counts = [0, 0, 0, 0, 0, 0, 0];
  for (let i = 0; i < N; i += 1) counts[rollBotDie(rng, CATALIN_LUCK)] += 1;
  let mean = 0;
  for (let k = 1; k <= 6; k += 1) {
    const freq = counts[k] / N;
    assert.ok(Math.abs(freq - expectedFace(k)) < 0.005, `fața ${k}: ${freq} vs ${expectedFace(k)}`);
    mean += k * freq;
  }
  // pasul mediu: 3.5 la zar normal, ~3.74 cu noroc
  assert.ok(Math.abs(mean - 3.743) < 0.02, `media ${mean}`);
});

test('anexa: cumpără cât timp rămâne cel puțin ANNEX_KEEP din banii de acum', () => {
  const prop = cat.actions[propId];
  const annex = prop.annexes[0];
  const money = Math.ceil(annex.price / (1 - ANNEX_KEEP)) + 10;   // după cumpărare rămân peste ANNEX_KEEP
  const ok = catalinDecideBuy({ state: state(money), botIdx: 0, board, actions: cat.actions, propertyId: propId, option: { type: 'ANNEX', annex } });
  assert.equal(ok, true);
  const tight = catalinDecideBuy({ state: state(annex.price + 1), botIdx: 0, board, actions: cat.actions, propertyId: propId, option: { type: 'ANNEX', annex } });
  assert.equal(tight, false);
});

test('anexa: exemplele din cerință (20.000 cu anexă de 12.000 da; 10.000 cu anexă de 7.700 nu)', () => {
  const annexOf = (id, index) => cat.actions[id].annexes.find((a) => a.index === index);
  const decide = (propertyId, money, annex) => catalinDecideBuy({
    state: state(money), botIdx: 0, board, actions: cat.actions, propertyId, option: { type: 'ANNEX', annex },
  });
  assert.equal(decide('continental', 20000, annexOf('continental', 3)), true);
  assert.equal(decide('piata_romana', 10000, annexOf('piata_romana', 3)), false);
});

test('zarul CatalinRobot: fețe sau sume valide, cu control și fără', () => {
  const s = state(50000);
  const one = [0.1, 0.99].map((r) => catalinDice(s, 0, board, cat.actions, 1, seeded(Math.round(r * 1000))));
  for (const d of one) {
    assert.equal(d.length, 1);
    assert.ok(d[0] >= 1 && d[0] <= 6);
  }
  const two = catalinDice(s, 0, board, cat.actions, 2, seeded(3));
  assert.equal(two.length, 2);
  assert.ok(two.every((v) => v >= 1 && v <= 6));
});
