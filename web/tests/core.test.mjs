// Teste pentru regulile, motorul și protocolul — pe datele reale din data/.
// Rulare: node --test web/tests/

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { distributeStartingMoney, maxCustomStartingMoney, billCounts, GameRules, BILL_SUPPLY, TOTAL_SUPPLY } from '../js/core/rules.js';
import { TurnEngine } from '../js/core/turnEngine.js';
import { CardDeck, lotoResult, telexResult, CardOutcome } from '../js/core/cards.js';
import { calculateRent, sellBackValue, wholeSaleValue, rankPlayers, netWorth } from '../js/core/actions.js';
import { botLiquidationSales } from '../js/core/botAI.js';
import { TILE, PAWN_COLORS } from '../js/core/model.js';
import { SnapshotCodec, IntentCodec, RoomCode, RoomRules, RoomStage, RoomRole, EV } from '../js/online/protocol.js';
import { loadRealCatalog, seededRng } from './helpers.mjs';

const cat = loadRealCatalog();
const tileIndex = (pred) => cat.board.findIndex(pred);
const propTile = (id) => tileIndex((t) => t.type === TILE.PROPERTY && t.propertyId === id);
const CASA = tileIndex((t) => t.type === TILE.CASA);
const LOTO = tileIndex((t) => t.type === TILE.LOTO);

function player(i, extra = {}) {
  return {
    id: `p${i}`, name: `J${i}`, color: PAWN_COLORS[i].key, money: 50000, position: GameRules.START_POSITION,
    ownedProperties: {}, skipTurns: 0, houseSince: 0, isBot: false, ...extra,
  };
}

function state(players, bankMoney = 100000) {
  return { turn: 1, players, bankMoney, history: [] };
}

/** Motor cu catalog modificat (pachete proprii) pentru cazuri deterministe. */
function engineWith(patch = {}) {
  return new TurnEngine({ ...cat, ...patch }, seededRng(7));
}

/* ---------- Reguli și distribuție ---------- */

test('tirajul are 500 000 ECU și bancnotele sunt corecte', () => {
  const sum = Object.entries(BILL_SUPPLY).reduce((s, [d, n]) => s + Number(d) * n, 0);
  assert.equal(sum, TOTAL_SUPPLY);
});

test('împărțirea egală: jucători * sold + casierie = tiraj', () => {
  for (const n of [2, 3, 4]) {
    const d = distributeStartingMoney(n);
    assert.equal(d.playerMoney * n + d.bankMoney, TOTAL_SUPPLY, `n=${n}`);
  }
});

test('suma aleasă respectă tirajul', () => {
  assert.equal(maxCustomStartingMoney(2), 250_000);
  assert.equal(maxCustomStartingMoney(3), 150_000);
  assert.equal(maxCustomStartingMoney(4), 100_000);
  const d = distributeStartingMoney(3, 150_000);
  assert.equal(d.playerMoney, 150_000);
  assert.equal(d.playerMoney * 3 + d.bankMoney, TOTAL_SUPPLY);
  assert.throws(() => distributeStartingMoney(2, 300_000));
  assert.throws(() => distributeStartingMoney(2, 12_345));
});

test('billCounts acoperă exact suma', () => {
  for (const v of [0, 50, 1350, 9999, 123450]) {
    const c = billCounts(v);
    const total = Object.entries(c).reduce((s, [d, n]) => s + Number(d) * n, 0);
    assert.ok(total <= v && v - total < 50, `v=${v} total=${total}`);
  }
});

test('valoarea de răscumpărare e 80%', () => {
  assert.equal(sellBackValue(5000), 4000);
  const prop = cat.actions.continental;
  assert.equal(wholeSaleValue(prop, 0), sellBackValue(prop.titlePrice));
});

test('chiria crește cu anexele deținute', () => {
  const prop = cat.actions.continental;
  assert.equal(calculateRent(prop, 0), prop.titleTax);
  assert.equal(calculateRent(prop, 1), prop.titleTax + prop.annexes[0].profit);
});

/* ---------- Pachete ---------- */

