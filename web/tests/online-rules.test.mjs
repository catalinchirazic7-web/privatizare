// Jocul online: 2 zaruri (câmpul dice, compatibil cu Android), modul Rapid și anexele cumpărate de oaspete.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { Game } from '../js/game/game.js';
import { EV, IntentCodec } from '../js/online/protocol.js';
import { applyEvent } from '../js/online/events.js';
import { loadRealCatalog } from './helpers.mjs';

const cat = loadRealCatalog();
const PLAYERS = [{ id: 'p0', name: 'Ana', colorKey: 'ROSU' }, { id: 'p1', name: 'Dan', colorKey: 'ALBASTRU' }];

test('intenția ROLL păstrează totalul și zarurile; mesajele vechi rămân valide', () => {
  const two = IntentCodec.decode(IntentCodec.encode({ t: EV.ROLL, playerId: 'p1', value: 7, dice: [3, 4] }));
  assert.deepEqual(two, { t: EV.ROLL, playerId: 'p1', value: 7, dice: [3, 4] });
  const one = IntentCodec.decode(IntentCodec.encode({ t: EV.ROLL, playerId: 'p1', value: 5 }));
  assert.deepEqual(one, { t: EV.ROLL, playerId: 'p1', value: 5 });
  // un mesaj vechi, de la aplicația Android, are doar totalul
  assert.deepEqual(IntentCodec.decode('{"p":"p1","t":"roll","v":4}'), { t: EV.ROLL, playerId: 'p1', value: 4 });
});

test('gazda cu 2 zaruri aruncă două zaruri, iar pionul merge cu suma', () => {
  const g = new Game(cat);
  g.startGame(PLAYERS, null, null, 2, 'new', false);
  let n = 0;
  assert.equal(applyEvent(g, { t: EV.ROLL, playerId: g.currentPlayer.id, value: 0 }, () => (n += 1)), true);
  assert.deepEqual(g.lastDice, [1, 2]);
});

test('modul Rapid online cumpără anexele rămase după titlu', () => {
  const g = new Game(cat);
  g.startGame(PLAYERS, null, null, 1, 'new', true);
  g.enterOnlineHost('p0');
  const p = cat.actions.poli, player = g.game.players[0];
  g.offer = { playerId: player.id, propertyId: p.id, option: { type: 'TITLE', price: p.titlePrice } };
  g.acceptOffer();
  g.offer = { playerId: player.id, propertyId: p.id, option: { type: 'ANNEX', annex: p.annexes[0] } };
  assert.equal(g.buyAllAnnexes(), true);
  assert.equal(g.game.players[0].ownedProperties[p.id], p.annexes.length);
});

test('oaspetele trimite câte o intenție ANNEX pentru fiecare anexă cumpărată', () => {
  const g = new Game(cat);
  g.startGame(PLAYERS, null, null, 1, 'new', true);
  const sent = [];
  g.enterOnlineGuest('p1', (event) => sent.push(event));
  const p = cat.actions.poli, player = g.game.players[0];
  player.ownedProperties[p.id] = 0;   // titlul e deja cumpărat
  g.offer = { playerId: player.id, propertyId: p.id, option: { type: 'ANNEX', annex: p.annexes[0] } };
  assert.equal(g.buyAnnexes(2), true);
  assert.deepEqual(sent.map((e) => e.t), [EV.ANNEX, EV.ANNEX]);
  assert.ok(sent.every((e) => e.tile === g.tileOf(p.id)));
});
