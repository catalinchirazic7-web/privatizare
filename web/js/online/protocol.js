// Protocolul online, compatibil cu aplicația Android (rooms/{cod}/info, state, intents).
// Port din RoomModels.kt, RoomRules.kt, RoomCode.kt, IntentCodec.kt, SnapshotCodec.kt și MultiplayerBridge.kt.
// Cheile JSON sunt cele din Kotlin; nu le schimba fără să schimbi și aplicația.

import { PAWN_BY_KEY } from '../core/model.js';

export const RoomStage = Object.freeze({ LOBBY: 'LOBBY', PLAYING: 'PLAYING', FINISHED: 'FINISHED' });
export const RoomRole = Object.freeze({ HOST: 'HOST', GUEST: 'GUEST', BOT: 'BOT' });
export const MAX_PLAYERS = 4;
export const MIN_PLAYERS = 2;
const SEATS = ['p0', 'p1', 'p2', 'p3'];

export class RoomException extends Error {}

/* ---------- Cod de cameră ---------- */

// Fără I, O, 0, 1 — ca să nu se confunde.
const ROOM_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
const ROOM_CODE_LENGTH = 4;

export const RoomCode = {
  generate(rng = Math.random) {
    let s = '';
    for (let i = 0; i < ROOM_CODE_LENGTH; i++) s += ROOM_ALPHABET[Math.floor(rng() * ROOM_ALPHABET.length)];
    return s;
  },
  isValid(code) {
    return code.length === ROOM_CODE_LENGTH && [...code].every((c) => ROOM_ALPHABET.includes(c));
  },
};

/* ---------- Reguli pure ale camerei ---------- */

export const RoomRules = {
  freeSeat(players) {
    return SEATS.find((id) => !players.some((p) => p.id === id)) ?? null;
  },
  freeColor(players) {
    return Object.keys(PAWN_BY_KEY).find((k) => !players.some((p) => p.color === k)) ?? null;
  },
  /** Motivul pentru care nu poți intra, sau null. */
  joinError(info, color) {
    if (!info) return 'Camera nu există.';
    if (info.stage !== RoomStage.LOBBY) return 'Meciul a început deja.';
    if (info.players.length >= MAX_PLAYERS) return 'Masa este plină.';
    if (info.players.some((p) => p.color === color)) return 'Pionul este deja luat de alt jucător.';
    return null;
  },
  isFull(info) {
    return info.players.length >= MAX_PLAYERS;
  },
  canStart(info) {
    return info.players.length >= MIN_PLAYERS && info.players.some((p) => p.role !== RoomRole.BOT);
  },
  toMap(info) {
    const players = {};
    for (const p of info.players) players[p.id] = { name: p.name, color: p.color, role: p.role };
    return { stage: info.stage, hostId: info.hostId, players };
  },
  /** Citește info din valoarea returnată de baza de date. Null dacă lipsește sau e stricată. */
  fromMap(raw) {
    if (!raw || typeof raw !== 'object') return null;
    if (!Object.values(RoomStage).includes(raw.stage)) return null;
    if (typeof raw.hostId !== 'string') return null;
    const players = [];
    if (raw.players && typeof raw.players === 'object') {
      for (const [id, pm] of Object.entries(raw.players)) {
        if (!pm || typeof pm !== 'object') continue;
        if (typeof pm.name !== 'string' || !PAWN_BY_KEY[pm.color] || !Object.values(RoomRole).includes(pm.role)) continue;
        players.push({ id, name: pm.name, color: pm.color, role: pm.role });
      }
    }
    players.sort((a, b) => (a.id < b.id ? -1 : a.id > b.id ? 1 : 0));
    return { stage: raw.stage, hostId: raw.hostId, players };
  },
};

/* ---------- Evenimente (intențiile oaspeților) ---------- */

/**
 * Forma unui eveniment: { t, playerId, ... }.
 * t: ROLL (value), BUY (tile), DECLINE (tile), ANNEX (tile), END, RENT, ACK,
 *    SELL (prop, ax), BANKRUPT, DRAW.
 */
export const EV = Object.freeze({
  ROLL: 'ROLL', BUY: 'BUY', DECLINE: 'DECLINE', ANNEX: 'ANNEX', END: 'END', RENT: 'RENT',
  ACK: 'ACK', SELL: 'SELL', BANKRUPT: 'BANKRUPT', DRAW: 'DRAW',
});

const INTENT_KEY = { ROLL: 'roll', BUY: 'buy', DECLINE: 'decline', ANNEX: 'annex', END: 'end', RENT: 'rent', ACK: 'ack', SELL: 'sell', BANKRUPT: 'bankrupt', DRAW: 'draw' };
const INTENT_TYPE = Object.fromEntries(Object.entries(INTENT_KEY).map(([k, v]) => [v, k]));

