// Motorul turei. Nu avansează ordinea jucătorilor: apelantul o face.
// Port din TurnEngine.kt. Stările sunt tratate ca valori: fiecare pas produce un obiect nou.

import { GameRules } from './rules.js';
import { TILE } from './model.js';
import { calculateRent, nextAnnex, sellBackValue, wholeSaleValue } from './actions.js';
import { CardDeck, lotoResult, telexResult, lotoValue, telexValue } from './cards.js';
import { CATALIN_CONTROL } from './dice.js';

/** Datorie care nu poate fi plătită din bani lichizi. creditorId null = Banca. */
export function debtEquals(a, b) {
  if (a === b) return true;
  if (!a || !b) return false;
  return a.debtorId === b.debtorId && a.amount === b.amount && a.creditorId === b.creditorId && a.reason === b.reason;
}

class DebtException extends Error {
  constructor(debt) {
    super('debt');
    this.debt = debt;
  }
}

const MAX_CHAIN = 4;

export class TurnEngine {
  /**
   * @param {object} catalog  rezultatul loadCatalog()
   * @param {() => number} rng  generatorul pentru pachete (inject pentru teste)
   */
  constructor(catalog, rng = Math.random) {
    this.board = catalog.board;
    this.actions = catalog.actions;
    this.lotoDeck = new CardDeck(catalog.loto, rng);
    this.telexDeck = new CardDeck(catalog.telex, rng);
    this.rng = rng;
  }

  /** Unul sau două zaruri: pionul merge cu suma. Trecerea peste START aduce bonusul. */
  playTurn(state, playerIndex, dice) {
    if (!(dice.length >= 1 && dice.length <= 2 && dice.every((d) => d >= 1 && d <= 6))) {
      throw new Error(`Unul sau două zaruri, valori 1..6: ${dice}`);
    }
    const steps = dice.reduce((a, b) => a + b, 0);
    const ctx = new Ctx(this, state);
    const player = ctx.players[playerIndex];

    if (player.skipTurns > 0) {
      ctx.players[playerIndex] = { ...player, skipTurns: player.skipTurns - 1 };
      ctx.log(`${player.name} stă o tură`);
      return ctx.result(state);
    }
    if (this.isWaitingAtHouse(state, playerIndex)) {
      ctx.log(`${player.name} așteaptă la Casa de Odihnă: iese primul cel care a ajuns înaintea lui`);
      return ctx.result(state);
    }

    const raw = player.position + steps;
    if (raw >= this.board.length) ctx.bankPays(playerIndex, GameRules.START_BONUS, 'trecere prin START');
    ctx.moveTo(playerIndex, raw % this.board.length);
    ctx.log(`${player.name} a dat ${dice.join('+')}`);
    try {
      this.resolveLanding(ctx, playerIndex, 0);
    } catch (e) {
      if (!(e instanceof DebtException)) throw e;
      ctx.pending = e.debt;
      ctx.log(`Fonduri insuficiente: ${e.debt.debtorId} datorează ${e.debt.amount} (${e.debt.reason})`);
    }
    return ctx.result(state);
  }

  /** Casa de Odihnă: cine a ajuns primul iese primul. */
  isWaitingAtHouse(state, playerIndex) {
    const p = state.players[playerIndex];
    if (p.skipTurns > 0) return false;
    if (!(p.position >= 0 && p.position < this.board.length) || this.board[p.position].type !== TILE.CASA) return false;
    return state.players.some((q) => q.id !== p.id && q.position === p.position && q.houseSince < p.houseSince);
  }

  /** Tura unui jucător cu ture de pierdut: scade una, fără zar și fără plăți. */
  skipTurn(state, playerIndex) {
    const ctx = new Ctx(this, state);
    const p = ctx.players[playerIndex];
    if (!(p.skipTurns > 0)) throw new Error(`${p.name} nu are ture de sărit`);
    ctx.players[playerIndex] = { ...p, skipTurns: p.skipTurns - 1 };
    ctx.log(`${p.name} staționează la Casa de Odihnă; ture rămase de sărit: ${p.skipTurns - 1}`);
    return ctx.result(state);
  }

