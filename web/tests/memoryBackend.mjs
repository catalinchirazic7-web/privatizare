// Backend în memorie cu aceeași interfață ca FirebaseRoomBackend (fără rețea), pentru teste.
import { RoomException, RoomRole, RoomRules, RoomStage } from '../js/online/protocol.js';
import { roomAfterLeave, roomWithState } from '../js/online/controller.js';

export class MemoryDb {
  constructor() {
    this.rooms = new Map();      // code -> { info, state, intents: [] }
    this.subs = new Map();       // `${kind}:${code}` -> Set<fn>
  }

  room(code) {
    if (!this.rooms.has(code)) this.rooms.set(code, { info: null, state: null, intents: [] });
    return this.rooms.get(code);
  }

  subscribe(key, fn) {
    if (!this.subs.has(key)) this.subs.set(key, new Set());
    this.subs.get(key).add(fn);
    return () => this.subs.get(key)?.delete(fn);
  }

  fire(key) {
    for (const fn of [...(this.subs.get(key) ?? [])]) fn();
  }
}

export class MemoryBackend {
  constructor(db) {
    this.db = db;
  }

  async createRoom(code, hostName, hostColor, mode = 'classic') {
    const r = this.db.room(code);
    if (r.info !== null) throw new RoomException(`Codul ${code} este deja folosit.`);
    const host = { id: 'p0', name: hostName, color: hostColor, role: RoomRole.HOST };
    r.info = RoomRules.toMap({ stage: RoomStage.LOBBY, hostId: 'p0', players: [host], mode });
    this.db.fire(`info:${code}`);
    return host;
  }

  async joinRoom(code, name, color) {
    const r = this.db.room(code);
    const info = RoomRules.fromMap(r.info);
    const err = RoomRules.joinError(info, color);
    if (err) throw new RoomException(err);
    const seat = RoomRules.freeSeat(info.players);
    const me = { id: seat, name, color, role: RoomRole.GUEST };
    r.info = RoomRules.toMap({ ...info, players: [...info.players, me] });
    this.db.fire(`info:${code}`);
    return me;
  }

  async addBot(code, hostId) {
    const r = this.db.room(code);
    const info = RoomRules.fromMap(r.info);
    if (!info || info.stage !== RoomStage.LOBBY || (hostId && info.hostId !== hostId)) {
      throw new RoomException('Nu se mai poate adăuga un robot (masa e plină).');
    }
    const seat = RoomRules.freeSeat(info.players);
    const color = RoomRules.freeColor(info.players);
    if (!seat || !color) throw new RoomException('Nu se mai poate adăuga un robot (masa e plină).');
    const bots = info.players.filter((p) => p.role === RoomRole.BOT).length;
    r.info = RoomRules.toMap({ ...info, players: [...info.players, { id: seat, name: `Robot ${bots + 1}`, color, role: RoomRole.BOT }] });
    this.db.fire(`info:${code}`);
  }

  async leaveRoom(code, playerId, finalState) {
    const room = this.db.rooms.get(code) ?? null;
    const next = roomAfterLeave(room, playerId, finalState);
    if (next === undefined) {
      if (RoomRules.fromMap(room.info)?.players.some((p) => p.id === playerId && p.role !== RoomRole.BOT)) {
        throw new RoomException('Ieșirea din cameră nu a fost confirmată.');
      }
      return;
    }
    if (next === null) this.db.rooms.delete(code);
    else Object.assign(room, next);
    this.db.fire(`info:${code}`);
  }

  async peekInfo(code) {
    return RoomRules.fromMap(this.db.room(code).info);
  }

  listenInfo(code, cb) {
    const key = `info:${code}`;
    const fn = () => {
      const room = this.db.rooms.get(code);
      cb(RoomRules.fromMap(room?.info), room?.state);
    };
    const off = this.db.subscribe(key, fn);
    fn();
    return off;
  }

  async publishState(code, json, hostId, startingPlayers) {
    const room = this.db.rooms.get(code) ?? null;
    const next = roomWithState(room, hostId, json, startingPlayers);
    if (!next) return false;
    Object.assign(room, next);
    this.db.fire(`info:${code}`);
    return true;
  }

  sendIntent(code, json) {
    this.db.room(code).intents.push(json);
    this.db.fire(`intents:${code}`);
    return Promise.resolve();
  }

  /** Livrează fiecare intenție o singură dată, în ordine. */
  listenIntents(code, cb, hostId) {
    const r = this.db.room(code);
    const drain = () => {
      while (r.info?.hostId === hostId && r.intents.length) cb(r.intents.shift());
    };
    const off = this.db.subscribe(`intents:${code}`, drain);
    drain();
    return off;
  }
}