export const IntentCodec = {
  encode(e) {
    const o = { p: e.playerId, t: INTENT_KEY[e.t] };
    switch (e.t) {
      case EV.ROLL: o.v = e.value; break;
      case EV.BUY:
      case EV.DECLINE:
      case EV.ANNEX: o.tile = e.tile; break;
      case EV.SELL: o.prop = e.propertyId; o.ax = e.annexIndex ?? null; break;
      default: break;
    }
    return JSON.stringify(o);
  },
  /** null pentru o intenție necunoscută sau stricată. */
  decode(json) {
    try {
      const o = JSON.parse(json);
      const p = o.p;
      const t = INTENT_TYPE[o.t];
      if (typeof p !== 'string' || !t) return null;
      switch (t) {
        case EV.ROLL: return { t, playerId: p, value: Number.isInteger(o.v) ? o.v : 1 };
        case EV.BUY:
        case EV.DECLINE:
        case EV.ANNEX:
          if (!Number.isInteger(o.tile)) return null;
          return { t, playerId: p, tile: o.tile };
        case EV.SELL: return { t, playerId: p, propertyId: o.prop, annexIndex: o.ax ?? null };
        default: return { t, playerId: p };
      }
    } catch {
      return null;
    }
  },
};

/* ---------- Starea oficială (gazda -> oaspeți) ---------- */

export const SnapshotCodec = {
  MAX_HISTORY: 20,

  encode(s) {
    const offer = (o) => (o ? { pl: o.playerId, pr: o.propertyId, ax: o.annexIndex ?? null } : null);
    const debt = (d) => ({ de: d.debtorId, am: d.amount, cr: d.creditorId ?? null, re: d.reason });
    return JSON.stringify({
      turn: s.state.turn,
      bank: s.state.bankMoney,
      history: s.state.history.slice(-this.MAX_HISTORY),
      st: s.state.stats ?? {},
      players: s.state.players.map(encodePlayer),
      cur: s.currentIndex,
      dice: s.lastDice,
      dc: s.diceCount ?? 1,
      rapid: !!s.rapidMode,
      bl: s.boardLayout ?? 'old',
      easy: !!s.easyMode,
      rerolled: !!s.rerolled,
      winner: s.winnerName ?? null,
      await: s.turnAwaitingEnd,
      skip: s.skipBanner ?? null,
      offer: offer(s.offer),
      fee: offer(s.feeOffer),
      rent: s.rentDue ? { pa: s.rentDue.payerId, ow: s.rentDue.ownerId, pr: s.rentDue.propertyId, am: s.rentDue.amount } : null,
      debt: s.debt ? { ...debt(s.debt), ...(s.debt.next?.length ? { next: s.debt.next.map(debt) } : {}) } : null,
      move: s.pendingMove ?? null,
      ...(s.decks ? { decks: s.decks } : {}),
      draw: s.pendingDraw ?? null,
      card: s.card
        ? { k: s.card.kind, t: s.card.text, o: s.card.outcome, a: s.card.amount ?? null, d: s.card.destination ?? null, i: s.card.cardId ?? null }
        : null,
    });
  },

  decode(json) {
    const o = JSON.parse(json);
    const offer = (x) => (x ? { playerId: x.pl, propertyId: x.pr, annexIndex: x.ax ?? null } : null);
    const debt = (d) => ({ debtorId: d.de, amount: d.am, creditorId: d.cr ?? null, reason: d.re });
    return {
      state: {
        turn: o.turn,
        players: o.players.map(decodePlayer),
        bankMoney: o.bank,
        history: [...o.history],
        stats: Object.fromEntries(Object.entries(o.st ?? {}).map(([id, stats]) => [id, {
          visits: stats.visits ?? stats.v ?? {},
          collected: stats.collected ?? stats.c ?? 0,
        }])),
      },
      currentIndex: o.cur,
      lastDice: [...o.dice],
      diceCount: o.dc === 2 ? 2 : 1,
      rapidMode: !!o.rapid,
      boardLayout: o.bl === 'new' ? 'new' : 'old',
      easyMode: !!o.easy,
      rerolled: !!o.rerolled,
      winnerName: o.winner ?? null,
      turnAwaitingEnd: o.await === true,
      skipBanner: o.skip ?? null,
      offer: offer(o.offer),
      feeOffer: offer(o.fee),
      rentDue: o.rent ? { payerId: o.rent.pa, ownerId: o.rent.ow, propertyId: o.rent.pr, amount: o.rent.am } : null,
      debt: o.debt ? { ...debt(o.debt), ...(Array.isArray(o.debt.next) ? { next: o.debt.next.map(debt) } : {}) } : null,
      pendingMove: Number.isInteger(o.move) ? o.move : null,
      decks: o.decks,
      pendingDraw: o.draw || null,
      card: o.card
        ? {
            kind: o.card.k,
            text: o.card.t,
            outcome: o.card.o || 'NEUTRAL',
            amount: o.card.a ?? null,
            destination: o.card.d || null,
            cardId: o.card.i || null,
          }
        : null,
    };
  },
};

function encodePlayer(p) {
  return {
    id: p.id, n: p.name, c: p.color, m: p.money, pos: p.position, sk: p.skipTurns, bot: p.isBot,
    cat: !!p.catalin, easy: !!p.easy, ar: !!p.annexRecovery, hs: p.houseSince, own: { ...p.ownedProperties },
  };
}

function decodePlayer(o) {
  return {
    id: o.id,
    name: o.n,
    color: o.c,
    money: o.m,
    position: o.pos,
    skipTurns: o.sk,
    isBot: o.bot === true,
    catalin: o.cat === true,
    easy: o.easy === true,
    annexRecovery: o.ar === true,
    houseSince: o.hs ?? 0,
    ownedProperties: { ...o.own },
  };
}
