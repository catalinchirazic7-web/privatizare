// Ecranul de joc: tabla, zarul, HUD-ul, sertarul cu jucătorii, cartonașul de aterizare, dialogurile
// și orchestrarea (roboții, încheierea automată a turei, salvarea). Port din GameScreen.kt.

import { BoardView } from './board.js';
import { sound } from '../audio.js';
import { storage } from '../storage.js';
import { el, clear, fmt, ecu } from '../ui/dom.js';
import { PAWN_BY_KEY, TILE } from '../core/model.js';
import { calculateRent, nextAnnex, sellBackValue, wholeSaleValue } from '../core/actions.js';
import { GameRules } from '../core/rules.js';
import { tileVisual, tileHint, propertyVisual } from './visuals.js';
import { actionArtPath, propertyIconPath } from '../core/model.js';
import { CardOutcome } from '../core/cards.js';

const DECISION_DIALOGS = ['BUY', 'BANK_FEE', 'RENT', 'DEBT', 'LIQUIDATION_HANDOVER', 'HANDOVER'];
const DIE_PIPS = {
  1: [4],
  2: [0, 8],
  3: [0, 4, 8],
  4: [0, 2, 6, 8],
  5: [0, 2, 4, 6, 8],
  6: [0, 2, 3, 5, 6, 8],
};
const DIE_POS = { fx: 0.63, fy: 0.34 };   // zarul stă în zona liberă din centrul tablei
const DIE2_DX = 0.075;                     // al doilea zar (mod cu două zaruri), lângă primul
const LANDING_MS = 3800;
const BOT_DELAY_MS = 1000;
const CARD_SHOW_MS = 3500;   // cât stă pe ecran cartonașul tras de un robot, ca să-l vezi
const SKIP_SHOW_MS = 2500;   // cât stă vizibil bannerul „staționează” al unui robot, ca să-l vezi
const END_DELAY_MS = 900;
const END_DELAY_SKIP_MS = 2200;
const ROLL_ANIM_MS = 488;   // zarul: cu 25% mai rapid (650 × 0,75)

class DieView {
  constructor(root, badge) {
    this.root = root;
    this.badge = badge;
    this.pips = [];
    for (let i = 0; i < 9; i++) {
      const pip = el('span', { class: 'pip' });
      this.pips.push(pip);
      root.querySelector('.die-face').append(pip);
    }
    this.rolling = false;
    this.iv = null;
    this.hideBadge = null;
    this.setFace(1);
  }

  get busy() {
    return this.rolling;
  }

  setFace(v) {
    const on = new Set(DIE_PIPS[v]);
    this.pips.forEach((p, i) => p.classList.toggle('on', on.has(i)));
  }

  startRolling() {
    if (this.rolling) return;
    this.rolling = true;
    this.root.classList.add('is-rolling');
    if (this.badge) this.badge.hidden = true;
    this.iv = setInterval(() => this.setFace(1 + Math.floor(Math.random() * 6)), 70);
  }

  /** @param {string|null} badgeText  ce scrie insigna; null = zarul nu are insignă (al doilea zar) */
  stopRolling(value, badgeText = String(value)) {
    clearInterval(this.iv);
    this.iv = null;
    this.rolling = false;
    this.root.classList.remove('is-rolling');
    this.setFace(value);
    this.root.classList.remove('is-pop', 'is-flash');
    void this.root.offsetWidth;   // repornește animația
    this.root.classList.add('is-pop', 'is-flash');
    setTimeout(() => this.root.classList.remove('is-flash'), 320);
    if (!this.badge || badgeText == null) return;
    this.badge.textContent = badgeText;
    this.badge.hidden = false;
    clearTimeout(this.hideBadge);
    this.hideBadge = setTimeout(() => (this.badge.hidden = true), 2000);
  }

  setReady(ready, enabled) {
    this.root.classList.toggle('is-ready', ready && enabled);
    this.root.classList.toggle('is-disabled', !enabled);
  }
}

export class GameScreen {
  /**
   * @param {object} o
   * @param {HTMLElement} o.root  secțiunea #scr-game
   * @param {object} o.catalog
   * @param {import('./game.js').Game} o.game
   * @param {import('../online/controller.js').OnlineController|null} o.online
   * @param {() => void} o.onExit
   */
  constructor(o) {
    this.root = o.root;
    this.catalog = o.catalog;
    this.game = o.game;
    this.online = o.online;
    this.onExit = o.onExit;

    this.$ = (id) => document.getElementById(id);
    this.modalRoot = this.$('modal-root');
    this.toastEl = this.$('toast');

    this.viewedId = null;
    this.zoomPreset = 1;
    this.zoomNonce = 0;
    this.stretch = true;
    this.tilt = true;
    this.pawnsBusy = false;
    this.landedTile = null;
    this.landingTimer = null;
    this.sheetProperty = null;
    this.sheetPage = 0;
    this.modalKey = null;
    this.lastShownNonce = 0;
    this.prevMoney = new Map();
    this.shownMoney = new Map();
    this.botKey = null;
    this.endKey = null;
    this.botTimer = null;
    this.endTimer = null;
    this.clockTimer = null;
    this.unsubs = [];
    this.active = false;
    this.bindDom();
  }

  /* ---------- elemente DOM ---------- */

