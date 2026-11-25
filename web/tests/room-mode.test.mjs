// Modul ales de gazdă la crearea camerei ajunge în informațiile camerei și la meci (tabla, zarurile, modul rapid).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { Game } from '../js/game/game.js';
import { OnlineController } from '../js/online/controller.js';
import { MemoryDb, MemoryBackend } from './memoryBackend.mjs';
import { loadRealCatalog } from './helpers.mjs';

const cat = loadRealCatalog();

for (const [mode, dice, rapid] of [['classic', 1, false], ['dice2', 2, false], ['rapid', 1, true]]) {
  test(`modul ${mode}: gazda pornește cu setările lui, oaspetele le primește`, async () => {
    const db = new MemoryDb();
    const hostGame = new Game(cat, { botDie: () => 1 });
    const guestGame = new Game(cat, { botDie: () => 1 });
    const host = new OnlineController(new MemoryBackend(db), hostGame, { newCode: () => 'WXYZ' });
    const guest = new OnlineController(new MemoryBackend(db), guestGame, {});
    await host.createRoom('Ana', 'ROSU', mode);
    await guest.joinRoom('WXYZ', 'Dan', 'ALBASTRU');
    assert.equal(guest.info.mode, mode);

    await host.startMatch();
    for (const g of [hostGame, guestGame]) {
      assert.equal(g.boardLayout, 'new');
      assert.equal(g.diceCount, dice);
      assert.equal(g.rapidMode, rapid);
    }
  });
}