  /** Mutarea cerută de un cartonaș TELEX: steps > 0 înainte, < 0 înapoi. */
  moveBy(state, playerIndex, steps) {
    const ctx = new Ctx(this, state);
    const player = ctx.players[playerIndex];
    const raw = player.position + steps;
    const n = this.board.length;
    if (steps > 0 && raw >= n) ctx.bankPays(playerIndex, GameRules.START_BONUS, 'trecere prin START');
    ctx.moveTo(playerIndex, ((raw % n) + n) % n);
    try {
      this.resolveLanding(ctx, playerIndex, 1);
    } catch (e) {
      if (!(e instanceof DebtException)) throw e;
      ctx.pending = e.debt;
      ctx.log(`Fonduri insuficiente: ${e.debt.debtorId} datorează ${e.debt.amount} (${e.debt.reason})`);
    }
    return ctx.result(state);
  }

  /** Trage manual din pachetul 'LOTO' sau 'TELEX' și aplică efectele. */
  drawCard(state, playerIndex, kind) {
    const ctx = new Ctx(this, state);
    const player = ctx.players[playerIndex];
    try {
      // CatalinRobot: în majoritatea cazurilor scoate cartonașul cel mai bun pentru el din teanc
      const chooses = player.catalin && this.rng() < CATALIN_CONTROL;
      if (kind === 'LOTO') {
        const card = chooses ? this.lotoDeck.drawBest(lotoValue) : this.lotoDeck.draw();
        ctx.log(`${player.name} a tras din pachetul LOTO: ${card.text}`);
        const r = lotoResult(card);
        ctx.card = { kind: 'LOTO', text: card.text, outcome: r.outcome, amount: r.amount, destination: null, cardId: card.id };
        if (card.kind === 'W') ctx.bankPays(playerIndex, card.amount, 'LOTO');
        else if (card.kind === 'L') ctx.playerPaysBank(playerIndex, card.amount, 'LOTO');
      } else if (kind === 'TELEX') {
        const card = chooses ? this.telexDeck.drawBest(telexValue) : this.telexDeck.draw();
        ctx.log(`${player.name} a tras din pachetul TELEX: ${card.text}`);
        const effects = this.effectiveEffects(ctx, playerIndex, card);
        const r = telexResult(effects);
        ctx.card = {
          kind: 'TELEX',
          text: card.text,
          outcome: r.outcome,
          amount: r.amount,
          destination: this.destinationLine(ctx, playerIndex, card),
          cardId: card.id,
        };
        for (const e of effects) this.applyEffect(ctx, playerIndex, e);
      } else {
        throw new Error(`Pachet necunoscut: ${kind}`);
      }
    } catch (e) {
      if (!(e instanceof DebtException)) throw e;
      ctx.pending = e.debt;
      ctx.log(`Fonduri insuficiente: ${e.debt.debtorId} datorează ${e.debt.amount} (${e.debt.reason})`);
    }
    return ctx.result(state);
  }

  /** Plata explicită a chiriei. */
  payRent(state, due) {
    const ctx = new Ctx(this, state);
    const payer = ctx.indexOf(due.payerId);
    const owner = ctx.indexOf(due.ownerId);
    if (!(payer >= 0 && owner >= 0)) throw new Error('Jucător necunoscut în chirie');
    const prop = this.actions[due.propertyId];
    try {
      ctx.transfer(payer, owner, due.amount, `chirie ${prop.name}`);
      ctx.recordCollected(due.propertyId, due.amount);
    } catch (e) {
      if (!(e instanceof DebtException)) throw e;
      ctx.pending = e.debt;
      ctx.log(`Fonduri insuficiente pentru chirie: datorie de ${e.debt.amount}`);
    }
    return ctx.result(state);
  }