  bindDom() {
    const $ = this.$;
    this.canvas = $('board');
    this.dieEl = $('die');
    this.dieBadge = $('die-badge');
    this.die = new DieView(this.dieEl, this.dieBadge);
    this.dieEl.addEventListener('click', () => this.onDieTap());
    this.die2El = $('die2');
    this.die2 = new DieView(this.die2El, null);
    this.die2El.addEventListener('click', () => this.onDieTap());

    this.board = new BoardView(this.canvas, {
      catalog: this.catalog,
      onTileTap: (i) => this.onTileTap(i),
      onDeckTap: () => this.game.drawCard(),
      onBusyChange: (busy) => this.setPawnsBusy(busy),
      dieBusy: () => this.die.busy,
      onFrame: (b) => this.placeDie(b),
      onArrive: () => this.render(),
    });
    this.board.onStep = () => sound.step();

    $('hud-toggle').addEventListener('click', () => this.toggleDrawer());
    $('zoom-1').addEventListener('click', () => this.setZoom(1));
    $('zoom-2').addEventListener('click', () => this.setZoom(2));
    $('toggle-stretch').addEventListener('click', () => {
      this.stretch = !this.stretch;
      this.render();
    });
    $('toggle-tilt').addEventListener('click', () => {
      this.tilt = !this.tilt;
      this.render();
    });
    $('test-sound').addEventListener('click', () => {
      sound.unlock();
      sound.coin();
      setTimeout(() => this.toast(`Sunet: ${sound.status()}`), 300);
    });
    $('toggle-sound').addEventListener('click', () => {
      sound.toggle();
      window.dispatchEvent(new Event('sound-changed'));
      this.render();
    });
    $('btn-game-exit').addEventListener('click', () => this.exitToMenu());
    $('drawer').addEventListener('pointerdown', () => this.touchDrawer());
    $('landing-card').addEventListener('click', () => this.onLandingTap());
  }

  /* ---------- ciclu de viață ---------- */

  /** @param {{online?: object|null}} o  controllerul online, dacă jocul e online */
  start({ online = null } = {}) {
    this.active = true;
    this.online = online;
    this.lastShownNonce = this.game.rollNonce;
    this.prevMoney = new Map((this.game.game?.players ?? []).map((p) => [p.id, p.money]));
    this.shownMoney = new Map(this.prevMoney);
    this.unsubs.push(this.game.onChange(() => this.render()));
    if (online) this.unsubs.push(online.onChange(() => this.render()));
    this.clockTimer = setInterval(() => this.tickClock(), 1000);
    this.render();
  }

  stop() {
    this.active = false;
    for (const u of this.unsubs) u();
    this.unsubs = [];
    clearInterval(this.clockTimer);
    clearTimeout(this.botTimer);
    clearTimeout(this.endTimer);
    clearTimeout(this.landingTimer);
    this.closeModal();
    this.sheetProperty = null;
  }

  /* ---------- utilitare ---------- */

  get myId() {
    return this.online && this.game.isOnline ? this.game.myPlayerId : null;
  }

  get myTurn() {
    if (!this.game.isOnline) return true;
    return this.game.currentPlayer?.id === this.game.myPlayerId;
  }

  toast(text) {
    this.toastEl.textContent = text;
    this.toastEl.hidden = false;
    clearTimeout(this.toastTimer);
    this.toastTimer = setTimeout(() => (this.toastEl.hidden = true), 2600);
  }

  /** Dialogul care trebuie arătat acum pe acest dispozitiv (online: doar celui vizat). */
  visibleDialog() {
    const g = this.game;
    const d = g.activeDialog;
    if (!d || this.pawnsBusy) return null;
    // o mutare care nu s-a afișat încă (pionul pornește abia acum) nu arată dialogul înainte de sosire
    const moving = g.game.players.some((p) => {
      const a = this.board.anims.get(p.id);
      return !!a && a.target !== p.position;
    });
    if (moving) return null;
    const target = {
      BUY: () => d.offer?.playerId,
      BANK_FEE: () => d.offer?.playerId,
      RENT: () => d.due?.payerId,
      DEBT: () => d.debt?.debtorId,
    }[d.type]?.() ?? g.currentPlayer?.id;
    // deciziile roboților le ia robotul singur: jucătorul nu vede și nu apasă nimic în locul lui
    const botTarget = g.game?.players.find((p) => p.id === target)?.isBot;
    // cartonașul tras de robot se vede tuturor, doar ca informație (nu are buton)
    if (botTarget) return d.type === 'CARD' ? d : null;
    if (!g.isOnline) return d;
    return target === g.myPlayerId ? d : null;
  }

  /* ---------- randare ---------- */

