// Sesiunea online a acestui dispozitiv: creează/intră într-o cameră și, odată pornit meciul,
// conectează Game la rețea. Port din OnlineController.kt.
//  - Gazda rulează motorul: primește intențiile, le aplică, apoi publică starea oficială.
//  - Oaspetele trimite doar intenții și afișează starea primită.

import { ROOM_MODES, RoomCode, RoomException, RoomRole, RoomRules, RoomStage, IntentCodec, SnapshotCodec } from './protocol.js';
import { applyEvent } from './events.js';
import { rollDie } from '../core/dice.js';

// Transformarea comună backend-urilor. undefined anulează tranzacția.
export function roomAfterLeave(room, playerId, finalState) {
  if (room === null) return null;
  const info = RoomRules.fromMap(room.info);
  const leaving = info?.players.find((p) => p.id === playerId && p.role !== RoomRole.BOT);
  if (!leaving) return undefined;

  const successor = info.players.find((p) => p.id !== playerId && p.role !== RoomRole.BOT);
  if (!successor) return null;
  const hostLeaving = info.hostId === playerId;
  const lobby = info.stage === RoomStage.LOBBY;
  const players = { ...room.info.players };
  if (lobby) delete players[playerId];
  else players[playerId] = { ...players[playerId], role: RoomRole.BOT };
  if (!hostLeaving) return { ...room, info: { ...room.info, players } };

  players[successor.id] = { ...players[successor.id], role: RoomRole.HOST };
  const next = { ...room, info: { ...room.info, players, hostId: successor.id } };
  if (lobby) return next;
  // Un oaspete poate cere ieșirea înainte să afle că tocmai a devenit gazdă.
  // În acel caz, nu a rulat motorul, deci starea din cameră rămâne cea oficială.
  const json = finalState ?? room.state;
  if (typeof json !== 'string') return undefined;
  try {
    const snapshot = JSON.parse(json);
    for (const player of snapshot.players) {
      if (players[player.id]?.role === RoomRole.BOT) player.bot = true;
    }
    return { ...next, state: JSON.stringify(snapshot) };
  } catch {
    return undefined;
  }
}

export function roomWithState(room, hostId, json, startingPlayers) {
  if (room === null) return null;
  const info = RoomRules.fromMap(room.info);
  if (!info || info.hostId !== hostId) return undefined;
  if (startingPlayers) {
    if (info.stage !== RoomStage.LOBBY || JSON.stringify(info.players) !== JSON.stringify(startingPlayers)) return undefined;
    return { ...room, info: { ...room.info, stage: RoomStage.PLAYING }, state: json };
  }
  if (info.stage !== RoomStage.PLAYING) return undefined;
  return { ...room, state: json };
}