  /** Refuzul unui titlu liber: taxa titlului se plătește Băncii. */
  declineOffer(state, offer) {
    if (offer.option.type !== 'TITLE') throw new Error('Doar titlurile libere pot fi refuzate cu taxă');
    const prop = this.actions[offer.propertyId];
    const ctx = new Ctx(this, state);
    const idx = ctx.indexOf(offer.playerId);
    if (idx < 0) throw new Error(`Jucător necunoscut: ${offer.playerId}`);
    ctx.log(`${ctx.players[idx].name} a refuzat cumpărarea ${prop.name}`);
    try {
      ctx.playerPaysBank(idx, prop.titleTax, `taxa titlului ${prop.name}`);
    } catch (e) {
      if (!(e instanceof DebtException)) throw e;
      ctx.pending = e.debt;
      ctx.log(`Fonduri insuficiente pentru taxa de refuz: datorie de ${e.debt.amount} către Bancă`);
    }
    return ctx.result(state);
  }

  /**
   * Stinge o datorie prin vânzări la Bancă (80%). Dacă nu mai există nimic de vândut și soldul
   * nu acoperă datoria, jucătorul falimentează și iese din joc.
   * @returns {{state, pending, bankrupt}}
   */
  settleDebt(state, debt, sales) {
    const events = [];
    const players = state.players.map((p) => ({ ...p, ownedProperties: { ...p.ownedProperties } }));
    let bank = state.bankMoney;

    for (const sale of sales) {
      const idx = players.findIndex((p) => p.id === debt.debtorId);
      if (idx < 0) throw new Error(`Jucătorul ${debt.debtorId} nu mai este în joc`);
      const player = players[idx];
      const prop = this.actions[sale.propertyId];
      const level = player.ownedProperties[prop.id];
      if (level === undefined) throw new Error(`${player.name} nu deține ${prop.name}`);
      let value;
      // a vândut din propriile anexe: nu mai cumpără anexe până își reface rezerva (vezi endTurn)
      if (level > 0) player.annexRecovery = true;
      if (sale.annexIndex !== null && sale.annexIndex !== undefined) {
        if (sale.annexIndex !== level) throw new Error(`Anexele se vând în ordine inversă (nivel ${level})`);
        value = sellBackValue(prop.annexes.find((a) => a.index === level).price);
        player.ownedProperties[prop.id] = level - 1;
        events.push(`Jucătorul ${player.name} a lichidat o anexă la ${prop.name}.`);
      } else {
        value = wholeSaleValue(prop, level);
        delete player.ownedProperties[prop.id];
        const what = level === 0 ? 'titlul' : `întreaga acțiune (titlu + ${level} anexe)`;
        events.push(`${player.name} a vândut ${what} ${prop.name} la Bancă (+${value})`);
      }
      player.money += value;
      bank -= value;
    }

    const idx = players.findIndex((p) => p.id === debt.debtorId);
    const debtor = players[idx];
    if (debtor.money >= debt.amount) {
      const creditorIdx = debt.creditorId ? players.findIndex((p) => p.id === debt.creditorId) : -1;
      debtor.money -= debt.amount;
      if (creditorIdx >= 0) players[creditorIdx].money += debt.amount;
      else bank += debt.amount;
      events.push(`${debtor.name} a achitat ${debt.amount} (${debt.reason})`);
      return {
        state: { ...state, players, bankMoney: bank, history: [...state.history, ...events] },
        pending: null,
        bankrupt: false,
      };
    }

    if (Object.keys(debtor.ownedProperties).length > 0) {
      return {
        state: { ...state, players, bankMoney: bank, history: [...state.history, ...events] },
        pending: debt,
        bankrupt: false,
      };
    }

    // Faliment: nimic de vândut, soldul merge la creditor, jucătorul iese din joc.
    const remaining = debtor.money;
    const creditorIdx = debt.creditorId ? players.findIndex((p) => p.id === debt.creditorId) : -1;
    if (creditorIdx >= 0) players[creditorIdx].money += remaining;
    else bank += remaining;
    players.splice(idx, 1);
    events.push(`FALIMENT: ${debtor.name} a fost eliminat; restul de ${remaining} merge la ${debt.creditorId ?? 'Bancă'}`);
    return {
      state: { ...state, players, bankMoney: bank, history: [...state.history, ...events] },
      pending: null,
      bankrupt: true,
    };
  }

