// Starea unei partide: rulează TurnEngine și expune dialogurile active. Port din GameViewModel.kt.
// Fiecare metodă care schimbă starea apelează emit(), ca interfața să se redeseneze.

import { GameRules, distributeStartingMoney } from '../core/rules.js';
import { TurnEngine, debtEquals } from '../core/turnEngine.js';
import { TILE } from '../core/model.js';
import { netWorth, rankPlayers } from '../core/actions.js';
import { botDecideBuy, botLiquidationSales } from '../core/botAI.js';
import { catalinDecideBuy } from '../core/catalinAI.js';
import { rollBotDie, CATALIN_LUCK } from '../core/dice.js';
import { EV } from '../online/protocol.js';

export class Game {
  /**
   * @param {object} catalog  rezultatul loadCatalog()
   * @param {{botDie?: (luck: number) => number, rng?: () => number}} opts
   */
  constructor(catalog, opts = {}) {
    this.catalog = catalog;
    this.botDie = opts.botDie ?? ((luck = 0) => rollBotDie(Math.random, luck));
    this.engine = new TurnEngine(catalog, opts.rng ?? Math.random);
    this.listeners = new Set();
    this.version = 0;

    // mod online
    this.myPlayerId = null;
    this.intentSender = null;

    this.game = null;            // { turn, players, bankMoney, history }
    this.currentIndex = 0;
    this.lastDice = [];
    this.rollNonce = 0;
    this.diceCount = 1;       // 1 sau 2 zaruri (doar jocul local)
    this.boardLayout = 'old';  // tabla: 'old' (veche) sau 'new' (nouă)
    this.winnerName = null;

    this.offer = null;           // { playerId, propertyId, option }
    this.feeOffer = null;
    this.rentDue = null;         // { payerId, ownerId, propertyId, amount }
    this.debt = null;            // { debtorId, amount, creditorId, reason }
    this.shownCard = null;       // { kind, text, outcome, amount, destination, cardId }
    this.pendingMove = null;
    this.pendingDraw = null;     // 'LOTO' | 'TELEX'
    this.turnAwaitingEnd = false;
    this.skipBanner = null;
    this.debtAcknowledged = null;
    this.handoverTo = null;

    this.deadlineMs = null;
    this.finalRanking = [];
  }

  /* ---------- notificări ---------- */

  onChange(fn) {
    this.listeners.add(fn);
    return () => this.listeners.delete(fn);
  }

  emit() {
    this.version++;
    for (const fn of [...this.listeners]) fn(this);
  }

  /* ---------- mod online ---------- */

  get isOnline() {
    return this.myPlayerId !== null;
  }

  /** Oaspete: nu rulează motorul, doar trimite intenții și afișează starea primită. */
  get isGuest() {
    return this.intentSender !== null;
  }

  enterOnlineHost(myId) {
    this.myPlayerId = myId;
    this.intentSender = null;
    this.emit();
  }

  enterOnlineGuest(myId, sender) {
    this.myPlayerId = myId;
    this.intentSender = sender;
    this.emit();
  }

  leaveOnline() {
    this.myPlayerId = null;
    this.intentSender = null;
    this.emit();
  }

  /** Pe oaspete: trimite intenția și returnează true. Pe gazdă/local: false. */
  guestSend(build) {
    if (!this.intentSender) return false;
    if (this.myPlayerId === null) return true;
    this.intentSender(build(this.myPlayerId));
    return true;
  }

  tileOf(propertyId) {
    return this.catalog.board.findIndex((t) => t.type === TILE.PROPERTY && t.propertyId === propertyId);
  }

  /* ---------- citiri ---------- */

  get currentPlayer() {
    return this.game?.players[this.currentIndex] ?? null;
  }

  get pendingOffer() {
    return this.offer;
  }

  get isOver() {
    return this.winnerName !== null;
  }

