// Backend Firebase Realtime Database (SDK web, modular). Structura e identică cu aplicația Android:
//  - rooms/{cod}/info    : stadiul camerei, gazda, locurile (nume, culoare, rol)
//  - rooms/{cod}/state   : starea oficială a jocului (JSON), scrisă doar de gazdă
//  - rooms/{cod}/intents : coada de intenții trimise de oaspeți (JSON), ștearsă de gazdă după livrare
// Port din FirebaseLobbyBridge.kt. SDK-ul se încarcă doar când intri în jocul online.

import { initializeApp } from 'https://cdn.jsdelivr.net/npm/@firebase/app@0.10.3/+esm';   // aceeași instanță ca @firebase/database@1.0.5
import {
  getDatabase, ref, get, set, push, remove, onValue, onChildAdded, runTransaction,
} from 'https://cdn.jsdelivr.net/npm/firebase@10.12.2/database/+esm';
import { FIREBASE_CONFIG, ONLINE_DATABASE_URL } from '../config.js';
import { RoomException, RoomRole, RoomRules, RoomStage } from './protocol.js';

export class FirebaseRoomBackend {
  constructor(db) {
    this.db = db;
  }

  /** Inițializează aplicația Firebase și baza de date din regiunea configurată. */
  static connect() {
    const app = initializeApp(FIREBASE_CONFIG, 'privatizare-web');
    return new FirebaseRoomBackend(getDatabase(app, ONLINE_DATABASE_URL));
  }

  room(code) {
    return ref(this.db, `rooms/${code}`);
  }

  infoRef(code) {
    return ref(this.db, `rooms/${code}/info`);
  }

  /** Creează camera; gazda e pe locul p0. Eșuează dacă există deja. */
  async createRoom(code, hostName, hostColor) {
    const host = { id: 'p0', name: hostName, color: hostColor, role: RoomRole.HOST };
    const info = { stage: RoomStage.LOBBY, hostId: 'p0', players: [host] };
    const res = await runTransaction(this.infoRef(code), (cur) => {
      if (cur !== null) return undefined;      // abort: codul există deja
      return RoomRules.toMap(info);
    });
    if (!res.committed) throw new RoomException(`Codul ${code} este deja folosit.`);
    return host;
  }

  /** Ocupă primul loc liber. Eșuează dacă camera lipsește, e plină, a pornit sau culoarea e luată. */
  async joinRoom(code, name, color) {
    let joined = null;
    const res = await runTransaction(this.infoRef(code), (cur) => {
      joined = null;
      // Prima rulare vine cu cache-ul local gol: returnăm null ca serverul să trimită valoarea reală.
      if (cur === null) return null;
      const info = RoomRules.fromMap(cur);
      if (!info) return undefined;
      if (RoomRules.joinError(info, color) !== null) return undefined;
      const seat = RoomRules.freeSeat(info.players);
      if (!seat) return undefined;
      joined = { id: seat, name, color, role: RoomRole.GUEST };
      return RoomRules.toMap({ ...info, players: [...info.players, joined] });
    });
    if (res.committed && joined) return joined;
    const info = RoomRules.fromMap(res.snapshot?.val());
    throw new RoomException(RoomRules.joinError(info, color) ?? 'Nu se poate intra în cameră.');
  }

  /** Gazda adaugă un robot pe primul loc liber, cu prima culoare liberă. */
  async addBot(code) {
    const res = await runTransaction(this.infoRef(code), (cur) => {
      if (cur === null) return null;
      const info = RoomRules.fromMap(cur);
      if (!info || info.stage !== RoomStage.LOBBY) return undefined;
      const seat = RoomRules.freeSeat(info.players);
      const color = RoomRules.freeColor(info.players);
      if (!seat || !color) return undefined;
      const bots = info.players.filter((p) => p.role === RoomRole.BOT).length;
      const bot = { id: seat, name: `Robot ${bots + 1}`, color, role: RoomRole.BOT };
      return RoomRules.toMap({ ...info, players: [...info.players, bot] });
    });
    if (!res.committed) throw new RoomException('Nu se mai poate adăuga un robot (masa e plină).');
  }

  async setStage(code, stage) {
    await set(ref(this.db, `rooms/${code}/info/stage`), stage);
  }

  async peekInfo(code) {
    try {
      const snap = await get(this.infoRef(code));
      return RoomRules.fromMap(snap.val());
    } catch {
      return null;
    }
  }

  /** Ascultă camera. Returnează funcția care oprește ascultarea. */
  listenInfo(code, onInfo) {
    return onValue(
      this.infoRef(code),
      (snap) => onInfo(RoomRules.fromMap(snap.val())),
      () => onInfo(null),
    );
  }

  publishState(code, json) {
    return set(ref(this.db, `rooms/${code}/state`), json);
  }

  listenState(code, onState) {
    return onValue(ref(this.db, `rooms/${code}/state`), (snap) => {
      if (typeof snap.val() === 'string') onState(snap.val());
    });
  }

  sendIntent(code, json) {
    return push(ref(this.db, `rooms/${code}/intents`), json);
  }

  /** Gazda primește fiecare intenție o singură dată; se șterge după livrare. */
  listenIntents(code, onIntent) {
    const intents = ref(this.db, `rooms/${code}/intents`);
    return onChildAdded(intents, (snap) => {
      const json = snap.val();
      remove(snap.ref);
      if (typeof json === 'string') onIntent(json);
    });
  }
}
