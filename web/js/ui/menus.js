// Ecranele din afara jocului: meniul principal, configurarea, JOC ONLINE, camera de așteptare,
// regulamentul și dialogurile „Despre” / „Donează”. Port din HomeScreen.kt, SetupScreen (GameScreen.kt),
// OnlineScreens.kt și RulesScreen.kt.

import { el, clear } from './dom.js';
import { storage } from '../storage.js';
import { sound } from '../audio.js';
import { PAWN_COLORS, PAWN_BY_KEY } from '../core/model.js';
import { STARTING_MONEY_STEP, maxCustomStartingMoney } from '../core/rules.js';
import { RoomRole } from '../online/protocol.js';
import { CONNECT_TIMEOUT_MS, DONATE } from '../config.js';

const $ = (id) => document.getElementById(id);

/* ---------- Locurile: OM → ROBOT → CATALINROBOT → OM ---------- */

function seatLabel(seat) {
  if (!seat.isBot) return 'OM';
  return seat.catalin ? 'CATALINROBOT' : 'ROBOT';
}

function cycleSeat(seat) {
  if (!seat.isBot) { seat.isBot = true; seat.catalin = false; }
  else if (!seat.catalin) seat.catalin = true;
  else { seat.isBot = false; seat.catalin = false; }
}

/* ---------- Ecranul principal ---------- */

export function initHome(app) {
  $('btn-new').addEventListener('click', () => app.openSetup(1));
  $('btn-new-dice').addEventListener('click', () => app.openSetup(2));
  $('btn-online').addEventListener('click', () => app.openOnline());
  $('btn-rules').addEventListener('click', () => app.show('rules'));
  $('btn-exit').addEventListener('click', () => app.show('outro'));
  $('btn-resume').addEventListener('click', () => app.continueGame());
  $('btn-about').addEventListener('click', () => openAbout(app));
  $('btn-donate').addEventListener('click', () => openDonate(app));
}

export function refreshHome() {
  $('btn-resume').hidden = !storage.loadGame();
}

function openModal(app, node) {
  const root = $('modal-root');
  clear(root);
  root.append(node);
  root.hidden = false;
  root.onclick = (e) => {
    if (e.target === root) closeModal();
  };
}

export function closeModal() {
  const root = $('modal-root');
  root.hidden = true;
  clear(root);
  root.onclick = null;
}

function openAbout(app) {
  openModal(app, el('div', { class: 'modal', role: 'dialog', 'aria-modal': 'true' },
    el('h3', null, el('img', { class: 'ico-sm', src: 'img/logo_privatizare.webp', alt: '' }), el('span', { text: 'Privatizare' })),
    el('p', { class: 'muted', text: 'Joc de societate românesc' }),
    el('hr'),
    el('p', { text: 'Joc de societate românesc, pentru tineret și adulți. Se joacă în 2–4 persoane.' }),
    el('p', { html: '<strong>Proiect independent realizat de Chirazic Catalin</strong>' }),
    el('p', { class: 'muted', text: 'Contact: catalinchirazic7@gmail.com' }),
    el('p', { class: 'muted', text: 'Versiune 1.04 (web)' }),
    el('div', { class: 'actions' }, el('button', { class: 'btn primary', type: 'button', text: 'Închide', onclick: closeModal })),
  ));
}

function openDonate(app) {
  openModal(app, el('div', { class: 'modal', role: 'dialog', 'aria-modal': 'true' },
    el('h3', null, el('span', { text: 'Donează' })),
    el('p', { text: 'Dacă îți place jocul, poți susține dezvoltarea lui:' }),
    el('img', { src: 'img/donate_qr.png', alt: 'Cod QR pentru donații', style: { width: '200px', display: 'block', margin: '8px auto', borderRadius: '10px', background: '#fff' } }),
    el('p', { class: 'muted', text: DONATE.url }),
    el('div', { class: 'actions' },
      el('a', { class: 'btn yellow', href: DONATE.url, target: '_blank', rel: 'noopener', text: 'Deschide link-ul' }),
      el('button', { class: 'btn ghost', type: 'button', text: 'Închide', onclick: closeModal }),
    ),
  ));
}

/* ---------- Configurare ---------- */

