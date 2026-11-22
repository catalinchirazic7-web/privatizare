import { test } from 'node:test';
import assert from 'node:assert/strict';
import { Game } from '../js/game/game.js';
import { SnapshotCodec } from '../js/online/protocol.js';
import { loadRealCatalog } from './helpers.mjs';

test('partida reluata pastreaza tabla noua si regulile alese', () => {
  const g = new Game(loadRealCatalog());
  g.startGame([{name:'Ana',colorKey:'ROSU'}, {name:'Robot',colorKey:'ALBASTRU',isBot:true,easy:true}], null, null, 2, 'new');
  g.rerolled = true;
  const restored = new Game(loadRealCatalog());
  restored.restore(SnapshotCodec.decode(SnapshotCodec.encode(g.snapshot())), null);
  assert.equal(restored.boardLayout, 'new');
  assert.equal(restored.diceCount, 2);
  assert.equal(restored.easyMode, true);
  assert.equal(restored.rerolled, true);
});