  render() {
    if (!this.active || !this.game.game) return;
    const g = this.game;
    const players = g.game.players;
    const current = g.currentPlayer;
    const myId = g.myPlayerId;
    const myTurn = this.myTurn;
    const dialog = this.visibleDialog();

    if (this.viewedId && !players.some((p) => p.id === this.viewedId)) this.viewedId = null;
    const me = players.find((p) => p.id === myId) ?? null;
    const viewed = players.find((p) => p.id === this.viewedId) ?? me ?? current ?? players[0];

    // --- HUD ---
    const dot = this.$('hud-dot');
    dot.style.background = current ? PAWN_BY_KEY[current.color].hex : '#888';
    this.$('hud-turn').textContent = `LA RÂND: ${current?.name ?? '-'}`;
    this.$('hud-arrow').textContent = this.drawerOpen ? '▲ ÎNCHIDE' : '▼ JUCĂTORI';
    this.$('hud-toggle').setAttribute('aria-expanded', String(!!this.drawerOpen));

    const skip = this.$('skip-banner');
    skip.hidden = !g.skipBanner;
    skip.textContent = g.skipBanner ?? '';

    const wait = this.$('wait-banner');
    const drawHint = g.pendingDraw && myTurn && !g.winnerName
      ? `Atinge pachetul ${g.pendingDraw} de pe tablă ca să tragi un cartonaș`
      : null;
    const waitText = g.isOnline && !myTurn ? `Așteaptă: ${current?.name ?? ''} își joacă tura…` : drawHint;
    wait.hidden = !waitText;
    wait.textContent = waitText ?? '';

    // --- zarul ---
    const busyDie = !!dialog || !!g.winnerName || g.turnAwaitingEnd || !!current?.isBot || !myTurn || this.die.busy;
    this.die.setReady(!busyDie, !busyDie);
    this.die2El.hidden = g.diceCount !== 2;
    this.die2.setReady(!busyDie, !busyDie);
    if (g.rollNonce !== this.lastShownNonce) {
      this.lastShownNonce = g.rollNonce;
      this.showRoll(g.lastDice);
    }

    // --- tabla ---
    const focusIndex = current?.position ?? null;
    const propertyTiles = {};
    this.catalog.board.forEach((t, i) => {
      if (t.type === TILE.PROPERTY) propertyTiles[t.propertyId] = i;
    });
    this.board.setLayout(g.boardLayout ?? 'old');   // tabla partidei (salvată în joc)
    // căsuța unde urmează să ajungă pionul pulsează din clipa în care s-a aruncat zarul
    const moving = current ? this.board.anims.get(current.id) : null;
    if (current && moving && moving.target !== current.position) {
      this.landedTile = current.position >= 0 ? current.position : this.rechematTile();
    }
    this.board.setModel({
      players,
      activePlayerId: current?.id ?? null,
      landedTile: this.landedTile,
      highlightColor: current ? PAWN_BY_KEY[current.color].hex : '#ffd54f',
      drawKind: dialog?.type === 'DRAW' && myTurn && !current?.isBot ? dialog.kind.toLowerCase() : null,
      propertyTiles,
      stretch: this.stretch,
      tilt: this.tilt,
      zoomPreset: this.zoomPreset,
      zoomNonce: this.zoomNonce,
      focusIndex,
    });

    // --- bani: după tabla de mai sus, care știe dacă pionul e încă în drum ---
    const shown = this.syncShownMoney(players);
    const money = this.$('hud-money');
    clear(money);
    for (const p of players) {
      money.append(el('span', {}, el('i', { style: { background: PAWN_BY_KEY[p.color].hex } }), `${fmt(shown.get(p.id))}`));
    }

    // --- drawer ---
    this.renderDrawer(viewed, me, current, dialog);
    this.renderDialog(dialog);
    this.renderWinner();
    if (this.sheetProperty && !dialog && !g.winnerName) this.renderSheet(false);
    else if (this.sheetProperty) this.closeSheet();
    this.renderMoneyChanges(players);
    this.pump();
    this.saveLocal();
  }

  renderDrawer(viewed, me, current, dialog) {
    const g = this.game;
    const players = g.game.players;
    const dp = this.$('drawer-players');
    clear(dp);
    for (const p of players) {
      dp.append(el('span', {}, el('i', { style: { background: PAWN_BY_KEY[p.color].hex } }), `${p.name}: ${ecu(this.shownMoney.get(p.id) ?? p.money)}, ${Object.keys(p.ownedProperties).length} firme`));
    }
    this.$('toggle-stretch').textContent = this.stretch ? 'Tablă: pe tot ecranul' : 'Tablă: proporțională';
    this.$('toggle-tilt').textContent = this.tilt ? 'Înclinare: pornit' : 'Înclinare: oprit';
    this.$('toggle-sound').textContent = sound.enabled ? 'Sunet: pornit' : 'Sunet: oprit';
    this.$('zoom-1').classList.toggle('chip-on', this.zoomPreset === 1);
    this.$('zoom-2').classList.toggle('chip-on', this.zoomPreset === 2);
    this.$('btn-game-exit').textContent = g.isOnline ? 'Părăsește masa' : 'Ieșire în meniu';

    const tabs = this.$('drawer-tabs');
    clear(tabs);
    for (const p of players) {
      tabs.append(el('button', {
        class: `btn ghost small ${viewed.id === p.id ? 'chip-on' : ''}`,
        type: 'button',
        onclick: () => {
          this.viewedId = p.id;
          this.touchDrawer();
          this.render();
        },
      }, el('i', { style: { display: 'inline-block', width: '10px', height: '10px', borderRadius: '50%', background: PAWN_BY_KEY[p.color].hex, marginRight: '6px' } }), p.name));
    }

    // portofoliu: detaliat doar pentru jucătorul la rând (online: doar pentru propriile firme)
    const detailed = g.isOnline ? viewed.id === g.myPlayerId : !!current && viewed.id === current.id;
    const sellMode = detailed && dialog?.type === 'DEBT' && dialog.debt.debtorId === viewed.id;
    const landed = g.landedPropertyId(viewed.id);
    const canBuyAnnexFor = !dialog && g.turnAwaitingEnd && this.myTurn && current?.id === viewed.id && landed && viewed.ownedProperties[landed] !== undefined
      ? landed
      : null;
    // lista de firme apare doar când jucătorul trebuie să aleagă (vânzare la datorie, anexă); altfel doar jurnalul
    if (sellMode || canBuyAnnexFor) this.renderPortfolio(viewed, detailed, canBuyAnnexFor, sellMode);
    else clear(this.$('portfolio'));

    // jurnalul fiecărui jucător: rândurile care îl privesc (numele lui apare în text)
    this.$('history-label').textContent = `Jurnalul lui ${viewed.name}`;
    const hist = this.$('history');
    clear(hist);
    for (const line of g.game.history) {
      if (line.includes(viewed.name)) hist.append(el('li', { text: line }));
    }
  }