export class OnlineController {
  /**
   * @param {object} backend  FirebaseRoomBackend sau un backend în memorie (teste)
   * @param {import('../game/game.js').Game} game
   */
  constructor(backend, game, { hostDie = () => rollDie(), newCode = () => RoomCode.generate(), boardLayout = 'old' } = {}) {
    this.backend = backend;
    this.game = game;
    this.hostDie = hostDie;
    this.newCode = newCode;
    this.boardLayout = boardLayout;
    this.marea = false;   // camera curentă are cheia Marea (MP-); se află la creare sau la intrare

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
    this.unsubIntents = null;
    this.starting = false;
    this.leaving = false;
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

  /** Codurile afișate rămân scurte. Cheile Marea sunt separate de camerele compatibile Android. */
  roomKey(code) {
    return this.marea ? `MP-${code}` : code;
  }

  /** Caută camera după cod: întâi cheia obișnuită, apoi cea Marea. Null dacă nu există. */
  async findRoom(code) {
    const plain = await this.backend.peekInfo(code);
    if (plain) return { marea: false, info: plain };
    const marea = await this.backend.peekInfo(`MP-${code}`);
    return marea ? { marea: true, info: marea } : null;
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
    this.findRoom(c).then((found) => {
      if (this.peekedCode !== c) return;
      this.takenColors = new Set((found?.info.players ?? []).map((p) => p.color));
      this.emit();
    });
  }

  clearError() {
    this.error = null;
    this.emit();
  }

  createRoom(name, color, mode = this.boardLayout === 'marea' ? 'marea' : 'classic') {
    if (this.busy || this.leaving) return;
    this.busy = true;
    this.error = null;
    this.emit();
    return this.tryCreate(name.trim(), color, mode, 5);
  }

  async tryCreate(name, color, mode, attemptsLeft) {
    const c = this.newCode();
    this.marea = mode === 'marea';
    try {
      const player = await this.backend.createRoom(this.roomKey(c), name, color, mode);
      this.code = c;
      this.me = player;
      this.busy = false;
      this.listenInfo(c);
      this.emit();
    } catch (e) {
      if (/deja folosit/.test(e.message) && attemptsLeft > 1) this.tryCreate(name, color, mode, attemptsLeft - 1);
      else this.fail(e);
    }
  }

  async joinRoom(rawCode, name, color) {
    if (this.busy || this.leaving) return;
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
      this.marea = (await this.findRoom(c))?.marea ?? false;
      const player = await this.backend.joinRoom(this.roomKey(c), name.trim(), color);
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
    if (!c || !this.isHost || this.starting) return;
    this.backend.addBot(this.roomKey(c), this.me.id).catch((e) => {
      this.error = e.message;
      this.emit();
    });
  }

  /** Gazda pornește meciul: locurile din cameră devin jucătorii partidei. */
  startMatch() {
    const c = this.code;
    const room = this.info;
    if (!c || !room || !this.isHost || this.starting || room.stage !== RoomStage.LOBBY || !RoomRules.canStart(room)) return;
    const setup = room.players.map((p) => ({ id: p.id, name: p.name, colorKey: p.color, isBot: p.role === RoomRole.BOT }));
    const mode = ROOM_MODES[room.mode];
    this.game.startGame(setup, null, null, mode.dice, mode.layout, mode.rapid);
    this.game.enterOnlineHost(this.me.id);
    this.starting = true;
    this.lastPublished = SnapshotCodec.encode(this.game.snapshot());
    const id = this.me.id;
    return this.backend.publishState(this.roomKey(c), this.lastPublished, id, room.players).then((committed) => {
      if (!committed) throw new RoomException('Camera s-a schimbat. Încearcă din nou.');
    }).catch((e) => {
      if (this.code !== c || this.me?.id !== id || !this.starting) return;
      this.starting = false;
      this.lastPublished = null;
      this.game.leaveOnline();
      this.fail(e);
    });
  }

  /** Gazda: publică starea oficială doar dacă s-a schimbat. */
  publishNow() {
    const c = this.code;
    if (!c || !this.isHost || !this.inGame || this.leaving) return;
    const snap = this.game.snapshot();
    if (!snap) return;
    const json = SnapshotCodec.encode(snap);
    if (json === this.lastPublished) return;
    this.lastPublished = json;
    this.backend.publishState(this.roomKey(c), json, this.me.id).catch((e) => {
      if (this.code === c && this.lastPublished === json) this.lastPublished = null;
      console.error('Publicare eșuată', e);
    });
  }

  onIntent(json) {
    const event = IntentCodec.decode(json);
    if (!event || !this.isHost || this.leaving) return;
    if (!this.info?.players.some((p) => p.id === event.playerId && p.role === RoomRole.GUEST)) return;
    applyEvent(this.game, event, () => this.hostDie());
  }

  listenInfo(c) {
    let infoKey;
    let appliedState;
    this.closers.push(
      this.backend.listenInfo(this.roomKey(c), (room, json) => {
        if (this.code !== c || this.leaving) return;
        const nextInfoKey = JSON.stringify(room);
        const changed = infoKey !== nextInfoKey;
        infoKey = nextInfoKey;
        this.info = room;
        const seat = room?.players.find((p) => p.id === this.me.id);
        if (!seat || seat.role === RoomRole.BOT) {
          if (changed) this.emit();
          return;
        }
        if (room.stage !== RoomStage.PLAYING) {
          this.me = seat;
          if (changed) this.emit();
          return;
        }

        if (room.hostId === seat.id) {
          if (!this.inGame || !this.isHost) {
            if (typeof json !== 'string') return;
            // Gazda nouă încarcă starea din aceeași citire ca schimbarea de rol.
            if (!this.starting && !this.applyState(json)) return;
            this.me = seat;
            this.inGame = true;
            this.starting = false;
            this.syncBots();
            this.game.enterOnlineHost(seat.id);
            this.unsubGame = this.game.onChange(() => this.publishNow());
            this.publishNow();
            this.unsubIntents = this.backend.listenIntents(this.roomKey(c), (intent) => this.onIntent(intent), seat.id);
            this.emit();
          } else if (changed) {
            this.syncBots();
            this.emit();
          }
          return;
        }

        if (!this.inGame) {
          if (typeof json !== 'string' || !this.applyState(json)) return;
          appliedState = json;
          this.me = seat;
          this.game.enterOnlineGuest(seat.id, (event) => this.backend.sendIntent(this.roomKey(c), IntentCodec.encode(event)));
          this.inGame = true;
          this.emit();
        } else if (json !== appliedState && typeof json === 'string' && this.applyState(json)) {
          appliedState = json;
        }
      }),
    );
  }

  applyState(json) {
    try {
      this.game.applySnapshot(SnapshotCodec.decode(json));
      return true;
    } catch (e) {
      console.error('Stare invalidă de la gazdă', e);
      return false;
    }
  }

  syncBots() {
    const botIds = this.info.players.filter((p) => p.role === RoomRole.BOT).map((p) => p.id);
    if (botIds.length) this.game.replaceOnlinePlayersWithBots(botIds);
  }

  async leave() {
    if (!this.isInRoom || this.leaving) return;
    const { code, me } = this;
    this.leaving = true;
    this.error = null;
    const snap = this.isHost && (this.inGame || this.starting) ? this.game.snapshot() : null;
    const finalState = snap ? SnapshotCodec.encode(snap) : null;

    this.unsubIntents?.();
    this.unsubIntents = null;
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
    this.starting = false;
    this.busy = false;
    this.lastPublished = null;
    this.emit();
    try {
      await this.backend.leaveRoom(this.roomKey(code), me.id, finalState);
    } catch (e) {
      this.error = e.message || 'Ieșirea din cameră nu a fost confirmată.';
    } finally {
      this.leaving = false;
      this.emit();
    }
  }

  fail(e) {
    this.busy = false;
    this.error = e.message || 'Eroare necunoscută.';
    this.emit();
  }
}
