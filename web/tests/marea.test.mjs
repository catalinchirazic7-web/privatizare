import { test } from 'node:test';
import assert from 'node:assert/strict';
import { buildCatalog, TILE } from '../js/core/model.js';
import { calculateRent, nextAnnex } from '../js/core/actions.js';
import { safetyBuffer, utilityScore } from '../js/core/botAI.js';
import { GameRules } from '../js/core/rules.js';
import { Game } from '../js/game/game.js';
import { GameScreen } from '../js/game/gameScreen.js';
import { SnapshotCodec } from '../js/online/protocol.js';
import { readData } from './helpers.mjs';
import { OnlineController } from '../js/online/controller.js';
import { MemoryBackend, MemoryDb } from './memoryBackend.mjs';

const seats = [{ name: 'Ana', colorKey: 'ROSU' }, { name: 'Dan', colorKey: 'ALBASTRU' }];
function createGame() {
  const catalog = buildCatalog(readData('board.json'), readData('actions.json'),
    readData('cards.json'), readData('board_new.json'), readData('marea.json'));
  return new Game(catalog, { rng: () => 0.5 });
}
function start(game, layout = 'marea', setup = seats) {
  game.startGame(setup, null, null, 1, layout);
  return game;
}
const cases = [
  [1, 'kandia', 2000, [450, 1450, 2700]],
  [7, 'unu_iunie', 1800, [250, 800, 1800]],
  [20, 'comtim', 5000, [1000, 3000, 6500]],
  [25, 'azur', 2500, [500, 1100, 2100]],
  [34, 'umt', 4500, [650, 1500, 2700, 4700]],
  [36, 'solventul', 2500, [500, 1100, 2000]],
];

test('cele șase fabrici au indicii, costurile și chiriile cumulative convenite', () => {
  const game = start(createGame());
  assert.equal(game.catalog.board.length, 37);
  assert.equal(Object.keys(game.catalog.actions).length, 33);
  for (const [index, id, price, rents] of cases) {
    const prop = game.catalog.actions[id];
    assert.equal(game.catalog.board[index].propertyId, id);
    assert.equal(prop.titlePrice, price);
    assert.equal(prop.titleTax, rents[0]);
    assert.equal(prop.annexes.length, rents.length - 1);
    assert.deepEqual(rents.map((_, level) => calculateRent(prop, level)), rents);
    assert.equal(nextAnnex(prop, rents.length - 1), null);
    assert.ok(prop.annexes.every((annex) => annex.name));
    const turn = start(createGame());
    turn.currentPlayer.position = index - 1;
    turn.roll(1);
    assert.equal(turn.activeDialog.offer.propertyId, id);
    turn.refuseOffer();
    assert.equal(turn.activeDialog.taxAmount, rents[0]);
  }
  for (const [layout, dice, rapid] of [['old', 1, false], ['old', 1, true], ['old', 2, false], ['new', 1, false]]) {
    game.startGame(seats, null, null, dice, layout, rapid);
    assert.equal(game.boardLayout, layout);
    assert.equal(game.catalog.board[36].type, TILE.BANK_DIVIDEND);
    assert.equal(game.catalog.board[1].type, TILE.LOTO);
    assert.equal(game.catalog.actions.comtim, undefined);
    assert.equal(game.diceCount, dice);
    assert.equal(game.rapidMode, rapid);
  }
});

test('rezerva botului include chiria cumulativă Comtim și randamentul folosește 3500', () => {
  const game = start(createGame());
  const { board, actions } = game.catalog;
  const { players } = game.game;
  players[0].position = 19;
  players[1].ownedProperties.comtim = 2;
  assert.equal(safetyBuffer(game.game, 0, board, actions), 8125);
  const annex = actions.comtim.annexes[1];
  assert.equal(annex.profit, 3500);
  const score = utilityScore({ state: game.game, botIdx: 0, board, actions,
    propertyId: 'comtim', option: { type: 'ANNEX', annex } });
  assert.ok(score.reasons.some(reason => reason.startsWith('randament')));
  // La COMTIM propriul bot e cu o căsuță înainte de un Guban complet al adversarului.
  players[0].ownedProperties = { comtim: 1 };
  players[1].ownedProperties = { guban: actions.guban.annexes.length };
  players[0].isBot = true;
  players[0].position = 19;
  const buffer = game.annexReserve(0);
  players[0].money = 14000 + buffer - 1;
  game.roll(1);
  assert.equal(game.activeDialog.offer.option.annex.name, 'Abator și Linie Frigorifică');
  game.botNextAction();
  assert.equal(game.currentPlayer.ownedProperties.comtim, 1);
});