  renderPortfolio(viewed, detailed, canBuyAnnexFor, sellMode) {
    const box = this.$('portfolio');
    clear(box);
    const actions = this.catalog.actions;
    box.append(el('p', { class: 'drawer-label', text: `Portofoliul lui ${viewed.name}` }));
    const owned = Object.entries(viewed.ownedProperties);
    if (owned.length === 0) {
      box.append(el('p', { class: 'prop-empty', text: 'Nicio firmă deținută încă.' }));
      return;
    }
    for (const [propId, level] of owned) {
      const prop = actions[propId];
      const v = propertyVisual(propId, prop.name);
      const iconImg = el('img', { alt: '', src: propertyIconPath(propId), onerror: (e) => e.target.replaceWith(el('span', { class: 'ico-emoji', text: v.icon })) });
      const row = el('div', { class: 'prop-row' },
        iconImg,
        el('div', { class: 'grow' },
          el('div', { class: 'name', text: prop.name }),
          el('div', { class: 'sub', text: level === 0 ? 'Titlu de proprietate' : `Titlu + ${level} ${level === 1 ? 'anexă' : 'anexe'}` }),
        ),
        el('button', { class: 'btn ghost small', type: 'button', text: 'Fișă', onclick: () => this.openSheet(propId) }),
      );
      box.append(row);
      if (detailed) {
        for (const annex of prop.annexes) {
          const owned2 = annex.index <= level;
          box.append(el('div', { class: 'annex-line' },
            owned2 ? `Anexa ${annex.index}: profit +${fmt(annex.profit)} ECU (deținută)` : `Anexa ${annex.index}: de cumpărat (${fmt(annex.price)} ECU)`));
        }
        if (canBuyAnnexFor === propId) {
          const next = nextAnnex(prop, level);
          if (next) {
            box.append(el('div', { class: 'annex-line' }, el('button', {
              class: 'btn yellow small', type: 'button',
              text: `EXTINDE / CUMPĂRĂ ANEXA ${next.index} — ${fmt(next.price)} ECU`,
              onclick: () => this.game.buyNextAnnex(propId),
            })));
          }
        }
        if (sellMode) {
          const btns = [];
          if (level > 0) btns.push(el('button', { class: 'btn ghost small', type: 'button', text: `Vinde anexa ${level}`, onclick: () => this.game.settleDebt([{ propertyId: propId, annexIndex: level }]) }));
          btns.push(el('button', { class: 'btn ghost small', type: 'button', text: level === 0 ? 'Vinde titlul' : 'Vinde întreaga acțiune', onclick: () => this.game.settleDebt([{ propertyId: propId, annexIndex: null }]) }));
          box.append(el('div', { class: 'annex-line' }, ...btns));
        }
      } else {
        box.append(el('div', { class: 'locked', text: 'Anexe și chirie: confidențiale' }));
      }
    }
  }

  /* ---------- zar ---------- */

  placeDie(b) {
    const p = b.toScreen(DIE_POS.fx, DIE_POS.fy);
    this.dieEl.style.left = `${p.x}px`;
    this.dieEl.style.top = `${p.y}px`;
    this.dieBadge.style.left = `${p.x}px`;
    this.dieBadge.style.top = `${p.y}px`;
    const p2 = b.toScreen(DIE_POS.fx + DIE2_DX, DIE_POS.fy);
    this.die2El.style.left = `${p2.x}px`;
    this.die2El.style.top = `${p2.y}px`;
  }

  /** Oprește zarele pe valorile oficiale; la două zaruri, insigna arată suma. */
  settleDice(values) {
    const [v1, v2] = values;
    this.die.stopRolling(v1, v2 == null ? String(v1) : String(v1 + v2));
    if (v2 != null) this.die2.stopRolling(v2, null);
  }

  /** Arată o aruncare: zarele se rostogolesc și se opresc pe valorile oficiale. */
  showRoll(values) {
    if (!values?.length) return;
    sound.dice();
    if (this.die.busy) {
      this.settleDice(values);
    } else {
      this.die.startRolling();
      if (values[1] != null) this.die2.startRolling();
      setTimeout(() => this.settleDice(values), ROLL_ANIM_MS);
    }
    this.pump();
  }

  onDieTap() {
    const g = this.game;
    if (this.die.busy || !this.dieEnabled()) return;
    sound.unlock();
    sound.dice();
    this.die.startRolling();
    const two = g.diceCount === 2 && !g.isGuest;   // online rămâne la un singur zar
    if (two) this.die2.startRolling();
    const values = Array.from({ length: two ? 2 : 1 }, () => 1 + Math.floor(Math.random() * 6));
    if (g.isGuest) {
      // oaspetele așteaptă zarul oficial de la gazdă: rostogolirea ține până sosește starea
      g.roll(values[0]);
      setTimeout(() => {
        if (this.die.busy && g.rollNonce === this.lastShownNonce) {
          this.settleDice([g.lastDice[0] ?? values[0]]);
          this.pump();
        }
      }, 6000);
      return;
    }
    setTimeout(() => {
      this.settleDice(values);
      this.lastShownNonce = g.rollNonce + 1;   // aruncarea locală nu se mai animă a doua oară
      setTimeout(() => g.roll(values), 300);
    }, ROLL_ANIM_MS);
    this.pump();
  }

  dieEnabled() {
    const g = this.game;
    const dialog = g.activeDialog;
    const current = g.currentPlayer;
    return !(dialog || g.winnerName || g.turnAwaitingEnd || current?.isBot || !this.myTurn);
  }

  /* ---------- tablă ---------- */

  onTileTap(index) {
    const tile = this.catalog.board[index];
    if (!tile || tile.type !== TILE.PROPERTY) return;
    if (this.visibleDialog() || this.game.winnerName) return;
    this.openSheet(tile.propertyId);
  }

  /** Tabla pentru partida care începe ('old' sau 'new'). */
  setLayout(name) {
    this.board.setLayout(name);
  }

  setZoom(preset) {
    this.zoomPreset = preset;
    this.zoomNonce++;
    this.render();
  }

  setPawnsBusy(busy) {
    const was = this.pawnsBusy;
    this.pawnsBusy = busy;
    if (was && !busy) this.onPawnsSettled();
    this.render();
  }

  /** Căsuța RECHEMAT URGENT (unde ajunge pionul înainte să fie trimis la START). */
  rechematTile() {
    return this.catalog.board.findIndex((t) => t.type === TILE.RECHEMAT);
  }

