import assert from 'node:assert/strict';
import { test } from 'node:test';
import { Game } from '../js/game/game.js';
import { SnapshotCodec } from '../js/online/protocol.js';
import { loadRealCatalog } from './helpers.mjs';

const catalog = loadRealCatalog();
const seats = ['ROSU', 'ALBASTRU', 'MOV', 'GALBEN'].map((colorKey, i) => ({ name: `P${i + 1}`, colorKey }));
const flower = catalog.telex.find((card) => card.effects.some((effect) => effect.type === 'receive_from_each_player'));

function drawFlower(game) {
  const deck = game.engine.telexDeck;
  deck.pile = [flower, ...deck.pile.filter((card) => card.id !== flower.id)];
  game.pendingDraw = 'TELEX';
  game.drawCard();
}

function resume(game) {
  const restored = new Game(catalog);
  restored.restore(SnapshotCodec.decode(SnapshotCodec.encode(game.snapshot())), null);
  return restored;
}

test('Ceas floral ia câte 2000 ECU de la fiecare dintre ceilalți trei jucători', () => {
  const game = new Game(catalog);
  game.startGame(seats, 50000);
  drawFlower(game);
  assert.deepEqual(game.game.players.map((p) => p.money), [56000, 48000, 48000, 48000]);
});

test('Ceas floral continuă după faliment, lichidare și reluarea partidei', () => {
  const initial = new Game(catalog);
  initial.startGame(seats, 50000);
  initial.currentIndex = 2;
  initial.game.players[0].money = 0;
  initial.game.players[1].money = 0;
  initial.game.players[1].ownedProperties.continental = 0;
  drawFlower(initial);
  const game = resume(initial);
  assert.equal(game.debt.debtorId, 'p0');
  assert.equal(game.game.players[3].money, 48000);
  assert.equal(game.currentPlayer.money, 52000);
  game.settleDebt([]);
  assert.equal(game.debt.debtorId, 'p1');
  assert.equal(game.currentPlayer.id, 'p2');
  game.settleDebt([{ propertyId: 'continental' }]);
  assert.equal(game.debt, null);
  assert.equal(game.currentPlayer.money, 54000);
  assert.equal(game.currentPlayer.id, 'p2');
  assert.equal(game.activeDialog.type, 'CARD');
  game.dismissCard();
  game.endTurn();
  assert.equal(game.currentPlayer.id, 'p3');
});

test('reluarea păstrează tipurile roboților și ordinea cartonașelor rămase', () => {
  const initial = new Game(catalog);
  initial.startGame([seats[0],
    { ...seats[1], isBot: true, catalin: true },
    { ...seats[2], isBot: true, easy: true },
    { ...seats[3], isBot: true },
  ]);
  drawFlower(initial);
  const game = resume(initial);
  assert.deepEqual(game.game.players.map(({ isBot, catalin, easy }) => [isBot, catalin, easy]), [
    [false, false, false], [true, true, false], [true, false, true], [true, false, false],
  ]);
  assert.deepEqual(game.engine.telexDeck.snapshot(), initial.engine.telexDeck.snapshot());
});
