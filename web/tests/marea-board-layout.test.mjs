import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { BoardView } from '../js/game/board.js';
import { buildCatalog } from '../js/core/model.js';
import { readData } from './helpers.mjs';

test('traseul, START și pachetele sunt aliniate la PNG-ul decupat', () => {
  const data = readData('marea.json');
  const catalog = buildCatalog(readData('board.json'), readData('actions.json'),
    readData('cards.json'), readData('board_new.json'), data);
  const { aspect, image, positions, decks, startMarker } = catalog.layouts.marea;
  assert.equal(image, 'assets/tabla-marea-privatizare.png');
  const png = fs.readFileSync(new URL('../' + image, import.meta.url));
  assert.equal(png.toString('ascii', 1, 4), 'PNG');
  const width = png.readUInt32BE(16), height = png.readUInt32BE(20);
  assert.equal(width, 3840);
  assert.deepEqual(data.coordinateSpace, { width, height });
  assert.equal(aspect, width / height);
  assert.equal(positions.length, 37);
  for (const [x, y] of [...positions, startMarker]) assert.ok(x > 0 && x < 1 && y > 0 && y < 1);
  for (const rect of Object.values(decks)) {
    assert.ok(rect.x > 0 && rect.y > 0 && rect.w > 0 && rect.h > 0);
    assert.ok(rect.x + rect.w < 1 && rect.y + rect.h < 1);
  }
});

test('lățimea adaptivă folosește ecranul disponibil și păstrează toată tabla vizibilă', () => {
  const originalWindow = globalThis.window;
  globalThis.window = { devicePixelRatio: 2 };
  try {
    const board = Object.assign(Object.create(BoardView.prototype), {
      catalog: { boardAspect: 3611 / 2470 },
      model: { stretch: 'adaptive' },
      canvas: { clientWidth: 1280, clientHeight: 720 },
      clampZoom() {},
    });
    board.resize();
    assert.ok(board.layout.bw > 1230);
    assert.equal(board.layout.bh, 720);
    board.canvas.clientWidth = 844;
    board.canvas.clientHeight = 390;
    board.resize();
    assert.ok(board.layout.bw > 650 && board.layout.bw <= 844);
    assert.equal(board.layout.bh, 390);
    assert.ok(board.layout.bw / board.layout.bh <= board.catalog.boardAspect * 1.18 + 1e-9);
    board.canvas.clientWidth = 390;
    board.canvas.clientHeight = 844;
    board.resize();
    assert.equal(board.layout.bw, 390);
    assert.ok(board.layout.bh < 300);
  } finally {
    if (originalWindow === undefined) delete globalThis.window;
    else globalThis.window = originalWindow;
  }
});