test('CardDeck scoate fiecare carte o dată, apoi reamestecă descărcarea', () => {
  const deck = new CardDeck([1, 2, 3, 4], seededRng(3));
  const first = [deck.draw(), deck.draw(), deck.draw(), deck.draw()].sort();
  assert.deepEqual(first, [1, 2, 3, 4]);
  const second = [deck.draw(), deck.draw(), deck.draw(), deck.draw()].sort();
  assert.deepEqual(second, [1, 2, 3, 4]);
});

test('rezultatul LOTO și TELEX', () => {
  assert.deepEqual(lotoResult({ kind: 'W', amount: 2000 }), { outcome: CardOutcome.GAIN, amount: 2000 });
  assert.deepEqual(lotoResult({ kind: 'N', amount: 0 }), { outcome: CardOutcome.NEUTRAL, amount: null });
  assert.equal(telexResult([{ type: 'move', steps: 3 }]).outcome, CardOutcome.GAIN);
  assert.equal(telexResult([{ type: 'skip_turns', turns: 2 }, { type: 'pay_bank', amount: 3000 }]).amount, 3000);
});

/* ---------- Motorul turei ---------- */

test('de la START, un pas duce pe prima căsuță, fără bonus', () => {
  const eng = engineWith();
  const s = state([player(0), player(1)]);
  const r = eng.playTurn(s, 0, [1]);
  assert.equal(r.state.players[0].position, 0);
  assert.equal(r.state.players[0].money, 50000);
  assert.equal(r.offer.propertyId, 'continental');
  assert.equal(r.offer.option.type, 'TITLE');
  assert.equal(r.offer.option.price, cat.actions.continental.titlePrice);
});

test('trecerea peste START aduce bonusul', () => {
  const eng = engineWith();
  const s = state([player(0, { position: 36 }), player(1)]);
  const r = eng.playTurn(s, 0, [3]);
  assert.equal(r.state.players[0].position, 2);
  assert.equal(r.state.players[0].money, 50000 + GameRules.START_BONUS);
});

test('LOTO cere tragere manuală', () => {
  const eng = engineWith();
  const r = eng.playTurn(state([player(0, { position: 0 }), player(1)]), 0, [1]);
  assert.equal(r.state.players[0].position, LOTO);
  assert.equal(r.pendingDraw, 'LOTO');
});

test('chirie: proprietarul încasează, plătitorul plătește', () => {
  const eng = engineWith();
  const s = state([player(0), player(1, { ownedProperties: { continental: 0 } })]);
  const r = eng.playTurn(s, 0, [1]);
  assert.equal(r.rentDue.amount, cat.actions.continental.titleTax);
  const paid = eng.payRent(r.state, r.rentDue);
  assert.equal(paid.state.players[0].money, 50000 - cat.actions.continental.titleTax);
  assert.equal(paid.state.players[1].money, 50000 + cat.actions.continental.titleTax);
  assert.equal(paid.debt, null);
});

test('chirie fără fonduri: datorie către proprietar', () => {
  const eng = engineWith();
  const s = state([player(0, { money: 10 }), player(1, { ownedProperties: { continental: 0 } })]);
  const r = eng.playTurn(s, 0, [1]);
  const paid = eng.payRent(r.state, r.rentDue);
  assert.equal(paid.debt.debtorId, 'p0');
  assert.equal(paid.debt.creditorId, 'p1');
});

test('CASA DE ODIHNĂ: două ture pierdute și ordinea de ieșire', () => {
  const eng = engineWith();
  const r = eng.playTurn(state([player(0, { position: CASA - 1 }), player(1)]), 0, [1]);
  assert.equal(r.state.players[0].skipTurns, GameRules.CASA_ODIHNA_SKIP_TURNS);
  assert.equal(r.state.players[0].houseSince, 1);
  const again = eng.playTurn(r.state, 0, [5]);
  assert.equal(again.state.players[0].position, CASA, 'stă pe loc cât are ture de pierdut');
  assert.equal(again.state.players[0].skipTurns, 1);
});

