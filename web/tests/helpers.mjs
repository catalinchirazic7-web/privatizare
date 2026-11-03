// Ajutoare pentru teste: catalogul real din data/ (fără rețea).
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { buildCatalog } from '../js/core/model.js';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

export function readData(name) {
  return JSON.parse(fs.readFileSync(path.join(root, 'data', name), 'utf8'));
}

export function loadRealCatalog() {
  return buildCatalog(readData('board.json'), readData('actions.json'), readData('cards.json'));
}

/** Generator determinist (LCG) pentru teste reproductibile. */
export function seededRng(seed = 1) {
  let s = seed >>> 0;
  return () => {
    s = (Math.imul(s, 1664525) + 1013904223) >>> 0;
    return s / 4294967296;
  };
}