  /** Dialogul activ, sau null dacă tura poate continua. */
  get activeDialog() {
    const g = this.game;
    if (!g) return null;
    const actions = this.catalog.actions;
    if (this.debt) {
      if (!this.isOnline && !debtEquals(this.debtAcknowledged, this.debt)) {
        const name = g.players.find((p) => p.id === this.debt.debtorId)?.name;
        if (!name) return null;
        return { type: 'LIQUIDATION_HANDOVER', debtorName: name };
      }
      return { type: 'DEBT', debt: this.debt };
    }
    if (this.offer) {
      return { type: 'BUY', offer: this.offer, property: actions[this.offer.propertyId] };
    }
    if (this.feeOffer) {
      const property = actions[this.feeOffer.propertyId];
      return { type: 'BANK_FEE', offer: this.feeOffer, property, taxAmount: property.titleTax };
    }
    if (this.rentDue) {
      const owner = g.players.find((p) => p.id === this.rentDue.ownerId);
      if (!owner) return null;
      return { type: 'RENT', due: this.rentDue, property: actions[this.rentDue.propertyId], owner, rentAmount: this.rentDue.amount };
    }
    if (this.shownCard) return { type: 'CARD', card: this.shownCard };
    if (this.pendingDraw) return { type: 'DRAW', kind: this.pendingDraw };
    if (this.handoverTo) {
      const name = g.players.find((p) => p.id === this.handoverTo)?.name;
      if (!name) return null;
      return { type: 'HANDOVER', playerName: name };
    }
    return null;
  }

  /** Id-ul proprietății de pe căsuța pe care stă jucătorul, sau null. */
  landedPropertyId(playerId) {
    const p = this.game?.players.find((x) => x.id === playerId);
    if (!p) return null;
    const tile = this.catalog.board[p.position];
    return tile && tile.type === TILE.PROPERTY ? tile.propertyId : null;
  }

  propertyAtTile(index) {
    const tile = this.catalog.board[index];
    return tile && tile.type === TILE.PROPERTY ? tile.propertyId : null;
  }

  /** Acțiunile vândabile ale unui jucător: anexa de sus și întreaga acțiune. */
  sellableFor(playerId) {
    const p = this.game?.players.find((x) => x.id === playerId);
    if (!p) return [];
    const out = [];
    for (const [propId, level] of Object.entries(p.ownedProperties)) {
      if (level > 0) out.push({ propertyId: propId, annexIndex: level }, { propertyId: propId, annexIndex: null });
      else out.push({ propertyId: propId, annexIndex: null });
    }
    return out;
  }

  /** Snapshot pentru salvare locală sau publicare online (gazda). Null înainte de start. */
  snapshot() {
    const g = this.game;
    if (!g) return null;
    const offerSnap = (o) => (o ? { playerId: o.playerId, propertyId: o.propertyId, annexIndex: o.option.type === 'ANNEX' ? o.option.annex.index : null } : null);
    return {
      state: { turn: g.turn, players: g.players, bankMoney: g.bankMoney, history: g.history, stats: g.stats ?? {} },
      currentIndex: this.currentIndex,
      diceCount: this.diceCount,
      boardLayout: this.boardLayout,
      lastDice: this.lastDice,
      winnerName: this.winnerName,
      turnAwaitingEnd: this.turnAwaitingEnd,
      skipBanner: this.skipBanner,
      offer: offerSnap(this.offer),
      feeOffer: offerSnap(this.feeOffer),
      rentDue: this.rentDue,
      debt: this.debt,
      card: this.shownCard,
      pendingDraw: this.pendingDraw,
    };
  }