test('datorie fără active: faliment, jucătorul iese din joc', () => {
  const eng = engineWith();
  const debt = { debtorId: 'p0', amount: 5000, creditorId: 'p1', reason: 'test' };
  const s = state([player(0, { money: 100 }), player(1)]);
  const res = eng.settleDebt(s, debt, []);
  assert.equal(res.bankrupt, true);
  assert.equal(res.state.players.length, 1);
  assert.equal(res.state.players[0].money, 50000 + 100);
});

test('lichidare: vânzarea titlului acoperă datoria', () => {
  const eng = engineWith();
  const prop = cat.actions.continental;
  const debt = { debtorId: 'p0', amount: 3000, creditorId: null, reason: 'test' };
  const s = state([player(0, { money: 0, ownedProperties: { continental: 0 } }), player(1)]);
  const res = eng.settleDebt(s, debt, [{ propertyId: 'continental', annexIndex: null }]);
  assert.equal(res.pending, null);
  assert.equal(res.bankrupt, false);
  assert.equal(res.state.players[0].money, sellBackValue(prop.titlePrice) - 3000);
  assert.deepEqual(res.state.players[0].ownedProperties, {});
});

test('cumpărare titlu și refuz cu taxă', () => {
  const eng = engineWith();
  const s = state([player(0), player(1)]);
  const r = eng.playTurn(s, 0, [1]);
  const bought = eng.applyBuy(r.state, r.offer);
  assert.equal(bought.players[0].ownedProperties.continental, 0);
  assert.equal(bought.players[0].money, 50000 - cat.actions.continental.titlePrice);
  const declined = eng.declineOffer(r.state, r.offer);
  assert.equal(declined.state.players[0].money, 50000 - cat.actions.continental.titleTax);
});

test('LOTO câștig: banca plătește', () => {
  const loto = [{ id: 'x', kind: 'W', amount: 2000, text: 'test' }];
  const eng = engineWith({ loto });
  const r = eng.drawCard(state([player(0), player(1)]), 0, 'LOTO');
  assert.equal(r.state.players[0].money, 52000);
  assert.equal(r.card.outcome, CardOutcome.GAIN);
});

test('TELEX scris cu pixul: nu plătește dacă deține compania', () => {
  const card = {
    id: 'h', text: 'test', handwritten: true,
    effects: [{ type: 'pay_owner_or_bank', amount: 2000, propertyId: 'telefoane' }],
  };
  const eng = engineWith({ telex: [card] });
  const s = state([player(0, { ownedProperties: { telefoane: 0 } }), player(1)]);
  const r = eng.drawCard(s, 0, 'TELEX');
  assert.equal(r.state.players[0].money, 50000);
  assert.match(r.card.destination, /Nu plătești nimic/);
});

test('TELEX cu mutare: pendingMove, apoi moveBy', () => {
  const card = { id: 'm', text: 'înapoi', handwritten: false, effects: [{ type: 'move', steps: -2 }] };
  const eng = engineWith({ telex: [card] });
  const s = state([player(0, { position: 5 }), player(1)]);
  const r = eng.drawCard(s, 0, 'TELEX');
  assert.equal(r.pendingMove, -2);
  const moved = eng.moveBy(r.state, 0, -2);
  assert.equal(moved.state.players[0].position, 3);
});

test('robotul vinde o acțiune întreagă (titlu + anexe) odată, doar cât trebuie', () => {
  const debtor = player(0, { money: 0, ownedProperties: { poli: 2, unirii: 0 } });
  const state = { turn: 1, players: [debtor, player(1)], bankMoney: 100000, history: [] };
  const sales = botLiquidationSales(debtor, 2000, cat.actions, state, cat.board, 2);
  assert.ok(sales.length >= 1);
  assert.equal(sales.every((x) => x.annexIndex === null), true);
});

test('clasamentul la final: bani plus acțiuni', () => {
  const a = player(0, { money: 10000 });
  const b = player(1, { money: 1000, ownedProperties: { continental: 0 } });
  const ranked = rankPlayers([a, b], cat.actions);
  assert.equal(ranked[0].id, netWorth(b, cat.actions) > netWorth(a, cat.actions) ? 'p1' : 'p0');
});

