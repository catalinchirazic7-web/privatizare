// Modul ușor: omul și robotul ușor pot rearunca zarul, o dată pe tură.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { Game } from '../js/game/game.js';
import { PAWN_COLORS } from '../js/core/model.js';
import { isLowRoll } from '../js/core/dice.js';
import { loadRealCatalog } from './helpers.mjs';

const cat = loadRealCatalog();

test('aruncarea e mică dacă media pe zar e cel mult 2', () => {
  assert.equal(isLowRoll([1]), true);
  assert.equal(isLowRoll([2]), true);
  assert.equal(isLowRoll([3]), false);
  assert.equal(isLowRoll([1, 3]), true);
  assert.equal(isLowRoll([3, 3]), false);
});

test('robotul ușor își rearunca zarul o dată pe tură', () => {
  const queue = [1, 5, 2];
  const game = new Game(cat, { botDie: () => queue.shift() });
  game.startGame([
    { name: 'Om', colorKey: PAWN_COLORS[0].key, isBot: false, catalin: false, easy: false },
    { name: 'Robot', colorKey: PAWN_COLORS[1].key, isBot: true, catalin: false, easy: true },
  ], null, null, 1, 'old');
  const robot = game.game.players[1];
  assert.deepEqual(game.easyBotRoll(robot), [5]);   // 1 e mic: rearuncă și iese 5
  assert.deepEqual(game.easyBotRoll(robot), [2]);   // deja a rearuncat în tura asta: rămâne 2
});

test('în modul ușor e activat, fără robot ușor nu', () => {
  const game = new Game(cat, { botDie: () => 3 });
  game.startGame([
    { name: 'Om', colorKey: PAWN_COLORS[0].key, isBot: false, catalin: false, easy: false },
    { name: 'Robot', colorKey: PAWN_COLORS[1].key, isBot: true, catalin: false, easy: false },
  ], null, null, 1, 'old');
  assert.equal(game.easyMode, false);
  game.startGame([
    { name: 'Om', colorKey: PAWN_COLORS[0].key, isBot: false, catalin: false, easy: false },
    { name: 'Robot', colorKey: PAWN_COLORS[1].key, isBot: true, catalin: false, easy: true },
  ], null, null, 1, 'old');
  assert.equal(game.easyMode, true);
});
