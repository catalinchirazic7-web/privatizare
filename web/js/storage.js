// Salvări locale (per dispozitiv): partida locală și ultima configurare. Erorile se ignoră.
import { SnapshotCodec } from './online/protocol.js';

const GAME_KEY = 'privatizare.partida';
const SETUP_KEY = 'privatizare.configurare';

export const storage = {
  /** Salvează partida locală: snapshot (același format ca online) și timpul rămas. */
  saveGame(snapshot, leftMs) {
    try {
      localStorage.setItem(GAME_KEY, JSON.stringify({ snap: SnapshotCodec.encode(snapshot), left: leftMs ?? null }));
    } catch {
      /* spațiu plin sau mod privat */
    }
  },
  loadGame() {
    try {
      const raw = localStorage.getItem(GAME_KEY);
      if (!raw) return null;
      const o = JSON.parse(raw);
      return { snapshot: SnapshotCodec.decode(o.snap), leftMs: o.left ?? null };
    } catch {
      return null;
    }
  },
  clearGame() {
    try {
      localStorage.removeItem(GAME_KEY);
    } catch {
      /* ignorat */
    }
  },
  saveSetup(config) {
    try {
      localStorage.setItem(SETUP_KEY, JSON.stringify(config));
    } catch {
      /* ignorat */
    }
  },
  loadSetup() {
    try {
      return JSON.parse(localStorage.getItem(SETUP_KEY) ?? 'null');
    } catch {
      return null;
    }
  },
};