/* ---------- Protocol ---------- */

test('snapshot: encode -> decode -> encode este stabil', () => {
  const eng = engineWith();
  const r = eng.playTurn(state([player(0), player(1, { isBot: true })]), 0, [1]);
  const snap = {
    state: r.state, currentIndex: 0, lastDice: [1], winnerName: null, turnAwaitingEnd: true, skipBanner: null,
    offer: { playerId: r.offer.playerId, propertyId: r.offer.propertyId, annexIndex: null },
    feeOffer: null, rentDue: null, debt: null, card: null, pendingDraw: null,
  };
  const json = SnapshotCodec.encode(snap);
  const back = SnapshotCodec.decode(json);
  assert.equal(SnapshotCodec.encode(back), json);
  assert.equal(back.state.players[1].isBot, true);
  const keys = Object.keys(JSON.parse(json));
  for (const k of ['turn', 'bank', 'history', 'players', 'cur', 'dice', 'winner', 'await', 'skip', 'offer', 'fee', 'rent', 'debt', 'draw', 'card']) {
    assert.ok(keys.includes(k), `cheie lipsă: ${k}`);
  }
});

test('intenții: encode -> decode pentru fiecare tip', () => {
  const events = [
    { t: EV.ROLL, playerId: 'p1', value: 4 },
    { t: EV.BUY, playerId: 'p1', tile: 0 },
    { t: EV.DECLINE, playerId: 'p1', tile: 0 },
    { t: EV.ANNEX, playerId: 'p1', tile: 5 },
    { t: EV.END, playerId: 'p1' },
    { t: EV.RENT, playerId: 'p1' },
    { t: EV.ACK, playerId: 'p1' },
    { t: EV.SELL, playerId: 'p1', propertyId: 'poli', annexIndex: null },
    { t: EV.BANKRUPT, playerId: 'p1' },
    { t: EV.DRAW, playerId: 'p1' },
  ];
  for (const e of events) {
    const back = IntentCodec.decode(IntentCodec.encode(e));
    assert.equal(back.t, e.t);
    assert.equal(back.playerId, e.playerId);
  }
  assert.equal(IntentCodec.decode('nu-e-json'), null);
  assert.equal(IntentCodec.decode('{"p":"p0","t":"zzz"}'), null);
});

test('coduri de cameră: alfabet fără I, O, 0, 1', () => {
  for (let i = 0; i < 200; i++) {
    const c = RoomCode.generate();
    assert.ok(RoomCode.isValid(c), c);
    assert.ok(!/[IO01]/.test(c));
  }
  assert.equal(RoomCode.isValid('ABC'), false);
  assert.equal(RoomCode.isValid('AB0D'), false);
});

test('reguli de cameră: loc liber, culoare luată, cameră plină', () => {
  const info = {
    stage: RoomStage.LOBBY, hostId: 'p0',
    players: [{ id: 'p0', name: 'A', color: 'ROSU', role: RoomRole.HOST }, { id: 'p1', name: 'B', color: 'VERDE', role: RoomRole.GUEST }],
  };
  assert.equal(RoomRules.freeSeat(info.players), 'p2');
  assert.match(RoomRules.joinError(info, 'ROSU'), /deja luat/);
  assert.equal(RoomRules.joinError(info, 'MOV'), null);
  assert.equal(RoomRules.joinError(null, 'MOV'), 'Camera nu există.');
  assert.equal(RoomRules.canStart(info), true);
  const back = RoomRules.fromMap(RoomRules.toMap(info));
  assert.deepEqual(back.players.map((p) => p.id), ['p0', 'p1']);
});

test('constantele de regulă sunt cele din Kotlin', () => {
  assert.equal(GameRules.START_BONUS, 3000);
  assert.equal(GameRules.BANK_INTEREST, 5000);
  assert.equal(GameRules.DIVIDEND, 2000);
  assert.equal(GameRules.SALA_CONSFATUIRI_FEE, 1000);
  assert.equal(GameRules.RESALE_RATIO_PERCENT, 80);
});
