// Backend în memorie cu aceeași interfață ca FirebaseRoomBackend (fără rețea), pentru teste.
import { RoomException, RoomRole, RoomRules, RoomStage } from '../js/online/protocol.js';

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

  async createRoom(code, hostName, hostColor) {
    const r = this.db.room(code);
    if (r.info !== null) throw new RoomException(`Codul ${code} este deja folosit.`);
    const host = { id: 'p0', name: hostName, color: hostColor, role: RoomRole.HOST };
    r.info = RoomRules.toMap({ stage: RoomStage.LOBBY, hostId: 'p0', players: [host] });
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

  async addBot(code) {
    const r = this.db.room(code);
    const info = RoomRules.fromMap(r.info);
    if (!info || info.stage !== RoomStage.LOBBY) throw new RoomException('Nu se mai poate adăuga un robot (masa e plină).');
    const seat = RoomRules.freeSeat(info.players);
    const color = RoomRules.freeColor(info.players);
    if (!seat || !color) throw new RoomException('Nu se mai poate adăuga un robot (masa e plină).');
    const bots = info.players.filter((p) => p.role === RoomRole.BOT).length;
    r.info = RoomRules.toMap({ ...info, players: [...info.players, { id: seat, name: `Robot ${bots + 1}`, color, role: RoomRole.BOT }] });
    this.db.fire(`info:${code}`);
  }

  async setStage(code, stage) {
    const r = this.db.room(code);
    r.info = { ...r.info, stage };
    this.db.fire(`info:${code}`);
  }

  async peekInfo(code) {
    return RoomRules.fromMap(this.db.room(code).info);
  }

  listenInfo(code, cb) {
    const r = this.db.room(code);
    const key = `info:${code}`;
    const fn = () => cb(RoomRules.fromMap(r.info));
    const off = this.db.subscribe(key, fn);
    fn();
    return off;
  }

  publishState(code, json) {
    this.db.room(code).state = json;
    this.db.fire(`state:${code}`);
    return Promise.resolve();
  }

  listenState(code, cb) {
    const r = this.db.room(code);
    const fn = () => {
      if (typeof r.state === 'string') cb(r.state);
    };
    const off = this.db.subscribe(`state:${code}`, fn);
    fn();
    return off;
  }

  sendIntent(code, json) {
    this.db.room(code).intents.push(json);
    this.db.fire(`intents:${code}`);
    return Promise.resolve();
  }

  /** Livrează fiecare intenție o singură dată, în ordine. */
  listenIntents(code, cb) {
    const r = this.db.room(code);
    const drain = () => {
      while (r.intents.length) cb(r.intents.shift());
    };
    const off = this.db.subscribe(`intents:${code}`, drain);
    drain();
    return off;
  }
}