  /** Pionul a ajuns pe o căsuță: cartonașul de aterizare apare o vreme. */
  onPawnsSettled() {
    const p = this.game.currentPlayer;
    if (!p) return;
    this.landedTile = p.position >= 0 ? p.position : this.rechematTile();
    const tile = this.catalog.board[p.position];
    if (!tile || p.position < 0) {
      this.hideLanding();
      return;
    }
    this.showLanding(tile);
    clearTimeout(this.landingTimer);
    this.landingTimer = setTimeout(() => this.hideLanding(), LANDING_MS);
  }

  hideLanding() {
    this.$('landing-card').hidden = true;
  }

  showLanding(tile) {
    const g = this.game;
    const box = this.$('landing-card');
    const v = tileVisual(tile, this.catalog.actions);
    const prop = tile.type === TILE.PROPERTY ? this.catalog.actions[tile.propertyId] : null;
    const owner = prop ? g.game.players.find((p) => Object.prototype.hasOwnProperty.call(p.ownedProperties, tile.propertyId)) : null;
    const level = owner && prop ? owner.ownedProperties[tile.propertyId] : 0;
    let info;
    if (prop) {
      if (!owner) info = `Titlu de proprietate · cost ${fmt(prop.titlePrice)} ECU · taxă ${fmt(prop.titleTax)} ECU`;
      else if (owner.id === g.myPlayerId) info = `Ai acțiunea ${prop.name}${level ? ` (${level} anexe)` : ''}`;
      else info = `Proprietar: ${owner.name} · chirie ${fmt(calculateRent(prop, level))} ECU`;
    } else {
      info = tileHint(tile) ?? '';
    }
    const iconImg = prop ? el('img', { alt: '', src: propertyIconPath(prop.id), onerror: (e) => e.target.replaceWith(el('span', { text: v.icon })) }) : null;
    box.style.setProperty('--accent', v.accent);
    clear(box);
    box.append(
      el('div', { class: 'ico' }, iconImg ?? v.icon),
      el('div', { class: 'txt' },
        el('h4', { text: prop ? prop.name : v.shortName }),
        el('p', { text: info }),
        prop ? el('p', { class: 'muted', text: 'Atinge pentru fișa acțiunii' }) : null,
      ),
    );
    box.dataset.property = prop ? tile.propertyId : '';
    box.hidden = false;
  }

  onLandingTap() {
    const id = this.$('landing-card').dataset.property;
    if (id) this.openSheet(id);
  }

  /* ---------- sertar ---------- */

  get drawerOpen() {
    return !this.$('drawer').hidden;
  }

  toggleDrawer() {
    const d = this.$('drawer');
    d.hidden = !d.hidden;
    if (!d.hidden) this.touchDrawer();
    this.render();
  }

  /** Atingerile în sertar țin sertarul deschis. Se închide singur după 4 secunde de inactivitate. */
  touchDrawer() {
    clearTimeout(this.drawerTimer);
    this.drawerTimer = setTimeout(() => {
      this.$('drawer').hidden = true;
      this.render();
    }, 4000);
  }

  /* ---------- dialoguri ---------- */

  renderDialog(d) {
    // Tragerea din pachet nu are fereastră: pachetul pulsează pe tablă și se atinge direct.
    // Un modal gol ar acoperi tabla cu fundalul întunecat și ar bloca atingerea pachetului.
    if (!d || d.type === 'DRAW') {
      if (this.modalKey && this.modalKey.startsWith('dlg:')) this.closeModal();
      return;
    }
    const key = this.dialogKey(d);
    if (key === this.modalKey) return;
    this.sheetProperty = null;   // fișa se închide când apare un dialog de joc
    this.openModal(key, this.buildDialog(d), d.type === 'CARD');
    if (d.type === 'CARD') sound.card();
    if (d.type === 'BUY') sound.coin();
    if (d.type === 'DEBT') sound.alert();
  }

  dialogKey(d) {
    const g = this.game;
    const parts = [d.type];
    if (d.offer) parts.push(d.offer.propertyId, d.offer.option.type, d.offer.option.annex?.index ?? '', d.offer.playerId);
    if (d.due) parts.push(d.due.propertyId, d.due.amount, d.due.payerId);
    if (d.debt) parts.push(d.debt.debtorId, d.debt.amount, d.debt.creditorId ?? '', d.debt.reason);
    if (d.card) parts.push(d.card.text, d.card.amount ?? '', d.card.cardId ?? '');
    if (d.kind) parts.push(d.kind);
    if (d.debtorName) parts.push(d.debtorName);
    if (d.playerName) parts.push(d.playerName);
    parts.push(g.game.players.length, g.game.players.find((p) => p.id === d.debt?.debtorId)?.money ?? '');
    return `dlg:${parts.join('|')}`;
  }

