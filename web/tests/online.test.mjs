// Flux online complet, cu backend în memorie: cameră, intrare, pornire, intenții și stare publicată.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { Game } from '../js/game/game.js';
import { OnlineController } from '../js/online/controller.js';
import { EV, RoomStage } from '../js/online/protocol.js';
import { MemoryDb, MemoryBackend } from './memoryBackend.mjs';
import { loadRealCatalog } from './helpers.mjs';

const cat = loadRealCatalog();

function setup(db) {
  const hostGame = new Game(cat, { botDie: () => 1 });
  const guestGame = new Game(cat, { botDie: () => 1 });
  const host = new OnlineController(new MemoryBackend(db), hostGame, { hostDie: () => 3, newCode: () => 'ABCD' });
  const guest = new OnlineController(new MemoryBackend(db), guestGame, {});
  return { host, guest, hostGame, guestGame };
}

test('creare, intrare cu culoare luată, pornire și stare publicată', async () => {
  const db = new MemoryDb();
  const { host, guest, hostGame, guestGame } = setup(db);

  await host.createRoom('Ana', 'ROSU');
  assert.equal(host.code, 'ABCD');
  assert.equal(host.isHost, true);

  await guest.joinRoom('abcd', 'Bogdan', 'ROSU');          // culoarea e luată
  assert.match(guest.error, /deja luat/);
  assert.equal(guest.isInRoom, false);
  await guest.joinRoom('ABCD', 'Bogdan', 'ALBASTRU');
  assert.equal(guest.me.id, 'p1');
  assert.equal(guest.isHost, false);

  host.startMatch();
  assert.equal(host.inGame, true);
  assert.equal(hostGame.isOnline, true);
  assert.equal(hostGame.isGuest, false);

  // Oaspetele primește starea oficială
  assert.equal(guest.inGame, true);
  assert.equal(guestGame.isGuest, true);
  assert.equal(guestGame.game.players.length, 2);
  assert.equal(guestGame.currentPlayer.id, 'p0');
});

test('oaspetele nu poate juca la rândul gazdei; gazda rulează roboții și zarul', async () => {
  const db = new MemoryDb();
  const { host, guest, hostGame, guestGame } = setup(db);
  await host.createRoom('Ana', 'ROSU');
  await guest.joinRoom('ABCD', 'Bogdan', 'ALBASTRU');
  host.startMatch();

  // Oaspetele încearcă să arunce zarul când e rândul gazdei: gazda respinge intenția
  guestGame.roll(6);
  assert.equal(hostGame.currentPlayer.position, -1, 'poziția nu s-a schimbat');
  assert.equal(guestGame.game.players[0].position, -1);

  // Gazda aruncă; oaspetele vede starea oficială (zarul gazdei, nu cel trimis)
  hostGame.roll(2);
  assert.equal(hostGame.lastDice[0], 2);
  assert.equal(guestGame.lastDice[0], 2);
  assert.equal(guestGame.game.players[0].position, 1);
});

test('oaspetele aruncă la rândul lui: gazda folosește zarul propriu', async () => {
  const db = new MemoryDb();
  const { host, guest, hostGame, guestGame } = setup(db);
  await host.createRoom('Ana', 'ROSU');
  await guest.joinRoom('ABCD', 'Bogdan', 'ALBASTRU');
  host.startMatch();

  hostGame.roll(1);            // gazda pe CONTINENTAL -> oferta de cumpărare
  assert.equal(hostGame.activeDialog.type, 'BUY');
  guestGame.acceptOffer();     // oaspetele nu e la rând: respins
  assert.equal(hostGame.activeDialog.type, 'BUY');

  hostGame.refuseOffer();      // -> taxa Băncii
  hostGame.acknowledgeBankFee();
  hostGame.endTurn();          // -> rândul oaspetelui
  assert.equal(hostGame.currentPlayer.id, 'p1');
  assert.equal(guestGame.currentPlayer.id, 'p1');

  guestGame.roll(6);           // gazda aruncă 3 (hostDie), nu 6
  assert.equal(hostGame.lastDice[0], 3);
  assert.equal(guestGame.game.players[1].position, 2, 'p1 a ajuns pe DURA, nu pe poziția 6');
  assert.equal(guestGame.activeDialog.type, 'BUY');
});

test('ieșirea din cameră curăță ascultătorii', async () => {
  const db = new MemoryDb();
  const { host, guest, guestGame } = setup(db);
  await host.createRoom('Ana', 'ROSU');
  await guest.joinRoom('ABCD', 'Bogdan', 'ALBASTRU');
  host.startMatch();
  guest.leave();
  assert.equal(guest.code, null);
  assert.equal(guestGame.isOnline, false);
  assert.equal(guest.inGame, false);
});

test('camera inexistentă și codul invalid', async () => {
  const db = new MemoryDb();
  const { guest } = setup(db);
  await guest.joinRoom('ZZZZ', 'X', 'ROSU');
  assert.equal(guest.error, 'Camera nu există.');
  await guest.joinRoom('AB', 'X', 'ROSU');
  assert.match(guest.error, /4 caractere/);
});

test('gazda pornește cu roboți: robotul joacă singur pe gazdă', async () => {
  const db = new MemoryDb();
  const { host, guest, hostGame } = setup(db);
  await host.createRoom('Ana', 'ROSU');
  await host.backend.addBot('ABCD');
  assert.equal(RoomStage.LOBBY, 'LOBBY');
  host.startMatch();
  assert.equal(hostGame.game.players[1].isBot, true);
  assert.equal(hostGame.hasBotAction, false, 'e rândul gazdei');
  hostGame.endTurn();    // nu e turn încheiat: fără efect
  hostGame.roll(1);
  hostGame.acceptOffer();
  hostGame.endTurn();
  assert.equal(hostGame.currentPlayer.isBot, true);
  assert.equal(hostGame.hasBotAction, true);
  hostGame.botNextAction();    // robotul aruncă
  assert.equal(hostGame.lastDice.length, 1);
  assert.equal(EV.ROLL, 'ROLL');
});
