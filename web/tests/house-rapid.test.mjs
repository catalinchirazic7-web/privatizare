import { test } from 'node:test';
import assert from 'node:assert/strict';
import { Game } from '../js/game/game.js';
import { SnapshotCodec } from '../js/online/protocol.js';
import { loadRealCatalog } from './helpers.mjs';

test('mod rapid: Casa de odihna pastreaza doua ture inclusiv dupa salvare', () => {
  let game = new Game(loadRealCatalog());
  game.startGame([{name:'Ana',colorKey:'ROSU'}, {name:'Robot',colorKey:'ALBASTRU',isBot:true}], null, null, 1, 'new', true);
  game.game.players[0].position = 28;
  game.roll(1);
  assert.equal(game.game.players[0].position, 29);
  assert.equal(game.game.players[0].skipTurns, 2);
  game.endTurn();
  let saved = SnapshotCodec.decode(SnapshotCodec.encode(game.snapshot()));
  game = new Game(loadRealCatalog()); game.restore(saved, null);
  for (let turn = 1; turn <= 2; turn++) {
    game.turnAwaitingEnd = true; game.endTurn();
    assert.equal(game.currentIndex, 0);
    assert.equal(game.game.players[0].position, 29);
    assert.equal(game.game.players[0].skipTurns, 2 - turn);
    assert.equal(game.turnAwaitingEnd, true);
    game.endTurn();
  }
  game.turnAwaitingEnd = true; game.endTurn();
  assert.equal(game.currentIndex, 0);
  assert.equal(game.turnAwaitingEnd, false);
});