  buildDialog(d) {
    const g = this.game;
    const btn = (text, cls, onclick, disabled = false) => el('button', { class: `btn ${cls}`, type: 'button', text, onclick, disabled: disabled || undefined });
    switch (d.type) {
      case 'BUY': {
        const buyer = g.game.players.find((p) => p.id === d.offer.playerId);
        const prop = d.property;
        const isTitle = d.offer.option.type === 'TITLE';
        const price = isTitle ? d.offer.option.price : d.offer.option.annex.price;
        const canAfford = buyer.money >= price;
        const v = propertyVisual(prop.id, prop.name);
        return this.dialogBox({
          title: isTitle ? prop.name : `Anexa ${d.offer.option.annex.index} — ${prop.name}`,
          icon: propertyIconPath(prop.id),
          fallbackIcon: v.icon,
          accent: v.accent,
          body: [
            el('p', { text: `Cost achiziție: ${fmt(price)} ECU` }),
            el('p', { text: `Sold: ${fmt(buyer.money)} ECU` }),
            isTitle ? el('p', { class: 'muted', text: `Atenție: dacă nu cumperi, achiți Băncii taxa de ${fmt(prop.titleTax)} ECU.` }) : null,
            !canAfford ? el('p', { class: 'error', text: 'Fonduri insuficiente pentru cumpărare.' }) : null,
          ],
          actions: [
            btn('NU', 'ghost', () => this.game.refuseOffer()),
            btn('DA, CUMPĂR', 'primary', () => this.game.acceptOffer(), !canAfford),
          ],
        });
      }
      case 'BANK_FEE':
        return this.dialogBox({
          title: 'CHIRIE BANCĂ',
          body: [el('p', { text: `Ai refuzat achiziția. Plătești taxa de ${fmt(d.taxAmount)} ECU către Bancă (${d.property.name}).` })],
          actions: [btn('OK', 'primary', () => this.game.acknowledgeBankFee())],
        });
      case 'RENT': {
        const level = d.owner.ownedProperties[d.property.id] ?? 0;
        const lines = [
          el('p', { text: `Proprietar: ${d.owner.name}. Trebuie să achiți chiria de ${fmt(d.rentAmount)} ECU.` }),
          el('p', { class: 'muted', text: `Taxă titlu: ${fmt(d.property.titleTax)} ECU` }),
          ...d.property.annexes.filter((a) => a.index <= level).map((a) => el('p', { class: 'muted', text: `Anexa ${a.index}: +${fmt(a.profit)} ECU` })),
        ];
        const v = propertyVisual(d.property.id, d.property.name);
        return this.dialogBox({
          title: `Ai oprit la ${d.property.name}!`,
          icon: propertyIconPath(d.property.id),
          fallbackIcon: v.icon,
          accent: v.accent,
          body: lines,
          actions: [btn('PLĂTEȘTE', 'primary', () => this.game.payRent())],
        });
      }
      case 'CARD':
        return this.cardDialog(d.card, !!this.game.currentPlayer?.isBot);
      case 'DEBT':
        return this.liquidationDialog(d.debt);
      case 'LIQUIDATION_HANDOVER':
        return this.dialogBox({
          title: 'Predați dispozitivul',
          body: [el('p', { text: `Atenție: ${d.debtorName} trebuie să își lichideze din active! Predați dispozitivul lui ${d.debtorName} — Apasă când ești gata!` })],
          actions: [btn('SUNT GATA', 'primary', () => this.game.acknowledgeLiquidation())],
        });
      case 'HANDOVER':
        return this.dialogBox({
          title: 'Predați telefonul',
          body: [el('p', { text: `Urmează la rând: ${d.playerName} — Apasă când ești gata!` })],
          actions: [btn('SUNT GATA', 'primary', () => this.game.acknowledgeHandover())],
        });
      default:
        return el('div');
    }
  }

  dialogBox({ title, icon, fallbackIcon, accent, body, actions }) {
    const head = el('h3', null,
      icon ? el('img', { class: 'ico-sm', alt: '', src: icon, onerror: (e) => e.target.replaceWith(el('span', { class: 'emoji-ico', style: { '--accent': accent ?? '#555' }, text: fallbackIcon ?? '🏢' })) }) : null,
      el('span', { text: title }),
    );
    return el('div', { class: 'modal', role: 'dialog', 'aria-modal': 'true' }, head, ...body, el('div', { class: 'actions' }, ...actions));
  }

  cardDialog(card, byBot = false) {
    const art = card.cardId ? this.catalog.cardArt[card.cardId] : null;
    const amountTxt = card.amount != null ? `${card.outcome === CardOutcome.LOSS ? '−' : '+'}${fmt(card.amount)} ECU` : null;
    const outcomeCls = card.outcome === CardOutcome.GAIN ? 'outcome-gain' : card.outcome === CardOutcome.LOSS ? 'outcome-loss' : '';
    // cartonașul se întoarce (animația de la afișare); rezultatul și destinația stau SUB el, nu peste text
    const visual = art
      ? el('div', { class: 'card-flip' }, el('div', { class: 'inner' },
          el('img', { class: 'card-img', alt: card.text, src: art }),
        ))
      : el('div', { class: 'card-text-only', text: card.text });
    const result = amountTxt ? el('p', { class: `card-result ${outcomeCls}`, text: amountTxt }) : null;
    const dest = card.destination ? el('p', { class: 'card-note', text: card.destination }) : null;
    return el('div', { class: 'modal wide', role: 'dialog', 'aria-modal': 'true' },
      el('h3', { text: card.kind === 'LOTO' ? 'Cartonaș LOTO' : 'Cartonaș TELEX' }),
      visual,
      result,
      dest,
      byBot
        ? el('p', { class: 'muted', text: 'Robotul a tras cartonașul și îl citește…' })
        : el('div', { class: 'actions' }, el('button', { class: 'btn primary', type: 'button', text: 'Am înțeles', onclick: () => this.game.dismissCard() })),
    );
  }

  liquidationDialog(debt) {
    const g = this.game;
    const debtor = g.game.players.find((p) => p.id === debt.debtorId);
    if (!debtor) return el('div');
    const creditor = debt.creditorId == null ? 'Băncii' : `jucătorului ${g.game.players.find((p) => p.id === debt.creditorId)?.name ?? debt.creditorId}`;
    const sellable = g.sellableFor(debtor.id);
    const rows = sellable.map((sale) => {
      const prop = this.catalog.actions[sale.propertyId];
      const level = debtor.ownedProperties[sale.propertyId] ?? 0;
      let label;
      let value;
      if (sale.annexIndex != null) {
        const annex = prop.annexes.find((a) => a.index === sale.annexIndex);
        label = `Anexa ${annex.index} la ${prop.name}`;
        value = sellBackValue(annex.price);
      } else if (level > 0) {
        label = `Întreaga acțiune ${prop.name} (titlu + ${level} anexe)`;
        value = wholeSaleValue(prop, level);
      } else {
        label = `Titlul ${prop.name}`;
        value = sellBackValue(prop.titlePrice);
      }
      return el('div', { class: 'sell-row' },
        el('span', { class: 'grow', text: `${label} (+${fmt(value)} ECU, ${GameRules.RESALE_RATIO_PERCENT}%)` }),
        el('button', { class: 'btn ghost small', type: 'button', text: 'Vinde', onclick: () => this.game.settleDebt([sale]) }),
      );
    });
    return el('div', { class: 'modal', role: 'dialog', 'aria-modal': 'true' },
      el('h3', { text: 'Fonduri insuficiente pentru plată! Lichidați acțiuni sau anexe' }),
      el('p', { text: `${debtor.name} datorează ${fmt(debt.amount)} ECU ${creditor} (${debt.reason}).` }),
      el('p', { text: `Sold lichid: ${fmt(debtor.money)} ECU. Lipsesc: ${fmt(debt.amount - debtor.money)} ECU.` }),
      sellable.length === 0 ? el('p', { class: 'muted', text: 'Nu mai aveți acțiuni de vândut.' }) : null,
      ...rows,
      el('div', { class: 'actions' }, sellable.length === 0
        ? el('button', { class: 'btn danger', type: 'button', text: 'Declară faliment', onclick: () => this.game.settleDebt([]) })
        : null),
    );
  }