const SEAT_COUNT_OPTIONS = [2, 3, 4];
const TIME_OPTIONS = [null, 30, 60, 90];

export class SetupView {
  constructor(app) {
    this.app = app;
    const saved = storage.loadSetup();
    this.count = saved?.count ?? 2;
    this.money = saved?.money ?? null;          // null = „Maxim”
    this.time = saved?.time ?? null;            // null = fără limită
    this.dice = saved?.dice === 2 ? 2 : 1;      // 1 zar (implicit) sau 2 zaruri (mod nou, doar local)
    this.layout = saved?.layout === 'new' ? 'new' : 'old';   // tabla veche (implicit) sau tabla nouă
    const defaults = PAWN_COLORS.slice(0, 4).map((c) => c.key);
    this.seats = [0, 1, 2, 3].map((i) => ({
      name: saved?.seats?.[i]?.name ?? `Jucător ${i + 1}`,
      colorKey: saved?.seats?.[i]?.colorKey ?? defaults[i],
      isBot: saved?.seats?.[i]?.isBot ?? false,
      catalin: saved?.seats?.[i]?.catalin ?? false,   // CatalinRobot (doar când isBot)
    }));
    this.bind();
  }

  bind() {
    $('btn-start-game').addEventListener('click', () => this.start());
  }

  active() {
    return this.seats.slice(0, this.count);
  }

  valid() {
    const seats = this.active();
    const colors = new Set(seats.map((s) => s.colorKey));
    return seats.every((s) => s.name.trim().length > 0) && colors.size === seats.length && seats.some((s) => !s.isBot);
  }

  render() {
    const cap = maxCustomStartingMoney(this.count);
    if (this.money != null && this.money > cap) this.money = null;

    const count = $('setup-count');
    clear(count);
    for (const n of SEAT_COUNT_OPTIONS) {
      count.append(el('button', {
        class: `btn chip ${n === this.count ? 'chip-on' : ''}`, type: 'button', text: String(n),
        onclick: () => { this.count = n; this.render(); },
      }));
    }

    const dice = $('setup-dice');
    clear(dice);
    for (const n of [1, 2]) {
      dice.append(el('button', {
        class: `btn chip ${n === this.dice ? 'chip-on' : ''}`, type: 'button', text: n === 1 ? '1 zar' : '2 zaruri',
        onclick: () => { this.dice = n; this.render(); },
      }));
    }

    const board = $('setup-board');
    clear(board);
    for (const [key, label] of [['old', 'Tablă veche'], ['new', 'Tablă nouă']]) {
      board.append(el('button', {
        class: `btn chip ${key === this.layout ? 'chip-on' : ''}`, type: 'button', text: label,
        onclick: () => { this.layout = key; this.render(); },
      }));
    }

    const time = $('setup-time');
    clear(time);
    for (const t of TIME_OPTIONS) {
      time.append(el('button', {
        class: `btn chip ${t === this.time ? 'chip-on' : ''}`, type: 'button', text: t == null ? 'Fără limită' : `${t} min`,
        onclick: () => { this.time = t; this.render(); },
      }));
    }

    const money = $('setup-money');
    clear(money);
    const options = [null];
    for (let v = STARTING_MONEY_STEP; v <= cap; v += STARTING_MONEY_STEP) options.push(v);
    for (const v of options) {
      money.append(el('button', {
        class: `btn chip ${v === this.money ? 'chip-on' : ''}`, type: 'button', text: v == null ? 'Maxim' : `${v / 1000}k`,
        onclick: () => { this.money = v; this.render(); },
      }));
    }

    const seats = $('setup-seats');
    clear(seats);
    this.active().forEach((seat, i) => {
      seats.append(el('div', { class: 'seat' },
        el('div', { class: 'seat-head' },
          el('strong', { text: `Locul ${i + 1}` }),
          el('button', {
            class: 'btn ghost small', type: 'button', text: seatLabel(seat),
            onclick: () => { cycleSeat(seat); this.render(); },
          }),
        ),
        el('label', { class: 'field-label', text: 'Nume' },
          el('input', {
            value: seat.name, maxlength: 14, autocomplete: 'off',
            oninput: (e) => { seat.name = e.target.value; this.updateStart(); },
          }),
        ),
        el('p', { class: 'hint', text: 'Pion' }),
        el('div', { class: 'swatches' }, PAWN_COLORS.map((c) => el('button', {
          class: 'swatch', type: 'button', role: 'radio', 'aria-checked': String(seat.colorKey === c.key), 'aria-label': c.name,
          title: c.name,
          onclick: () => {
            // dacă pionul e al altui loc, schimbăm între ele
            const other = this.active().find((s, j) => j !== i && s.colorKey === c.key);
            if (other) other.colorKey = seat.colorKey;
            seat.colorKey = c.key;
            this.render();
          },
        }, el('img', { src: c.img, alt: '' })))),
      ));
    });
    this.updateStart();
  }