  /** Oaspete: înlocuiește starea locală cu cea oficială de la gazdă. */
  applySnapshot(s) {
    const actions = this.catalog.actions;
    const toOffer = (o) => {
      if (!o) return null;
      const prop = actions[o.propertyId];
      if (!prop) return null;
      const option =
        o.annexIndex == null
          ? { type: 'TITLE', price: prop.titlePrice }
          : { type: 'ANNEX', annex: prop.annexes.find((a) => a.index === o.annexIndex) };
      return { playerId: o.playerId, propertyId: o.propertyId, option };
    };
    this.game = s.state;
    this.currentIndex = s.currentIndex;
    this.diceCount = s.diceCount === 2 ? 2 : 1;   // salvările vechi au un singur zar
    this.boardLayout = s.boardLayout === 'new' ? 'new' : 'old';   // salvările vechi sunt pe tabla veche
    if (!sameDice(s.lastDice, this.lastDice)) this.rollNonce++;
    this.lastDice = s.lastDice;
    this.winnerName = s.winnerName;
    this.turnAwaitingEnd = s.turnAwaitingEnd;
    this.skipBanner = s.skipBanner;
    this.offer = toOffer(s.offer);
    this.feeOffer = toOffer(s.feeOffer);
    this.rentDue = s.rentDue;
    this.debt = s.debt;
    this.pendingDraw = s.pendingDraw;
    this.shownCard = s.card;
    this.handoverTo = null;
    this.debtAcknowledged = null;
    this.emit();
  }

  /* ---------- timp, pornire, restaurare ---------- */

  timeUp() {
    const g = this.game;
    if (!g || this.winnerName !== null) return;
    const ranked = rankPlayers(g.players, this.catalog.actions);
    this.finalRanking = ranked.map((p) => `${p.name}: ${netWorth(p, this.catalog.actions)} ECU`);
    this.winnerName = ranked[0].name;
    this.deadlineMs = null;
    this.emit();
  }

  /** Reia o partidă salvată. leftMs = timpul rămas, null = fără limită. */
  restore(s, leftMs) {
    this.applySnapshot(s);
    this.deadlineMs = leftMs == null ? null : Date.now() + leftMs;
  }

  timeLeftMs() {
    return this.deadlineMs == null ? null : Math.max(0, this.deadlineMs - Date.now());
  }

  /**
   * @param {{name, colorKey, isBot}[]} setup
   * @param {number|null} moneyPerPlayer  null = împărțire egală a tirajului
   * @param {number|null} timeLimitMinutes
   */
  startGame(setup, moneyPerPlayer = null, timeLimitMinutes = null, diceCount = 1, boardLayout = 'old') {
    if (diceCount !== 1 && diceCount !== 2) throw new Error('Un zar sau două');
    this.diceCount = diceCount;
    this.boardLayout = boardLayout === 'new' ? 'new' : 'old';
    this.deadlineMs = timeLimitMinutes ? Date.now() + timeLimitMinutes * 60_000 : null;
    this.finalRanking = [];
    if (!(setup.length >= 2 && setup.length <= 4)) throw new Error('Între 2 și 4 jucători');
    if (new Set(setup.map((s) => s.colorKey)).size !== setup.length) throw new Error('Culori duplicate');
    if (!setup.some((s) => !s.isBot)) throw new Error('Cel puțin un jucător trebuie să fie om');
    const dist = moneyPerPlayer == null ? distributeStartingMoney(setup.length) : distributeStartingMoney(setup.length, moneyPerPlayer);
    const players = setup.map((s, i) => ({
      id: `p${i}`,
      name: s.name,
      color: s.colorKey,
      money: dist.playerMoney,
      position: GameRules.START_POSITION,
      ownedProperties: {},
      skipTurns: 0,
      houseSince: 0,
      isBot: !!s.isBot,
      catalin: !!s.isBot && !!s.catalin,   // CatalinRobot: robot cu zarul în favoarea lui
    }));
    this.game = { turn: 1, players, bankMoney: dist.bankMoney, history: [], stats: {} };
    this.currentIndex = 0;
    this.offer = null;
    this.feeOffer = null;
    this.rentDue = null;
    this.debt = null;
    this.shownCard = null;
    this.pendingMove = null;
    this.pendingDraw = null;
    this.turnAwaitingEnd = false;
    this.winnerName = null;
    this.lastDice = [];
    this.skipBanner = null;
    this.handoverTo = null;
    this.debtAcknowledged = null;
    this.beginTurnIfSkipping();
    this.emit();
  }