  renderWinner() {
    const g = this.game;
    if (!g.winnerName) {
      this.winVideoWatched = false;
      if (this.modalKey === 'winner') this.closeModal();
      return;
    }
    if (this.modalKey === 'winner' || this.winVideoPlaying) return;
    if (!this.winVideoWatched) {
      this.playWinVideo();
      return;
    }
    sound.buy();
    const node = el('div', { class: 'modal', role: 'dialog', 'aria-modal': 'true' },
      el('h3', { text: 'Joc terminat' }),
      el('p', { html: `<strong>Câștigător: ${escapeHtml(g.winnerName)}</strong>` }),
      g.finalRanking.length
        ? el('div', null, el('p', { class: 'muted', text: 'Clasament (bani + acțiuni la 80%):' }), ...g.finalRanking.map((line, i) => el('p', { text: `${i + 1}. ${line}` })))
        : null,
      el('div', { class: 'actions' }, el('button', { class: 'btn primary', type: 'button', text: 'Meniu', onclick: () => this.exitToMenu() })),
    );
    this.openModal('winner', node);
  }

  /** Clipul de victorie se redă pe tot ecranul; apoi apare fereastra cu câștigătorul. */
  playWinVideo() {
    const overlay = $('win-overlay');
    const video = $('win-video');
    this.winVideoPlaying = true;
    overlay.hidden = false;
    const finish = () => {
      if (!this.winVideoPlaying) return;
      this.winVideoPlaying = false;
      this.winVideoWatched = true;
      video.onended = null;
      video.pause();
      overlay.hidden = true;
      this.renderWinner();
    };
    video.onended = finish;
    $('win-skip').onclick = finish;
    video.currentTime = 0;
    video.play().catch(finish);
  }

  /* ---------- fișa acțiunii ---------- */

  openSheet(propertyId) {
    const g = this.game;
    if (!this.catalog.actions[propertyId]) return;
    if (this.visibleDialog() || g.winnerName) return;
    this.sheetProperty = propertyId;
    this.sheetPage = 0;
    this.renderSheet(true);
  }

  renderSheet(rebuild) {
    const id = this.sheetProperty;
    if (!id) {
      if (this.modalKey?.startsWith('sheet:')) this.closeModal();
      return;
    }
    const g = this.game;
    const prop = this.catalog.actions[id];
    const owner = g.game?.players.find((p) => Object.prototype.hasOwnProperty.call(p.ownedProperties, id)) ?? null;
    const level = owner?.ownedProperties[id] ?? 0;
    const current = g.currentPlayer;
    const dialog = this.visibleDialog();
    const canBuy = !dialog && g.turnAwaitingEnd && this.myTurn && current?.id === owner?.id && owner && nextAnnex(prop, level) !== null;
    const next = canBuy ? nextAnnex(prop, level) : null;
    const pages = prop.annexes.length + 1;
    this.sheetPage = Math.min(Math.max(0, this.sheetPage), pages - 1);

    if (rebuild || this.modalKey !== `sheet:${id}`) {
      const img = el('img', { alt: prop.name, src: actionArtPath(id, 0) });
      const dots = el('div', { class: 'dots' });
      const prev = el('button', { class: 'btn ghost small', type: 'button', text: '‹', onclick: () => this.turnSheet(-1) });
      const nextBtn = el('button', { class: 'btn ghost small', type: 'button', text: '›', onclick: () => this.turnSheet(1) });
      const buy = el('button', { class: 'btn yellow', type: 'button', id: 'sheet-buy', onclick: () => this.game.buyNextAnnex(id) });
      const info = el('p', { class: 'muted', id: 'sheet-info' });
      const node = el('div', { class: 'modal wide', role: 'dialog', 'aria-modal': 'true' },
        el('h3', null,
          el('span', { text: prop.name }),
          el('span', { class: 'spacer' }),
          el('button', { class: 'btn ghost small', type: 'button', text: '✕', onclick: () => this.closeSheet() })),
        el('div', { class: 'sheet-pages' }, img),
        el('div', { class: 'sheet-nav' }, prev, dots, nextBtn),
        info,
        el('div', { class: 'actions' }, buy),
      );
      this.openModal(`sheet:${id}`, node);
    }
    const modal = this.modalRoot.querySelector('.modal');
    if (!modal) return;
    const img = modal.querySelector('.sheet-pages img');
    img.src = actionArtPath(id, this.sheetPage);
    img.alt = this.sheetPage === 0 ? `Titlu ${prop.name}` : `Anexa ${this.sheetPage}`;
    const dots = modal.querySelector('.dots');
    clear(dots);
    for (let i = 0; i < pages; i++) dots.append(el('i', { class: i === this.sheetPage ? 'on' : '' }));
    const info = modal.querySelector('#sheet-info');
    info.textContent = this.sheetPage === 0
      ? `Titlu de proprietate · cost ${fmt(prop.titlePrice)} ECU · taxă ${fmt(prop.titleTax)} ECU` +
        (owner ? ` · proprietar: ${owner.name}` : ' · De vânzare')
      : (() => {
          const a = prop.annexes[this.sheetPage - 1];
          const own = owner && a.index <= level;
          return `Anexa ${a.index}: ${fmt(a.price)} ECU · profit +${fmt(a.profit)} ECU${own ? ' · DEȚINUTĂ' : ''}`;
        })();
    const buy = modal.querySelector('#sheet-buy');
    buy.hidden = !next;
    if (next) buy.textContent = `CUMPĂRĂ ANEXA ${next.index} — ${fmt(next.price)} ECU`;
  }

