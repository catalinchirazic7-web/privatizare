import { test } from 'node:test';
import assert from 'node:assert/strict';
import { Game } from '../js/game/game.js';
import { SnapshotCodec } from '../js/online/protocol.js';
import { loadRealCatalog } from './helpers.mjs';

for (const layout of ['old', 'new']) {
  for (const resume of [false, true]) {
    test(`Casa de odihnă: robotul joacă între turele pierdute (${layout}, reluat=${resume})`, () => {
      let game = new Game(loadRealCatalog(), { botDie: () => 1, rng: () => .5 });
      game.startGame([{name:'Ana',colorKey:'ROSU'}, {name:'Robot',colorKey:'ALBASTRU',isBot:true}], null, null, 1, layout, true);
      game.game.players[0].position = 28;
      game.roll(1);
      assert.equal(game.game.players[0].skipTurns, 2);
      game.endTurn();
      if (resume) {
        const saved = SnapshotCodec.decode(SnapshotCodec.encode(game.snapshot()));
        game = new Game(loadRealCatalog(), { botDie: () => 1, rng: () => .5 });
        game.restore(saved, null);
      }
      for (let turn = 1; turn <= 2; turn++) {
        const rollsBefore = game.rollNonce;
        for (let action = 0; game.currentPlayer.isBot && action < 20; action++) game.botNextAction();
        assert.ok(game.rollNonce > rollsBefore, 'robotul trebuie să arunce înaintea fiecărei ture pierdute');
        assert.equal(game.currentPlayer.name, 'Ana');
        assert.equal(game.game.players[0].position, 29);
        assert.equal(game.game.players[0].skipTurns, 2 - turn);
        assert.equal(game.turnAwaitingEnd, true);
        game.endTurn();
      }
    });
  }
}
