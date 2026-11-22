import assert from 'node:assert/strict';
import { test } from 'node:test';
import { Game } from '../js/game/game.js';
import { GameScreen } from '../js/game/gameScreen.js';
import { loadRealCatalog } from './helpers.mjs';

const catalog = loadRealCatalog();
const person = { name: 'Ana', colorKey: 'ROSU' };
const robot = { name: 'Robot', colorKey: 'ALBASTRU', isBot: true };

for (const [mode, dice, rapid] of [['Normal', 1, false], ['Rapid', 1, true], ['Două zaruri', 2, false]]) {
  test(`Abandon în modul ${mode} acordă victoria robotului`, () => {
    const game = new Game(catalog);
    game.startGame([person, robot], null, null, dice, 'old', rapid);
    assert.equal(game.abandon('p0'), true);
    assert.equal(game.winnerName, 'Robot');
    assert.equal(game.abandonedBy, 'Ana');
    assert.equal(game.hasBotAction, false);
  });
}

test('după abandonul unui alt jucător, partida locală păstrează tura și continuă', () => {
  const game = new Game(catalog);
  game.startGame([person, robot, { name: 'Dan', colorKey: 'MOV' }]);
  game.currentIndex = 2;
  assert.equal(game.abandon('p0'), true);
  assert.equal(game.currentPlayer.name, 'Dan');
  assert.equal(game.winnerName, null);
});

test('confirmarea abandonului pornește clipul de victorie, apoi arată înfrângerea', () => {
  class Element {
    constructor() { this.children = []; this.events = {}; }
    append(child) { this.children.push(child); }
    setAttribute() {}
    addEventListener(type, handle) { this.events[type] = handle; }
  }
  globalThis.document = { createElement: () => new Element(), createTextNode: (text) => ({ textContent: text }) };
  globalThis.Node = Element;
  globalThis.window = {};
  const game = new Game(catalog);
  game.startGame([person, robot]);
  const screen = Object.assign(Object.create(GameScreen.prototype), {
    game, catalog, resultPlayerName: person.name,
    $: () => ({}),
    openModal(key, node) { this.modalKey = key; this.node = node; },
    closeModal() { this.modalKey = null; },
    render() {}, cancelPendingRoll() {},
    playWinVideo() { this.played = true; },
  });
  screen.openAbandon();
  assert.equal(screen.node.children[0].textContent, 'Ești sigur că vrei să renunți?');
  assert.equal(game.winnerName, null);
  screen.node.children.at(-1).children[0].events.click();
  assert.equal(game.winnerName, null);

  screen.openAbandon();
  screen.node.children.at(-1).children[1].events.click();
  screen.renderWinner();
  assert.equal(screen.played, true);
  screen.winVideoWatched = true;
  screen.renderWinner();
  assert.equal(screen.node.children[0].textContent, 'Ai pierdut prin abandon');
});
