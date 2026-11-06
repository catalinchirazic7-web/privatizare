// Robotul ușor: omul are noroc 33% la zar, robotul ușor are 0.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { rollBotDie, EASY_HUMAN_LUCK } from '../js/core/dice.js';

/** Generator determinist (mulberry32), ca testul să nu depindă de Math.random. */
function seeded(seed) {
  let a = seed;
  return () => {
    a |= 0; a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function mean(luck, n = 60000) {
  const rng = seeded(42);
  let sum = 0;
  for (let i = 0; i < n; i += 1) sum += rollBotDie(rng, luck);
  return sum / n;
}

test('noroc omului în robotul ușor: 0,33', () => {
  assert.equal(EASY_HUMAN_LUCK, 0.33);
});

test('fără noroc zarul e uniform (media ~3,5)', () => {
  assert.ok(Math.abs(mean(0) - 3.5) < 0.05);
});

test('cu noroc 0,33 media urcă (cel mai bun din două aruncări, ~3,8)', () => {
  const lucky = mean(EASY_HUMAN_LUCK);
  assert.ok(lucky > mean(0) + 0.2, `medie ${lucky}`);
  assert.ok(Math.abs(lucky - 3.82) < 0.05, `medie ${lucky}`);
});

test('noroc 0 dă exact aceeași secvență ca zarul normal', () => {
  const a = seeded(7);
  const b = seeded(7);
  for (let i = 0; i < 1000; i += 1) assert.equal(rollBotDie(a, 0), 1 + Math.floor(b() * 6));
});