  /** Aplică oferta acceptată (cumpărare titlu sau anexă). Aruncă eroare dacă nu e validă. */
  applyBuy(state, offer) {
    const idx = state.players.findIndex((p) => p.id === offer.playerId);
    if (idx < 0) throw new Error(`Jucător necunoscut: ${offer.playerId}`);
    const prop = this.actions[offer.propertyId];
    const buyer = state.players[idx];
    const players = state.players.map((p) => ({ ...p, ownedProperties: { ...p.ownedProperties } }));
    let bank = state.bankMoney;
    let event;
    const opt = offer.option;

    if (opt.type === 'TITLE') {
      if (this.ownerOf(state, prop.id) !== null) throw new Error(`${prop.name} are deja proprietar`);
      if (buyer.money < opt.price) throw new Error('Fonduri insuficiente pentru titlu');
      players[idx].money = buyer.money - opt.price;
      players[idx].ownedProperties[prop.id] = 0;
      bank += opt.price;
      event = `${buyer.name} a cumpărat titlul ${prop.name} (${opt.price})`;
    } else {
      const owner = this.ownerOf(state, prop.id);
      if (!owner || owner.id !== buyer.id) throw new Error('Doar proprietarul titlului poate cumpăra anexe');
      const level = buyer.ownedProperties[prop.id];
      if (opt.annex.index !== level + 1) throw new Error('Anexa trebuie cumpărată în ordine');
      if (buyer.money < opt.annex.price) throw new Error('Fonduri insuficiente pentru anexă');
      players[idx].money = buyer.money - opt.annex.price;
      players[idx].ownedProperties[prop.id] = level + 1;
      bank += opt.annex.price;
      event = `Jucătorul ${buyer.name} a investit într-o anexă la ${prop.name}.`;
    }
    return { ...state, players, bankMoney: bank, history: [...state.history, event] };
  }

  ownerOf(state, propertyId) {
    return state.players.find((p) => Object.prototype.hasOwnProperty.call(p.ownedProperties, propertyId)) ?? null;
  }

  resolveLanding(ctx, idx, depth) {
    if (depth > MAX_CHAIN) return;
    const player = ctx.players[idx];
    const tile = this.board[player.position];
    switch (tile.type) {
      case TILE.START:
        break;
      case TILE.PROPERTY:
        this.landOnProperty(ctx, idx, tile.propertyId);
        break;
      case TILE.LOTO:
        ctx.pendingDraw = 'LOTO';
        break;
      case TILE.TELEX:
        ctx.pendingDraw = 'TELEX';
        break;
      case TILE.BANK:
        ctx.bankPays(idx, GameRules.BANK_INTEREST, 'dobândă de la BANCA');
        break;
      case TILE.BANK_DIVIDEND:
        ctx.bankPays(idx, GameRules.DIVIDEND, 'dividende de la BANCA');
        break;
      case TILE.SALA:
        ctx.playerPaysBank(idx, GameRules.SALA_CONSFATUIRI_FEE, 'SALA CONSFĂTUIRI');
        break;
      case TILE.CASA:
        ctx.setSkip(idx, GameRules.CASA_ODIHNA_SKIP_TURNS, 'CASA DE ODIHNĂ');
        ctx.markHouseArrival(idx);
        break;
      case TILE.RECHEMAT:
        ctx.log(`${player.name} este rechemat urgent la START (fără bonus)`);
        ctx.moveTo(idx, GameRules.START_POSITION);
        break;
      default:
        break;
    }
  }