  turnSheet(delta) {
    const id = this.sheetProperty;
    if (!id) return;
    const pages = this.catalog.actions[id].annexes.length + 1;
    this.sheetPage = (this.sheetPage + delta + pages) % pages;
    this.renderSheet(false);
  }

  closeSheet() {
    this.sheetProperty = null;
    this.closeModal();
  }

  /* ---------- modal comun ---------- */

  openModal(key, node) {
    if (this.modalKey === key) return;
    this.modalKey = key;
    clear(this.modalRoot);
    this.modalRoot.append(node);
    this.modalRoot.hidden = false;
    // atingerea fundalului închide doar fișa; dialogurile de joc rămân până se rezolvă
    this.modalRoot.onclick = (e) => {
      if (e.target === this.modalRoot && key.startsWith('sheet:')) this.closeSheet();
    };
  }

  closeModal() {
    this.modalKey = null;
    this.modalRoot.hidden = true;
    clear(this.modalRoot);
  }

  /**
   * Banii afișați. O creștere (bancă, START, cartonaș) apare abia când pionul ajunge pe căsuță;
   * o scădere apare imediat. Motorul și starea jocului nu se schimbă: doar afișarea.
   */
  syncShownMoney(players) {
    const out = new Map();
    for (const p of players) {
      const anim = this.board.anims.get(p.id);
      const holding = !!anim && anim.moving && !anim.arrived;
      const before = this.shownMoney.get(p.id);
      const shown = before === undefined || !holding || p.money < before ? p.money : before;
      out.set(p.id, shown);
    }
    this.shownMoney = out;
    return out;
  }

  renderMoneyChanges(players) {
    const float = this.$('money-float');
    for (const p of players) {
      const value = this.shownMoney.get(p.id) ?? p.money;
      const prev = this.prevMoney.get(p.id);
      this.prevMoney.set(p.id, value);
      if (prev == null || prev === value) continue;
      const diff = value - prev;
      const chip = el('span', { class: diff > 0 ? 'plus' : 'minus', text: `${p.name} ${diff > 0 ? '+' : '−'}${fmt(Math.abs(diff))}` });
      float.append(chip);
      setTimeout(() => chip.remove(), 3000);
      if (diff > 0) sound.coin();
    }
    for (const id of [...this.prevMoney.keys()]) if (!players.some((p) => p.id === id)) this.prevMoney.delete(id);
  }

  /** Ieșirea din joc e blocată cât timp există o decizie obligatorie (cumpărare, chirie, datorie). */
  needsDecision() {
    const d = this.visibleDialog();
    return !!d && DECISION_DIALOGS.includes(d.type);
  }

  /* ---------- orchestrare ---------- */

  /** Programează roboții și încheierea automată a turei. Cheile includ versiunea stării. */
  pump() {
    if (!this.active) return;
    const g = this.game;
    const botKey = `${g.version}|${this.pawnsBusy}|${this.die.busy}|${g.hasBotAction}`;
    if (botKey !== this.botKey) {
      this.botKey = botKey;
      clearTimeout(this.botTimer);
      this.botTimer = null;
      if (g.hasBotAction && !this.pawnsBusy && !this.die.busy) {
        const delay = g.activeDialog?.type === 'CARD' ? CARD_SHOW_MS
          : g.skipBanner && g.currentPlayer?.isBot ? SKIP_SHOW_MS
          : BOT_DELAY_MS;
        this.botTimer = setTimeout(() => {
          this.botTimer = null;
          if (g.hasBotAction && !this.pawnsBusy && !this.die.busy) g.botNextAction();
        }, delay);
      }
    }

    const p = g.currentPlayer;
    const endCond = !this.pawnsBusy && g.turnAwaitingEnd && !g.activeDialog && !g.winnerName && !!p && !p.isBot
      && (!g.isOnline || p.id === g.myPlayerId);
    const endKey = `${g.version}|${this.pawnsBusy}|${endCond}|${g.skipBanner ? 1 : 0}`;
    if (endKey !== this.endKey) {
      this.endKey = endKey;
      clearTimeout(this.endTimer);
      this.endTimer = null;
      if (endCond) {
        this.endTimer = setTimeout(() => {
          this.endTimer = null;
          const q = g.currentPlayer;
          const again = !this.pawnsBusy && g.turnAwaitingEnd && !g.activeDialog && !g.winnerName && !!q && !q.isBot
            && (!g.isOnline || q.id === g.myPlayerId);
          if (again) g.endTurn();
        }, g.skipBanner ? END_DELAY_SKIP_MS : END_DELAY_MS);
      }
    }
  }

  tickClock() {
    const g = this.game;
    const clock = this.$('hud-clock');
    const left = g.timeLeftMs();
    clock.hidden = left == null;
    if (left == null) return;
    const s = Math.floor(left / 1000);
    clock.textContent = `⏱ ${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
    clock.classList.toggle('low', s < 60);
    if (left <= 0 && !g.isOnline && !g.isGuest && !g.winnerName) g.timeUp();
  }

  saveLocal() {
    const g = this.game;
    if (g.isOnline || !g.game) return;
    if (g.winnerName) storage.clearGame();
    else storage.saveGame(g.snapshot(), g.timeLeftMs());
  }

  exitToMenu() {
    this.onExit();
  }
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
}

