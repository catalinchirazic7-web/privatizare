// Sesiunea online a acestui dispozitiv: creează/intră într-o cameră și, odată pornit meciul,
// conectează Game la rețea. Port din OnlineController.kt.
//  - Gazda rulează motorul: primește intențiile, le aplică, apoi publică starea oficială.
//  - Oaspetele trimite doar intenții și afișează starea primită.

import { RoomCode, RoomRole, RoomRules, RoomStage, IntentCodec, SnapshotCodec } from './protocol.js';
import { applyEvent } from './events.js';
import { rollDie } from '../core/dice.js';

export class OnlineController {
  /**
   * @param {object} backend  FirebaseRoomBackend sau un backend în memorie (teste)
   * @param {import('../game/game.js').Game} game
   */
  constructor(backend, game, { hostDie = () => rollDie(), newCode = () => RoomCode.generate() } = {}) {
    this.backend = backend;
    this.game = game;
    this.hostDie = hostDie;
    this.newCode = newCode;

    this.code = null;
    this.me = null;
    this.info = null;
    this.error = null;
    this.busy = false;
    this.inGame = false;
    this.takenColors = new Set();

    this.closers = [];
    this.lastPublished = null;
    this.peekedCode = null;
    this.unsubGame = null;
    this.listeners = new Set();
  }

  onChange(fn) {
    this.listeners.add(fn);
    return () => this.listeners.delete(fn);
  }

  emit() {
    for (const fn of [...this.listeners]) fn(this);
  }

  get isHost() {
    return this.me?.role === RoomRole.HOST;
  }

  get isInRoom() {
    return this.code !== null && this.me !== null;
  }

  /** Apelat după un timp fără răspuns: oprește așteptarea și explică posibila cauză. */
  timeoutIfBusy() {
    if (!this.busy) return;
    this.busy = false;
    this.error =
      'Baza de date nu răspunde. Verifică internetul și că Realtime Database este activă ' +
      '(regiunea ei trebuie să fie cea din config.js).';
    this.emit();
  }

  /** Apelat când se schimbă codul scris: culorile deja luate se dezactivează. */
  peekRoom(rawCode) {
    const c = rawCode.trim().toUpperCase();
    if (!RoomCode.isValid(c)) {
      this.peekedCode = null;
      this.takenColors = new Set();
      this.emit();
      return;
    }
    this.peekedCode = c;
    this.backend.peekInfo(c).then((room) => {
      if (this.peekedCode !== c) return;
      this.takenColors = new Set((room?.players ?? []).map((p) => p.color));
      this.emit();
    });
  }

  clearError() {
    this.error = null;
    this.emit();
  }

  createRoom(name, color) {
    if (this.busy) return;
    this.busy = true;
    this.error = null;
    this.emit();
    return this.tryCreate(name.trim(), color, 5);
  }

  async tryCreate(name, color, attemptsLeft) {
    const c = this.newCode();
    try {
      const player = await this.backend.createRoom(c, name, color);
      this.code = c;
      this.me = player;
      this.busy = false;
      this.listenInfo(c);
      this.emit();
    } catch (e) {
      if (/deja folosit/.test(e.message) && attemptsLeft > 1) this.tryCreate(name, color, attemptsLeft - 1);
      else this.fail(e);
    }
  }

  async joinRoom(rawCode, name, color) {
    if (this.busy) return;
    const c = rawCode.trim().toUpperCase();
    if (!RoomCode.isValid(c)) {
      this.error = 'Codul are 4 caractere (litere și cifre).';
      this.emit();
      return;
    }
    this.busy = true;
    this.error = null;
    this.emit();
    try {
      const player = await this.backend.joinRoom(c, name.trim(), color);
      this.code = c;
      this.me = player;
      this.busy = false;
      this.listenInfo(c);
      this.emit();
    } catch (e) {
      this.fail(e);
    }
  }

  addBot() {
    const c = this.code;
    if (!c || !this.isHost) return;
    this.backend.addBot(c).catch((e) => {
      this.error = e.message;
      this.emit();
    });
  }

  /** Gazda pornește meciul: locurile din cameră devin jucătorii partidei. */
  startMatch() {
    const c = this.code;
    const room = this.info;
    if (!c || !room || !this.isHost || !RoomRules.canStart(room)) return;
    const setup = room.players.map((p) => ({ name: p.name, colorKey: p.color, isBot: p.role === RoomRole.BOT }));
    this.game.startGame(setup);
    this.game.enterOnlineHost(this.me.id);
    this.closers.push(this.backend.listenIntents(c, (json) => this.onIntent(json)));
    this.unsubGame = this.game.onChange(() => this.publishNow());
    this.publishNow();
    this.inGame = true;
    this.backend.setStage(c, RoomStage.PLAYING).catch((e) => {
      this.error = e.message;
      this.emit();
    });
    this.emit();
  }

  /** Gazda: publică starea oficială doar dacă s-a schimbat. */
  publishNow() {
    const c = this.code;
    if (!c || !this.isHost) return;
    const snap = this.game.snapshot();
    if (!snap) return;
    const json = SnapshotCodec.encode(snap);
    if (json === this.lastPublished) return;
    this.lastPublished = json;
    this.backend.publishState(c, json).catch((e) => console.error('Publicare eșuată', e));
  }

  onIntent(json) {
    const event = IntentCodec.decode(json);
    if (!event) return;
    applyEvent(this.game, event, () => this.hostDie());
  }

  listenInfo(c) {
    this.closers.push(
      this.backend.listenInfo(c, (room) => {
        this.info = room;
        if (room && !this.isHost && room.stage === RoomStage.PLAYING && !this.inGame) this.beginGuestGame(c);
        this.emit();
      }),
    );
  }

  beginGuestGame(c) {
    const myId = this.me?.id;
    if (!myId) return;
    this.game.enterOnlineGuest(myId, (event) => this.backend.sendIntent(c, IntentCodec.encode(event)));
    this.closers.push(
      this.backend.listenState(c, (json) => {
        try {
          this.game.applySnapshot(SnapshotCodec.decode(json));
        } catch (e) {
          console.error('Stare invalidă de la gazdă', e);
        }
      }),
    );
    this.inGame = true;
    this.emit();
  }

  leave() {
    this.closers.forEach((close) => {
      try {
        if (typeof close === 'function') close();
      } catch {
        /* ignorat: conexiunea se închide oricum */
      }
    });
    this.closers = [];
    if (this.unsubGame) this.unsubGame();
    this.unsubGame = null;
    this.game.leaveOnline();
    this.code = null;
    this.me = null;
    this.info = null;
    this.inGame = false;
    this.busy = false;
    this.lastPublished = null;
    this.emit();
  }

  fail(e) {
    this.busy = false;
    this.error = e.message || 'Eroare necunoscută.';
    this.emit();
  }
}