  /* ---------- confirmări ---------- */

  acknowledgeLiquidation() {
    this.debtAcknowledged = this.debt;
    this.emit();
  }

  acknowledgeHandover() {
    if (this.handoverTo === null) return;
    this.handoverTo = null;
    this.emit();
  }

  /** Predarea telefonului: doar între cel puțin doi oameni pe același dispozitiv, și nu către un robot. */
  prepareHandover() {
    if (this.isOnline) return;
    const players = this.game?.players ?? [];
    const next = players[this.currentIndex];
    const humans = players.filter((p) => !p.isBot).length;
    this.handoverTo = humans >= 2 && next && !next.isBot ? next.id : null;
  }

  /** Jucătorul la rând stă la Casa de Odihnă: îi scade o tură fără zar. */
  beginTurnIfSkipping() {
    const g = this.game;
    if (!g) return;
    const p = g.players[this.currentIndex];
    if (!p) return;
    if (!(p.skipTurns > 0)) {
      if (this.engine.isWaitingAtHouse(g, this.currentIndex)) {
        this.skipBanner = `${p.name} așteaptă la Casa de Odihnă: iese primul cel care a ajuns înaintea lui`;
        this.turnAwaitingEnd = true;
      }
      return;
    }
    const result = this.engine.skipTurn(g, this.currentIndex);
    this.game = result.state;
    this.skipBanner = `${p.name} staționează la Casa de Odihnă! Ture rămase de sărit: ${p.skipTurns - 1}`;
    this.turnAwaitingEnd = true;
  }

  /* ---------- acțiuni ale jucătorului ---------- */

  /** Aruncă zarul și mută pionul. Blocat cât timp există un dialog activ. */
  roll(die) {
    // un zar (număr) sau două (listă); online rămâne la un singur zar, ca în aplicația Android
    const dice = Array.isArray(die) ? die : [die];
    if (this.guestSend((id) => ({ t: EV.ROLL, playerId: id, value: dice[0] }))) return;
    const g = this.game;
    if (!g) return;
    if (this.activeDialog || this.turnAwaitingEnd || this.winnerName !== null) return;
    const r = this.engine.playTurn(g, this.currentIndex, dice);
    this.game = r.state;
    this.lastDice = dice;
    this.rollNonce++;
    this.offer = r.offer;
    this.rentDue = r.rentDue;
    this.debt = r.debt;
    this.debtAcknowledged = null;
    this.shownCard = r.card;
    this.pendingMove = r.pendingMove;
    this.pendingDraw = r.pendingDraw;
    this.turnAwaitingEnd = true;
    this.checkWinner();
    this.emit();
  }

  acceptOffer() {
    const o = this.offer;
    if (!o) return;
    if (this.guestSend((id) => ({ t: EV.BUY, playerId: id, tile: this.tileOf(o.propertyId) }))) return;
    if (!this.game) return;
    this.game = this.engine.applyBuy(this.game, o);
    this.offer = null;
    this.turnAwaitingEnd = true;
    this.checkWinner();
    this.emit();
  }

  /** NU la titlu deschide balonul cu taxa; NU la anexă nu are taxă. */
  refuseOffer() {
    const o = this.offer;
    if (!o) return;
    if (this.guestSend((id) => ({ t: EV.DECLINE, playerId: id, tile: this.tileOf(o.propertyId) }))) return;
    this.offer = null;
    if (o.option.type === 'TITLE') this.feeOffer = o;
    else this.turnAwaitingEnd = true;
    this.emit();
  }

  acknowledgeBankFee() {
    if (this.guestSend((id) => ({ t: EV.ACK, playerId: id }))) return;
    const o = this.feeOffer;
    if (!o || !this.game) return;
    const r = this.engine.declineOffer(this.game, o);
    this.game = r.state;
    this.feeOffer = null;
    this.debt = r.debt;
    this.debtAcknowledged = null;
    this.turnAwaitingEnd = true;
    this.checkWinner();
    this.emit();
  }