  landOnProperty(ctx, idx, propertyId) {
    const prop = this.actions[propertyId];
    if (!prop) return;
    const buyer = ctx.players[idx];
    const owner = ctx.ownerOf(propertyId);
    if (owner === null) {
      ctx.offer = { playerId: buyer.id, propertyId, option: { type: 'TITLE', price: prop.titlePrice } };
    } else if (owner.id === buyer.id) {
      const annex = nextAnnex(prop, owner.ownedProperties[propertyId]);
      if (annex) ctx.offer = { playerId: buyer.id, propertyId, option: { type: 'ANNEX', annex } };
    } else {
      const level = owner.ownedProperties[propertyId];
      ctx.recordVisit(propertyId, buyer.id);
      ctx.rentDue = {
        payerId: buyer.id,
        ownerId: owner.id,
        propertyId,
        amount: calculateRent(prop, level),
      };
    }
  }

  /**
   * Scutirea casei: dacă numele companiei de pe cartonaș e scris cu pixul (handwritten) și
   * jucătorul care trage deține chiar compania, nu plătește nimic.
   */
  effectiveEffects(ctx, idx, card) {
    if (!card.handwritten) return card.effects;
    const pay = card.effects.find((e) => e.type === 'pay_owner_or_bank');
    if (!pay) return card.effects;
    const owner = ctx.ownerOf(pay.propertyId);
    if (!owner || owner.id !== ctx.players[idx].id) return card.effects;
    const company = this.actions[pay.propertyId]?.name ?? pay.propertyId;
    ctx.log(`${ctx.players[idx].name} nu plătește nimic: ${company} îi aparține (scris cu pixul pe cartonaș)`);
    return card.effects.filter((e) => e.type !== 'pay_owner_or_bank');
  }

  destinationLine(ctx, idx, card) {
    const pay = card.effects.find((e) => e.type === 'pay_owner_or_bank' || e.type === 'pay_bank');
    if (!pay) return null;
    if (pay.type === 'pay_bank') return 'Banii se achită către: Banca';
    const company = this.actions[pay.propertyId]?.name ?? pay.propertyId;
    const owner = ctx.ownerOf(pay.propertyId);
    const me = ctx.players[idx];
    if (!owner) return `Banii se achită către: Banca (${company} nu e privatizată)`;
    if (owner.id === me.id && card.handwritten) return `Nu plătești nimic: ${company} îți aparține`;
    if (owner.id === me.id) return `Banii se achită către: Banca (${company} e a ta)`;
    return `Banii se achită către: ${owner.name} (proprietar ${company})`;
  }

  applyEffect(ctx, idx, effect) {
    switch (effect.type) {
      case 'pay_bank':
        ctx.playerPaysBank(idx, effect.amount, 'TELEX');
        break;
      case 'pay_owner_or_bank': {
        const owner = ctx.ownerOf(effect.propertyId);
        if (owner && owner.id !== ctx.players[idx].id) {
          ctx.transfer(idx, ctx.indexOf(owner.id), effect.amount, 'TELEX (proprietar)');
        } else {
          ctx.playerPaysBank(idx, effect.amount, 'TELEX (bancă)');
        }
        break;
      }
      case 'receive_bank':
        ctx.bankPays(idx, effect.amount, 'TELEX');
        break;
      case 'receive_from_each_player':
        ctx.players.forEach((_, i) => {
          if (i !== idx) ctx.transfer(i, idx, effect.amount, 'TELEX (contribuție)');
        });
        break;
      case 'move':
        // Pionul rămâne pe TELEX cât se vede cartonașul; mutarea se face după confirmare (moveBy).
        ctx.pendingMove = effect.steps;
        ctx.log(`${ctx.players[idx].name} va muta ${effect.steps > 0 ? 'înainte' : 'înapoi'} ${Math.abs(effect.steps)} căsuțe`);
        break;
      case 'skip_turns':
        ctx.setSkip(idx, effect.turns, 'TELEX');
        break;
      default:
        break;
    }
  }
}

/** Statistica unei proprietăți: de câte ori a aterizat fiecare jucător și cât s-a încasat. */
export function statsFor(stats, propertyId) {
  if (!stats[propertyId]) stats[propertyId] = { visits: {}, collected: 0 };
  return stats[propertyId];
}