function resume(game) {
  const snapshot = SnapshotCodec.decode(SnapshotCodec.encode(game.snapshot()));
  const restored = createGame();
  restored.restore(snapshot, null);
  return restored;
}

test('SALA păstrează TELEX după lichidare și după salvare', () => {
  let game = start(createGame());
  game.currentPlayer.position = 9;
  game.currentPlayer.money = 0;
  game.currentPlayer.ownedProperties.comtim = 0;
  game.roll(1);
  assert.equal(game.debt.amount, GameRules.SALA_CONSFATUIRI_FEE);
  assert.equal(game.pendingDraw, 'TELEX');
  game = resume(game);
  assert.equal(game.boardLayout, 'marea');
  assert.equal(game.autoDrawReady, false);
  game.settleDebt([{ propertyId: 'comtim', annexIndex: null }]);
  assert.equal(game.autoDrawReady, true);
  game.drawCard();
  assert.equal(game.shownCard.kind, 'TELEX');
  game = resume(game);
  assert.equal(game.pendingDraw, null);
  assert.equal(game.shownCard.kind, 'TELEX');
});

test('BANCA și SALA declanșează automat o singură extragere după animații', () => {
  const originalSet = globalThis.setTimeout;
  const originalClear = globalThis.clearTimeout;
  const scheduled = new Map();
  let id = 0;
  globalThis.setTimeout = (callback) => { scheduled.set(++id, callback); return id; };
  globalThis.clearTimeout = (timer) => scheduled.delete(timer);
  try {
    for (const [index, deck, balance, bot] of [
      [14, 'LOTO', GameRules.BANK_INTEREST, false],
      [10, 'TELEX', -GameRules.SALA_CONSFATUIRI_FEE, true],
    ]) {
      const game = start(createGame(), 'marea', [seats[0], { ...seats[1], isBot: bot }]);
      if (bot) game.currentIndex = 1;
      game.currentPlayer.position = index - 1;
      const before = game.currentPlayer.money;
      game.roll(1);
      assert.equal(game.currentPlayer.money, before + balance);
      assert.equal(game.pendingDraw, deck);
      const screen = Object.assign(Object.create(GameScreen.prototype), {
        active: true, game, pawnsBusy: true, die: { busy: false }, rollHold: false,
      });
      screen.pump();
      assert.equal(scheduled.size, 0);
      screen.pawnsBusy = false;
      screen.pump();
      assert.equal(scheduled.size, 1);
      const [timer, callback] = scheduled.entries().next().value;
      scheduled.delete(timer);
      callback();
      assert.equal(game.shownCard.kind, deck);
      assert.equal(game.pendingDraw, null);
    }
  } finally {
    globalThis.setTimeout = originalSet;
    globalThis.clearTimeout = originalClear;
  }
});
test('online: camerele Marea și clasice sunt separate și noua gazdă păstrează tabla', async () => {
  const db = new MemoryDb();
  const hostGame = createGame();
  const guestGame = createGame();
  const classicGame = createGame();
  const host = new OnlineController(new MemoryBackend(db), hostGame, { boardLayout: 'marea', newCode: () => 'ABCD' });
  const guest = new OnlineController(new MemoryBackend(db), guestGame, { boardLayout: 'marea' });
  const classic = new OnlineController(new MemoryBackend(db), classicGame, { newCode: () => 'ABCD' });
  await host.createRoom('Ana', 'ROSU');
  await classic.joinRoom('ABCD', 'Dan', 'ALBASTRU');
  assert.equal(classic.error, 'Camera nu există.');
  await classic.createRoom('Maria', 'ROSU');
  await guest.joinRoom('ABCD', 'Dan', 'ALBASTRU');
  await host.startMatch();
  assert.equal(hostGame.boardLayout, 'marea');
  assert.equal(guestGame.boardLayout, 'marea');
  hostGame.currentPlayer.position = 13;
  hostGame.roll(1);
  assert.equal(hostGame.autoDrawReady, true);
  assert.equal(guestGame.autoDrawReady, false);
  assert.equal(guestGame.pendingDraw, 'LOTO');
  await host.leave();
  assert.equal(guest.isHost, true);
  assert.equal(guestGame.boardLayout, 'marea');
  guestGame.drawCard();
  assert.equal(guestGame.shownCard.kind, 'LOTO');
  assert.equal(classic.info.players.length, 1);
});