  payRent() {
    if (this.guestSend((id) => ({ t: EV.RENT, playerId: id }))) return;
    const r = this.rentDue;
    if (!r || !this.game) return;
    const res = this.engine.payRent(this.game, r);
    this.game = res.state;
    this.rentDue = null;
    this.debt = res.debt;
    this.debtAcknowledged = null;
    this.turnAwaitingEnd = true;
    this.checkWinner();
    this.emit();
  }

  /** Trage cartonașul din pachetul pe care a ajuns jucătorul. */
  drawCard() {
    if (this.guestSend((id) => ({ t: EV.DRAW, playerId: id }))) return;
    const kind = this.pendingDraw;
    if (!kind || !this.game) return;
    const r = this.engine.drawCard(this.game, this.currentIndex, kind);
    this.game = r.state;
    this.pendingDraw = null;
    this.shownCard = r.card;
    this.pendingMove = r.pendingMove;
    this.debt = r.debt;
    this.turnAwaitingEnd = true;
    this.checkWinner();
    this.emit();
  }

  /** Confirmarea cartonașului. Dacă cere o mutare, o aplică acum. */
  dismissCard() {
    if (this.guestSend((id) => ({ t: EV.ACK, playerId: id }))) return;
    if (!this.shownCard) return;
    this.shownCard = null;
    const steps = this.pendingMove;
    this.pendingMove = null;
    if (steps != null && this.game) {
      const r = this.engine.moveBy(this.game, this.currentIndex, steps);
      this.game = r.state;
      this.offer = r.offer;
      this.rentDue = r.rentDue;
      this.debt = r.debt;
      this.shownCard = r.card;
      this.pendingMove = r.pendingMove;
      this.pendingDraw = r.pendingDraw;
    }
    this.turnAwaitingEnd = true;
    this.checkWinner();
    this.emit();
  }

  /** Cumpără următoarea anexă a unei proprietăți deținute. Nu încheie tura. */
  buyNextAnnex(propertyId) {
    if (this.guestSend((id) => ({ t: EV.ANNEX, playerId: id, tile: this.tileOf(propertyId) }))) return;
    const g = this.game;
    if (!g) return;
    const owner = this.engine.ownerOf(g, propertyId);
    if (!owner) return;
    const level = owner.ownedProperties[propertyId];
    const prop = this.catalog.actions[propertyId];
    const annex = prop.annexes.find((a) => a.index === level + 1);
    if (!annex) return;
    if (!this.turnAwaitingEnd || this.activeDialog) return;
    this.game = this.engine.applyBuy(g, { playerId: owner.id, propertyId, option: { type: 'ANNEX', annex } });
    this.emit();
  }

  /** Vânzările alese pentru datorie. Dacă datoria e stinsă, tura se poate încheia. */
  settleDebt(sales) {
    if (this.isGuest) {
      if (sales.length === 0) this.guestSend((id) => ({ t: EV.BANKRUPT, playerId: id }));
      else for (const s of sales) this.guestSend((id) => ({ t: EV.SELL, playerId: id, propertyId: s.propertyId, annexIndex: s.annexIndex }));
      return;
    }
    const d = this.debt;
    const g = this.game;
    if (!d || !g) return;
    const res = this.engine.settleDebt(g, d, sales);
    this.game = res.state;
    if (res.pending === null) {
      this.debt = null;
      this.debtAcknowledged = null;
      if (res.bankrupt) {
        // Jucătorul a fost eliminat: următorul jucător e deja la index.
        const size = this.game.players.length;
        if (size > 0) this.currentIndex %= size;
        this.turnAwaitingEnd = false;
        this.skipBanner = null;
        this.prepareHandover();
        this.beginTurnIfSkipping();
      } else {
        this.turnAwaitingEnd = true;
      }
    } else {
      this.debt = res.pending;
    }
    this.checkWinner();
    this.emit();
  }

