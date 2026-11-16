import { test } from 'node:test';
import assert from 'node:assert/strict';
import { Game } from '../js/game/game.js';
import { GameScreen } from '../js/game/gameScreen.js';
import { loadRealCatalog } from './helpers.mjs';

test('orchestrarea automată lasă robotul să arunce între turele de odihnă', () => {
 const originalSet = globalThis.setTimeout, originalClear = globalThis.clearTimeout;
 let next = 0; const scheduled = new Map();
 globalThis.setTimeout = (fn) => { const id = ++next; scheduled.set(id, fn); return id; };
 globalThis.clearTimeout = id => scheduled.delete(id);
 try {
  const g = new Game(loadRealCatalog(), { botDie: () => 1, rng: () => .5 });
  g.startGame([{name:'Ana',colorKey:'ROSU'}, {name:'Robot',colorKey:'ALBASTRU',isBot:true}], null, null, 1, 'new', true);
  const screen = Object.create(GameScreen.prototype);
  Object.assign(screen,{active:true,game:g,pawnsBusy:false,die:{busy:false},rollHold:null,botKey:null,endKey:null,botTimer:null,endTimer:null});
  g.onChange(() => screen.pump());
  g.game.players[0].position = 28; g.roll(1);
  const seen = [];
  for (let i=0; i<40 && seen.length<2; i++) {
    const entry=scheduled.entries().next().value;
    assert.ok(entry, 'există o acțiune programată');
    scheduled.delete(entry[0]); entry[1]();
    if (g.currentPlayer.name==='Ana' && g.skipBanner && seen.length < 2 && !seen.includes(g.game.players[0].skipTurns)) {
      seen.push(g.game.players[0].skipTurns);
      assert.ok(g.rollNonce >= seen.length + 1, 'robotul a aruncat înaintea fiecărei ture de odihnă');
    }
  }
  assert.deepEqual(seen,[1,0]);
 } finally { globalThis.setTimeout=originalSet; globalThis.clearTimeout=originalClear; }
});