  updateStart() {
    $('btn-start-game').disabled = !this.valid();
  }

  start() {
    if (!this.valid()) return;
    const config = { count: this.count, money: this.money, time: this.time, dice: this.dice, layout: this.layout, seats: this.seats.map((s) => ({ ...s })) };
    storage.saveSetup(config);
    const setup = this.active().map((s) => ({ name: s.name.trim(), colorKey: s.colorKey, isBot: s.isBot, catalin: s.catalin }));
    this.app.startLocal(setup, this.money, this.time, this.dice, this.layout);
  }
}

/* ---------- JOC ONLINE: meniul ---------- */

export class OnlineMenuView {
  constructor(app) {
    this.app = app;
    this.color = PAWN_COLORS[0].key;
    this.timeout = null;
    this.lastBusy = false;
    $('online-name').addEventListener('input', () => this.update());
    $('online-code').addEventListener('input', (e) => {
      e.target.value = e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 4);
      this.onCodeChange(e.target.value);
      this.update();
    });
    $('btn-create-room').addEventListener('click', () => this.create());
    $('btn-join-room').addEventListener('click', () => this.join());
  }

  get controller() {
    return this.app.online;
  }

  onCodeChange(code) {
    this.controller?.peekRoom(code);
  }

  enter() {
    this.render();
    this.update();
  }

  render() {
    const ctl = this.controller;
    const taken = ctl?.takenColors ?? new Set();
    if (taken.has(this.color)) {
      this.color = PAWN_COLORS.find((c) => !taken.has(c.key))?.key ?? this.color;
    }
    const wrap = $('online-colors');
    clear(wrap);
    for (const c of PAWN_COLORS) {
      const isTaken = taken.has(c.key) && c.key !== this.color;
      wrap.append(el('button', {
        class: 'swatch', type: 'button', role: 'radio', 'aria-checked': String(c.key === this.color),
        disabled: isTaken, title: c.name,
        onclick: () => { this.color = c.key; this.render(); this.update(); },
      }, el('img', { src: c.img, alt: '', style: { opacity: isTaken ? 0.25 : 1 } })));
    }
    $('online-error').textContent = ctl?.error ?? '';
    $('online-status').textContent = ctl?.busy ? 'Se conectează…' : '';
    if (ctl?.busy && !this.lastBusy) {
      clearTimeout(this.timeout);
      this.timeout = setTimeout(() => ctl.timeoutIfBusy(), CONNECT_TIMEOUT_MS);
    }
    this.lastBusy = !!ctl?.busy;
    this.update();
  }

  update() {
    const name = $('online-name').value.trim();
    const code = $('online-code').value;
    const busy = !!this.controller?.busy;
    $('btn-create-room').disabled = !name || busy;
    $('btn-join-room').disabled = !name || code.length !== 4 || busy;
  }

  create() {
    sound.unlock();
    this.app.requestLandscape();
    this.error('');
    this.controller.createRoom($('online-name').value, this.color);
  }

  join() {
    sound.unlock();
    this.app.requestLandscape();
    this.controller.joinRoom($('online-code').value, $('online-name').value, this.color);
  }

  error(text) {
    $('online-error').textContent = text;
  }
}

/* ---------- Camera de așteptare ---------- */