  /** Încheie tura și trece la jucătorul următor. */
  endTurn() {
    if (this.guestSend((id) => ({ t: EV.END, playerId: id }))) return;
    if (this.activeDialog || !this.turnAwaitingEnd) return;
    const size = this.game?.players.length ?? 0;
    if (size === 0) return;
    this.currentIndex = (this.currentIndex + 1) % size;
    // un rând complet (s-a întors la primul jucător) = o tură pentru statistici și pentru robot
    if (this.currentIndex === 0) this.game = { ...this.game, turn: this.game.turn + 1 };
    this.turnAwaitingEnd = false;
    this.skipBanner = null;
    this.prepareHandover();
    this.beginTurnIfSkipping();
    this.emit();
  }

  /**
   * Un pas al robotului la rând: confirmă predarea, decide dialogurile și aruncă zarul sau
   * încheie tura. Returnează false dacă nu e rândul unui robot. Apelantul așteaptă între pași.
   * Excepție față de Android: dacă datornicul e robot (nu jucătorul la rând), el vinde pentru datorie.
   */
  botNextAction() {
    if (this.isGuest || this.winnerName !== null) return false;
    const d = this.activeDialog;
    const debtor = this.debtorBot(d);
    if (debtor) {
      if (d.type === 'LIQUIDATION_HANDOVER') this.acknowledgeLiquidation();
      else this.settleDebt(botLiquidationSales(debtor, d.debt.amount, this.catalog.actions, this.game, this.catalog.board, this.diceCount));
      return true;
    }
    const p = this.currentPlayer;
    if (!p || !p.isBot) return false;
    switch (d?.type) {
      case 'HANDOVER': this.acknowledgeHandover(); break;
      case 'LIQUIDATION_HANDOVER': this.acknowledgeLiquidation(); break;
      case 'DEBT': return false;   // datornicul e om: lichidează el
      case 'BUY': {
        const opt = d.offer.option;
        const decide = p.catalin ? catalinDecideBuy : botDecideBuy;
        const accept = decide({
          state: this.game, botIdx: this.game.players.indexOf(p), board: this.catalog.board,
          actions: this.catalog.actions, propertyId: d.offer.propertyId, option: opt, diceCount: this.diceCount,
        });
        if (accept) this.acceptOffer();
        else this.refuseOffer();
        break;
      }
      case 'BANK_FEE': this.acknowledgeBankFee(); break;
      case 'RENT': this.payRent(); break;
      case 'CARD': this.dismissCard(); break;
      case 'DRAW': this.drawCard(); break;
      default:
        if (this.turnAwaitingEnd) this.endTurn();
        else this.roll(Array.from({ length: this.diceCount }, () => this.botDie(p.catalin ? CATALIN_LUCK : 0)));
    }
    return true;
  }

  /** Datornicul dialogului, dacă e robot (lichidează el, nu jucătorul la rând). */
  debtorBot(d) {
    if (!d || (d.type !== 'DEBT' && d.type !== 'LIQUIDATION_HANDOVER')) return null;
    const id = d.type === 'DEBT' ? d.debt.debtorId : this.debt?.debtorId;
    const debtor = this.game?.players.find((x) => x.id === id);
    return debtor?.isBot ? debtor : null;
  }

  /** True dacă un robot trebuie să acționeze acum (pentru orchestrator). */
  get hasBotAction() {
    if (this.isGuest || this.winnerName !== null) return false;
    if (this.debtorBot(this.activeDialog)) return true;
    return !!this.currentPlayer?.isBot;
  }

  checkWinner() {
    const players = this.game?.players;
    if (players && players.length === 1) this.winnerName = players[0].name;
  }
}

/** Zarul din starea oficială, comparat după valoare (pentru a ști dacă s-a aruncat din nou). */
function sameDice(a, b) {
  return a.length === b.length && a.every((v, i) => v === b[i]);
}

