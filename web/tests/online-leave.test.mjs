import { test } from 'node:test';
import assert from 'node:assert/strict';
import { Game } from '../js/game/game.js';
import { OnlineController } from '../js/online/controller.js';
import { RoomRole, SnapshotCodec } from '../js/online/protocol.js';
import { MemoryDb, MemoryBackend } from './memoryBackend.mjs';
import { loadRealCatalog } from './helpers.mjs';

const catalog = loadRealCatalog();
const code = 'ABCD';

async function setup() {
  const db = new MemoryDb();
  const client = () => {
    const game = new Game(catalog, { botDie: () => 1 });
    return new OnlineController(new MemoryBackend(db), game, { newCode: () => code, hostDie: () => 3 });
  };
  const host = client();
  const guest = client();
  await host.createRoom('Ana', 'ROSU');
  await guest.joinRoom(code, 'Bogdan', 'ALBASTRU');
  return { db, host, guest, client };
}

test('host handoff preserves the seat and enables the successor to run its bot', async () => {
  const { db, host, guest } = await setup();
  host.startMatch();
  const stale = db.room(code).state;
  host.unsubGame();
  host.game.roll(1);
  host.game.acceptOffer();
  host.game.engine.lotoDeck.draw();
  const snapshot = host.game.snapshot();
  const [seat] = SnapshotCodec.decode(SnapshotCodec.encode(snapshot)).state.players;
  assert.ok(Object.keys(seat.ownedProperties).length);
  assert.equal(guest.game.game.players[0].position, -1);

  const enterHost = guest.game.enterOnlineHost.bind(guest.game);
  guest.game.enterOnlineHost = (id) => {
    assert.deepEqual(guest.game.game.players[0], { ...seat, isBot: true });
    assert.deepEqual(guest.game.snapshot().decks, snapshot.decks);
    enterHost(id);
  };

  await host.leave();

  assert.equal(host.game.isOnline, false);
  assert.equal(guest.isHost, true);
  assert.equal(guest.game.isGuest, false);
  assert.deepEqual(guest.game.game.players[0], { ...seat, isBot: true });
  assert.deepEqual(SnapshotCodec.decode(db.room(code).state).state.players[0], { ...seat, isBot: true });
  assert.equal(guest.game.hasBotAction, true);
  assert.equal(await host.backend.publishState(code, stale, 'p0'), false);
  guest.game.botNextAction();
  assert.equal(guest.game.currentPlayer.id, 'p1');
  assert.equal(SnapshotCodec.decode(db.room(code).state).currentIndex, 1);
});

test('guest leave converts the playing seat and the last human closes the room', async () => {
  const { db, host, guest } = await setup();
  host.startMatch();
  const seat = SnapshotCodec.decode(db.room(code).state).state.players[1];
  await guest.leave();
  assert.equal(host.info.players[1].role, RoomRole.BOT);
  assert.deepEqual(SnapshotCodec.decode(SnapshotCodec.encode(host.game.snapshot())).state.players[1], { ...seat, isBot: true });
  assert.deepEqual(SnapshotCodec.decode(db.room(code).state).state.players[1], { ...seat, isBot: true });
  await host.leave();
  assert.equal(await host.backend.peekInfo(code), null);
});

test('lobby host leave promotes a guest and the match preserves the remaining IDs', async () => {
  const { host, guest, client } = await setup();
  const otherGuest = client();
  await otherGuest.joinRoom(code, 'Dana', 'GALBEN');
  await host.leave();
  assert.equal(guest.isHost, true);
  assert.equal(guest.inGame, false);
  assert.deepEqual(guest.info.players.map((p) => p.id), ['p1', 'p2']);
  guest.startMatch();
  assert.equal(otherGuest.inGame, true);
  assert.deepEqual(guest.game.game.players.map((p) => p.id), ['p1', 'p2']);
  assert.equal(guest.game.myPlayerId, 'p1');
});

test('a promoted guest already leaving passes the committed state to the next guest', async () => {
  const { db, host, guest, client } = await setup();
  const otherGuest = client();
  await otherGuest.joinRoom(code, 'Dana', 'GALBEN');
  host.startMatch();
  host.game.roll(1);
  host.game.acceptOffer();

  const leaveRoom = guest.backend.leaveRoom.bind(guest.backend);
  let finishLeave;
  guest.backend.leaveRoom = (...args) => new Promise((resolve) => {
    finishLeave = () => resolve(leaveRoom(...args));
  });
  const leaving = guest.leave();
  await host.leave();
  assert.equal(db.room(code).info.hostId, 'p1');
  finishLeave();
  await leaving;

  assert.equal(otherGuest.isHost, true);
  const players = otherGuest.game.game.players;
  assert.deepEqual(players.map((p) => p.isBot), [true, true, false]);
  assert.equal(players[0].position, 0);
  assert.equal(players[0].ownedProperties.continental, 0);
});

test('a guest can reclaim a released lobby color and seat', async () => {
  const { host, guest, client } = await setup();
  await guest.leave();
  const newcomer = client();
  await newcomer.joinRoom(code, 'Carmen', 'ALBASTRU');
  assert.equal(newcomer.me.id, 'p1');
  assert.equal(host.info.players[1].name, 'Carmen');
});

test('the new host finishes a TELEX move that was awaiting confirmation', async () => {
  const { host, guest } = await setup();
  host.startMatch();
  const game = host.game;
  const tile = catalog.board.findIndex((space) => space.type === 'TELEX');
  game.currentPlayer.position = tile;
  const deck = game.engine.telexDeck;
  const card = deck.pile.find((entry) => entry.effects.some((effect) => effect.type === 'move'));
  deck.pile = [card, ...deck.pile.filter((entry) => entry !== card)];
  game.pendingDraw = 'TELEX';
  game.drawCard();
  const target = (tile + game.pendingMove + catalog.board.length) % catalog.board.length;
  const cards = deck.snapshot();
  await host.leave();

  assert.equal(guest.isHost, true);
  assert.equal(guest.game.activeDialog.type, 'CARD');
  assert.deepEqual(guest.game.engine.telexDeck.snapshot(), cards);
  assert.equal(guest.game.botNextAction(), true);
  assert.equal(guest.game.currentPlayer.position, target);
});