export function renderLobby(app) {
  const ctl = app.online;
  if (!ctl) return;
  const info = ctl.info;
  $('lobby-code').textContent = ctl.code ?? '----';
  const seats = $('lobby-seats');
  clear(seats);
  for (let i = 0; i < 4; i++) {
    const p = info?.players[i];
    if (p) {
      const role = { [RoomRole.HOST]: 'Gazdă', [RoomRole.GUEST]: 'Oaspete', [RoomRole.BOT]: 'Robot' }[p.role];
      seats.append(el('div', { class: 'lobby-seat' },
        el('img', { src: PAWN_BY_KEY[p.color].img, alt: '' }),
        el('strong', { text: p.name }),
        el('small', { text: role }),
      ));
    } else {
      seats.append(el('div', { class: 'lobby-seat' }, el('div', { class: 'empty', text: 'liber' })));
    }
  }
  const host = ctl.isHost;
  $('btn-add-bot').hidden = !host;
  $('btn-start-match').hidden = !host;
  $('btn-add-bot').disabled = !info || info.players.length >= 4;
  $('btn-start-match').disabled = !info || !(info.players.length >= 2 && info.players.some((p) => p.role !== RoomRole.BOT));
  $('lobby-hint').textContent = host
    ? (info && info.players.length >= 2 ? '' : 'Sunt necesari cel puțin 2 jucători.')
    : 'Așteptăm ca gazda să pornească meciul…';
  $('lobby-error').textContent = ctl.error ?? '';
}

export function bindLobby(app) {
  $('btn-add-bot').addEventListener('click', () => app.online.addBot());
  $('btn-start-match').addEventListener('click', () => {
    sound.unlock();
    app.requestLandscape();
    app.online.startMatch();
  });
  $('btn-leave-room').addEventListener('click', () => {
    app.online.leave();
    app.show('home');
  });
}

/* ---------- Regulament ---------- */

export function initRules() {
  const vp = $('rules-viewport');
  const img = $('rules-img');
  const st = { scale: 1, x: 0, y: 0 };
  const pointers = new Map();
  let gesture = null;

  const apply = () => {
    img.style.transform = `translate(calc(-50% + ${st.x}px), calc(-50% + ${st.y}px)) scale(${st.scale})`;
  };
  // dimensiunile din layout (fără transformări): imaginea scalată nu iese din fereastră
  const clamp = () => {
    st.scale = Math.min(4, Math.max(1, st.scale));
    const maxX = Math.max(0, (img.offsetWidth * st.scale - vp.clientWidth) / 2);
    const maxY = Math.max(0, (img.offsetHeight * st.scale - vp.clientHeight) / 2);
    st.x = Math.min(maxX, Math.max(-maxX, st.x));
    st.y = Math.min(maxY, Math.max(-maxY, st.y));
  };
  const reset = () => {
    st.scale = 1;
    st.x = 0;
    st.y = 0;
    apply();
  };
  $('rules-reset').addEventListener('click', reset);

  vp.addEventListener('pointerdown', (e) => {
    vp.setPointerCapture?.(e.pointerId);
    pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });
    if (pointers.size === 2) {
      const [a, b] = [...pointers.values()];
      gesture = { dist: Math.hypot(a.x - b.x, a.y - b.y) || 1, scale: st.scale };
    }
  });
  vp.addEventListener('pointermove', (e) => {
    const p = pointers.get(e.pointerId);
    if (!p) return;
    const dx = e.clientX - p.x;
    const dy = e.clientY - p.y;
    p.x = e.clientX;
    p.y = e.clientY;
    if (pointers.size === 2 && gesture) {
      const [a, b] = [...pointers.values()];
      const dist = Math.hypot(a.x - b.x, a.y - b.y) || 1;
      st.scale = gesture.scale * (dist / gesture.dist);
    } else if (pointers.size === 1 && st.scale > 1) {
      st.x += dx;
      st.y += dy;
    }
    clamp();
    apply();
  });
  const up = (e) => {
    pointers.delete(e.pointerId);
    if (pointers.size < 2) gesture = null;
  };
  vp.addEventListener('pointerup', up);
  vp.addEventListener('pointercancel', up);
  vp.addEventListener('wheel', (e) => {
    e.preventDefault();
    st.scale *= e.deltaY < 0 ? 1.12 : 1 / 1.12;
    clamp();
    apply();
  }, { passive: false });
  apply();
  return { reset };
}

