import test from 'node:test';
import assert from 'node:assert/strict';
import { BoardView } from '../js/game/board.js';
import { tileHint } from '../js/game/visuals.js';

 test('banca afișează suma încasată', () => {
  assert.equal(tileHint({ type: 'BANK' }), 'Primești 5.000 ECU de la bancă.');
});

test('pionul parcurge căsuțele continuu într-o singură animație', async () => {
  const originalRaf = globalThis.requestAnimationFrame;
  const frames = [];
  globalThis.requestAnimationFrame = (callback) => frames.push(callback);
  try {
    const board = Object.create(BoardView.prototype);
    board.running = true;
    let steps = 0;
    board.onStep = () => steps++;
    const pawn = { v: 2, token: 1 };
    const start = performance.now();
    const finished = board.walk(pawn, 3, 1);
    for (const offset of [215, 430, 645, 860, 1075, 1291]) {
      assert.equal(frames.length, 1);
      frames.shift()(start + offset);
      if (offset === 645) assert.ok(pawn.v > 3 && pawn.v < 4);
    }
    assert.equal(await finished, true);
    assert.equal(pawn.v, 5);
    assert.equal(steps, 3);
  } finally {
    globalThis.requestAnimationFrame = originalRaf;
  }
});