function copyStats(stats) {
  const out = {};
  for (const [pid, s] of Object.entries(stats ?? {})) out[pid] = { visits: { ...s.visits }, collected: s.collected };
  return out;
}

/** Stare de lucru pentru o tură: modificările se acumulează aici, apoi se produce un state nou. */
class Ctx {
  constructor(engine, state) {
    this.engine = engine;
    this.players = state.players.map((p) => ({ ...p, ownedProperties: { ...p.ownedProperties } }));
    this.bank = state.bankMoney;
    this.stats = copyStats(state.stats);
    this.offer = null;
    this.events = [];
    this.pending = null;
    this.rentDue = null;
    this.card = null;
    this.pendingMove = null;
    this.pendingDraw = null;
    this.original = state;
  }

  log(text) {
    this.events.push(text);
  }

  indexOf(playerId) {
    return this.players.findIndex((p) => p.id === playerId);
  }

  ownerOf(propertyId) {
    return this.players.find((p) => Object.prototype.hasOwnProperty.call(p.ownedProperties, propertyId)) ?? null;
  }

  moveTo(idx, position) {
    this.players[idx] = { ...this.players[idx], position };
  }

  /** Un adversar a aterizat pe proprietatea [propertyId] (înainte de plata chiriei). */
  recordVisit(propertyId, playerId) {
    const s = statsFor(this.stats, propertyId);
    s.visits[playerId] = (s.visits[playerId] ?? 0) + 1;
  }

  /** Chirie efectiv încasată de proprietar pe [propertyId]. */
  recordCollected(propertyId, amount) {
    statsFor(this.stats, propertyId).collected += amount;
  }

  setSkip(idx, turns, reason) {
    this.players[idx] = { ...this.players[idx], skipTurns: turns };
    this.log(`${this.players[idx].name} pierde ${turns} ture (${reason})`);
  }

  /** Ștampilează sosirea la Casa de Odihnă: ordinea de ieșire e cea a sosirii. */
  markHouseArrival(idx) {
    const next = Math.max(0, ...this.players.map((p) => p.houseSince)) + 1;
    this.players[idx] = { ...this.players[idx], houseSince: next };
  }

  bankPays(idx, amount, reason) {
    this.bank -= amount;
    this.players[idx] = { ...this.players[idx], money: this.players[idx].money + amount };
    this.log(`${this.players[idx].name} primește ${amount} de la bancă (${reason})`);
  }

  playerPaysBank(idx, amount, reason) {
    if (amount === 0) return;
    if (this.players[idx].money < amount) {
      throw new DebtException({ debtorId: this.players[idx].id, amount, creditorId: null, reason });
    }
    this.players[idx] = { ...this.players[idx], money: this.players[idx].money - amount };
    this.bank += amount;
    this.log(`${this.players[idx].name} a achitat automat ${amount} către Bancă (${reason})`);
  }

  transfer(from, to, amount, reason) {
    if (from === to || amount === 0) return;
    if (this.players[from].money < amount) {
      throw new DebtException({ debtorId: this.players[from].id, amount, creditorId: this.players[to].id, reason });
    }
    this.players[from] = { ...this.players[from], money: this.players[from].money - amount };
    this.players[to] = { ...this.players[to], money: this.players[to].money + amount };
    this.log(`${this.players[from].name} a achitat automat ${amount} lui ${this.players[to].name} (${reason})`);
  }

  result(original) {
    const state = {
      ...original,
      players: this.players.map((p) => ({ ...p })),
      bankMoney: this.bank,
      history: [...original.history, ...this.events],
      stats: this.stats,
    };
    return {
      state,
      offer: this.offer,
      events: [...this.events],
      debt: this.pending,
      rentDue: this.rentDue,
      card: this.card,
      pendingMove: this.pendingMove,
      pendingDraw: this.pendingDraw,
    };
  }
}
