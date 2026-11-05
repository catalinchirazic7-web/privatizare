// Tabla de joc pe canvas: fotografia tablei, pachetele LOTO/TELEX, marcajele de proprietate,
// bancnotele jucătorului, pionii animați pas cu pas, zoom (1x–4x) și atingerile.
// Port din BoardView.kt, PawnView și CashStacks.kt. Coordonatele căsuțelor sunt fracții [0,1].

import { loadImage } from '../ui/dom.js';
import { PAWN_BY_KEY, TILE, applyLayout } from '../core/model.js';

const STEP_MS = 430;          // durata unui pas pe tablă
const WALK_MS = 240;          // un pas din mersul forțat (RECHEMAT URGENT), mai rapid
const TILT_SCALE = 0.93;      // înclinarea: tabla e puțin mai mică și ridicată
const TILT_LIFT = -0.022;     // (fracție din înălțime)
const BOARD_FILTER = 'saturate(1.6) contrast(1.06) brightness(1.06)';   // tabla: verde mai viu (ctx.filter; fără efect pe browserele vechi)
const PAWN_ASPECT = 0.54;   // lățimea pionului față de înălțime (forma desenată)
const DECK_LAYERS = 12;        // câte cartonașe are teancul LOTO / TELEX
const DECK_OFFSET = 0.24;      // fracția din latura mică a cadrului pe care o ocupă decalajul teancului
const TILE_SIZE_HALF = { x: 0.034, y: 0.045 };   // elipsa de atingere pentru o căsuță
const CLUSTER = {
  1: [[0, 0, 1]],
  2: [[-0.028, 0, 0.86], [0.028, 0, 0.86]],
  3: [[-0.025, 0.008, 0.8], [0.025, 0.008, 0.8], [0, -0.022, 0.8]],
  4: [[-0.024, 0.009, 0.76], [0.024, 0.009, 0.76], [-0.024, -0.019, 0.76], [0.024, -0.019, 0.76]],
};

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const mod = (a, n) => ((a % n) + n) % n;

/** Interpolare liniară într-un interval de timp; se oprește dacă [alive] devine false. */
function tween(ms, onFrame, alive = () => true) {
  return new Promise((resolve) => {
    const t0 = performance.now();
    const frame = (now) => {
      if (!alive()) return resolve();
      const t = Math.min(1, (now - t0) / ms);
      onFrame(t);
      if (t < 1) requestAnimationFrame(frame);
      else resolve();
    };
    requestAnimationFrame(frame);
  });
}

class PawnAnim {
  constructor(id, position) {
    this.id = id;
    this.v = position;        // poziția vizuală pe traseu (fracționară, poate trece peste N)
    this.target = position;   // poziția logică din starea jocului
    this.token = 0;           // identifică mutarea curentă (o mutare nouă o oprește pe cea veche)
    this.squash = 0;          // turtire la aterizare (0..1)
    this.moving = false;
    this.arrived = false;     // pionul a ajuns pe căsuța mutării curente (banii se arată de-abia atunci)
  }
}

export class BoardView {
  /**
   * @param {HTMLCanvasElement} canvas
   * @param {object} o
   * @param {object} o.catalog  din loadCatalog()
   * @param {(index:number)=>void} o.onTileTap
   * @param {(kind:string)=>void} o.onDeckTap
   * @param {(busy:boolean)=>void} o.onBusyChange   true cât un pion se mută
   * @param {()=>boolean} o.dieBusy                  true cât zarul se rostogolește
   * @param {(board:BoardView)=>void} [o.onFrame]    apelat la fiecare cadru (pentru zar)
   * @param {(src:string)=>void} [o.onAsset]
   */
  constructor(canvas, o) {
    this.canvas = canvas;
    this.ctx = canvas.getContext('2d');
    this.catalog = o.catalog;
    this.onTileTap = o.onTileTap;
    this.onDeckTap = o.onDeckTap;
    this.onBusyChange = o.onBusyChange ?? (() => {});
    this.dieBusy = o.dieBusy ?? (() => false);
    this.onFrame = o.onFrame ?? (() => {});
    this.onArrive = o.onArrive ?? (() => {});   // (playerId) pionul a ajuns pe căsuță

    this.model = {
      players: [], activePlayerId: null, landedTile: null, highlightColor: '#ffd54f',
      drawKind: null, propertyTiles: {}, stretch: true, tilt: true,
      zoomPreset: 1, zoomNonce: 0, focusIndex: null,
    };
    this.anims = new Map();
    this.zoom = { scale: 1, ox: 0, oy: 0 };
    this.lastZoomNonce = 0;
    this.layout = { bx: 0, by: 0, bw: 1, bh: 1, cssW: 1, cssH: 1, dpr: 1 };
    this.pointers = new Map();
    this.gesture = null;
    this.dragged = false;
    this.running = true;

    this.boardImg = null;
    this.loadBoardImage();
    this.deckImgs = {
      loto: null,
      telex: null,
    };
    loadImage('img/deck_back_loto.webp').then((img) => (this.deckImgs.loto = img));
    loadImage('img/deck_back_telex.webp').then((img) => (this.deckImgs.telex = img));
    this.bindPointers();
    this.resize();
    this.loop = this.loop.bind(this);
    requestAnimationFrame(this.loop);
    window.addEventListener('resize', () => this.resize());
  }

  destroy() {
    this.running = false;
  }

  /** Fotografia tablei din catalogul curent (veche sau nouă). */
  loadBoardImage() {
    const src = this.catalog.boardImage;
    loadImage(src).then((img) => {
      if (this.catalog.boardImage === src) this.boardImg = img;
    });
  }

  /** Schimbă tabla ('old' sau 'new'): pozițiile și fotografia. Pionii rămân pe căsuțele lor. */
  setLayout(name) {
    if (this.catalog.layout === name) return;
    applyLayout(this.catalog, name);
    this.boardImg = null;
    this.loadBoardImage();
    this.resize();   // raportul tablei s-a schimbat
  }

  /** Joc nou: pionii stau direct pe poziția de start, fără mers de la partida anterioară. */
  resetPawns() {
    this.anims.clear();
  }

  /** Model nou din starea jocului. Pionii care s-au mutat pornesc animația. */
  setModel(m) {
    Object.assign(this.model, m);
    const seen = new Set();
    for (const p of this.model.players) {
      seen.add(p.id);
      let a = this.anims.get(p.id);
      if (!a) {
        a = new PawnAnim(p.id, p.position);
        this.anims.set(p.id, a);
      }
      if (a.target !== p.position) {
        a.target = p.position;
        this.animateTo(a, p.position);
      }
    }
    for (const id of [...this.anims.keys()]) if (!seen.has(id)) this.anims.delete(id);

    if (this.model.zoomNonce !== this.lastZoomNonce) {
      this.lastZoomNonce = this.model.zoomNonce;
      this.presetZoom();
    }
  }

  /* ---------- geometrie ---------- */

  resize() {
    const dpr = Math.min(window.devicePixelRatio || 1, 2.5);
    const cssW = this.canvas.clientWidth || 1;
    const cssH = this.canvas.clientHeight || 1;
    if (this.canvas.width !== Math.round(cssW * dpr) || this.canvas.height !== Math.round(cssH * dpr)) {
      this.canvas.width = Math.round(cssW * dpr);
      this.canvas.height = Math.round(cssH * dpr);
    }
    let bw;
    let bh;
    const BOARD_ASPECT = this.catalog.boardAspect;
    if (this.model.stretch || cssW / cssH > BOARD_ASPECT) {
      // tabla umple lățimea (sau tot ecranul, când e întinsă)
      bw = this.model.stretch ? cssW : cssH * BOARD_ASPECT;
      bh = cssH;
    } else {
      bw = cssW;
      bh = cssW / BOARD_ASPECT;
    }
    const bx = (cssW - bw) / 2;
    const by = (cssH - bh) / 2;
    this.layout = { bx, by, bw, bh, cssW, cssH, dpr };
    this.clampZoom();
  }

  /** Transformarea (zoom ∘ înclinare) ca matrice afină: [A, E, F] cu scalare uniformă A. */
  transform() {
    const { bx, by, bw, bh } = this.layout;
    const S = this.model.tilt ? TILT_SCALE : 1;
    const lift = this.model.tilt ? TILT_LIFT * bh : 0;
    const cx = bx + bw / 2;
    const cy = by + bh / 2;
    const Z = this.zoom.scale;
    return {
      A: Z * S,
      E: Z * cx * (1 - S) + this.zoom.ox,
      F: Z * (cy * (1 - S) + lift) + this.zoom.oy,
      S,
      lift,
      cx,
      cy,
    };
  }

  /** Din fracții ale tablei în coordonate ecran (CSS px). */
  toScreen(fx, fy) {
    const { bx, by, bw, bh } = this.layout;
    const t = this.transform();
    const x = bx + fx * bw;
    const y = by + fy * bh;
    return { x: t.A * x + t.E, y: t.A * y + t.F };
  }

  /** Din coordonate ecran (CSS px) în fracții ale tablei. */
  fromScreen(px, py) {
    const { bx, by, bw, bh } = this.layout;
    const t = this.transform();
    const x = (px - t.E) / t.A;
    const y = (py - t.F) / t.A;
    return { fx: (x - bx) / bw, fy: (y - by) / bh };
  }

  /* ---------- zoom ---------- */

  clampZoom() {
    const { cssW, cssH } = this.layout;
    const z = this.zoom;
    z.scale = Math.min(4, Math.max(1, z.scale));
    z.ox = Math.min(0, Math.max(cssW * (1 - z.scale), z.ox));
    z.oy = Math.min(0, Math.max(cssH * (1 - z.scale), z.oy));
  }

  presetZoom() {
    const presetZoom = this.model.zoomPreset;
    const idx = this.model.focusIndex;
    const f = idx != null ? this.catalog.positions[idx] : null;
    const { cssW, cssH } = this.layout;
    if (presetZoom > 1 && f) {
      this.zoom = { scale: presetZoom, ox: cssW / 2 - f[0] * cssW * presetZoom, oy: cssH / 2 - f[1] * cssH * presetZoom };
    } else {
      this.zoom = { scale: 1, ox: 0, oy: 0 };
    }
    this.clampZoom();
  }

  /* ---------- atingeri ---------- */

  bindPointers() {
    const c = this.canvas;
    c.addEventListener('pointerdown', (e) => {
      c.setPointerCapture?.(e.pointerId);
      this.pointers.set(e.pointerId, { x: e.clientX, y: e.clientY, sx: e.clientX, sy: e.clientY, t: performance.now() });
      this.dragged = false;
      if (this.pointers.size === 2) this.startPinch();
    });
    c.addEventListener('pointermove', (e) => {
      const p = this.pointers.get(e.pointerId);
      if (!p) return;
      const dx = e.clientX - p.x;
      const dy = e.clientY - p.y;
      p.x = e.clientX;
      p.y = e.clientY;
      if (Math.hypot(e.clientX - p.sx, e.clientY - p.sy) > 8) this.dragged = true;
      if (this.pointers.size === 1 && this.zoom.scale > 1 && this.dragged) {
        this.zoom.ox += dx;
        this.zoom.oy += dy;
        this.clampZoom();
      } else if (this.pointers.size === 2 && this.gesture) {
        const [a, b] = [...this.pointers.values()];
        const dist = Math.hypot(a.x - b.x, a.y - b.y) || 1;
        const cx = (a.x + b.x) / 2;
        const cy = (a.y + b.y) / 2;
        const ratio = dist / this.gesture.dist;
        const oldScale = this.zoom.scale;
        const newScale = Math.min(4, Math.max(1, this.gesture.scale * ratio));
        const k = newScale / oldScale;
        this.zoom.ox = cx - (cx - this.zoom.ox) * k;
        this.zoom.oy = cy - (cy - this.zoom.oy) * k;
        this.zoom.scale = newScale;
        this.clampZoom();
        this.dragged = true;
      }
    });
    const end = (e) => {
      const p = this.pointers.get(e.pointerId);
      this.pointers.delete(e.pointerId);
      if (this.pointers.size < 2) this.gesture = null;
      if (!p || this.pointers.size > 0 || this.dragged) return;
      if (performance.now() - p.t > 700) return;
      this.hitTest(e.clientX - this.canvas.getBoundingClientRect().left, e.clientY - this.canvas.getBoundingClientRect().top);
    };
    c.addEventListener('pointerup', end);
    c.addEventListener('pointercancel', (e) => {
      this.pointers.delete(e.pointerId);
      this.gesture = null;
    });
    c.addEventListener('wheel', (e) => {
      e.preventDefault();
      const f = e.deltaY < 0 ? 1.12 : 1 / 1.12;
      const scale = Math.min(4, Math.max(1, this.zoom.scale * f));
      const k = scale / this.zoom.scale;
      const rect = c.getBoundingClientRect();
      const cx = e.clientX - rect.left;
      const cy = e.clientY - rect.top;
      this.zoom.ox = cx - (cx - this.zoom.ox) * k;
      this.zoom.oy = cy - (cy - this.zoom.oy) * k;
      this.zoom.scale = scale;
      this.clampZoom();
    }, { passive: false });
  }

  startPinch() {
    const [a, b] = [...this.pointers.values()];
    this.gesture = { dist: Math.hypot(a.x - b.x, a.y - b.y) || 1, scale: this.zoom.scale };
    this.dragged = true;
  }

  hitTest(px, py) {
    const { fx, fy } = this.fromScreen(px, py);
    // Pachetele au prioritate: un tap pe pachet cere tragerea
    for (const kind of ['loto', 'telex']) {
      const d = this.catalog.decks[kind];
      if (d && fx >= d.x && fx <= d.x + d.w && fy >= d.y && fy <= d.y + d.h) {
        if (this.model.drawKind === kind) this.onDeckTap(kind);
        return;
      }
    }
    let best = -1;
    let bestDist = 1;
    this.catalog.positions.forEach(([x, y], i) => {
      const dx = (x - fx) / TILE_SIZE_HALF.x;
      const dy = (y - fy) / TILE_SIZE_HALF.y;
      const d = dx * dx + dy * dy;
      if (d < bestDist) {
        bestDist = d;
        best = i;
      }
    });
    if (best >= 0) this.onTileTap(best);
  }

  /* ---------- animația pionilor ---------- */

  async animateTo(a, target) {
    const token = ++a.token;
    a.arrived = false;
    this.setMoving(a, true);
    try {
      await sleep(90);
      if (token !== a.token) return;
      const t0 = performance.now();
      while (this.dieBusy() && performance.now() - t0 < 5000) {
        await sleep(60);
        if (token !== a.token) return;
      }
      const N = this.catalog.positions.length;
      const start = Math.round(a.v);
      const forward = mod(target - start, N);
      const backward = mod(start - target, N);
      if (target < 0) {
        // RECHEMAT URGENT (START_POSITION): pionul merge pas cu pas, pe drumul zarului, până la
        // căsuța RECHEMAT (adversarii o văd), apoi e teleportat la marcajul START. Fără bonus.
        if (start !== target) {
          const rechemat = this.catalog.board.findIndex((t) => t.type === TILE.RECHEMAT);
          const toRechemat = mod(rechemat - start, N);
          for (let i = 1; i <= toRechemat; i++) {
            if (!(await this.step(a, start + i, token))) return;
          }
          a.v = target;   // teleportat la START, după ce a ajuns pe RECHEMAT
        }
      } else if (forward >= 1 && forward <= 12) {
        for (let i = 1; i <= forward; i++) {
          if (!(await this.step(a, start + i, token))) return;
        }
      } else if (backward >= 1 && backward <= 6) {
        for (let i = 1; i <= backward; i++) {
          if (!(await this.step(a, start - i, token))) return;
        }
      } else {
        a.v = target;   // salt de rezervă, dacă distanța nu e plauzibilă pentru un zar
      }
      if (token !== a.token) return;
      // „pop" la aterizare: pionul se turtește o clipă și revine
      await tween(70, (t) => (a.squash = t), () => token === a.token);
      await tween(230, (t) => (a.squash = 1 - t), () => token === a.token);
      if (token !== a.token) return;
      a.squash = 0;
      a.v = target;
      a.arrived = true;
      this.onArrive(a.id);   // banii și cartonașul apar acum, nu înainte de sosire
      await sleep(180);   // o pauză pe căsuța de sosire, înainte de ofertă / plată / cartonaș
    } finally {
      if (token === a.token) this.setMoving(a, false);
    }
  }

  async step(a, to, token, ms = STEP_MS) {
    const from = a.v;
    if (token !== a.token) return false;
    this.onStep?.();
    await tween(ms, (t) => (a.v = from + (to - from) * t), () => token === a.token);
    return token === a.token;
  }

  setMoving(a, flag) {
    if (a.moving === flag) return;
    a.moving = flag;
    const any = [...this.anims.values()].some((x) => x.moving);
    if (this.lastBusy !== any) {
      this.lastBusy = any;
      this.onBusyChange(any);
    }
  }

  /* ---------- desenare ---------- */

  loop() {
    if (!this.running) return;
    this.draw();
    this.onFrame(this);
    requestAnimationFrame(this.loop);
  }

  draw() {
    const { ctx, layout } = this;
    const { dpr, cssW, cssH } = layout;
    if (this.canvas.clientWidth && (this.canvas.clientWidth !== cssW || this.canvas.clientHeight !== cssH)) this.resize();
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.fillStyle = '#173a22';
    ctx.fillRect(0, 0, cssW, cssH);

    const t = this.transform();
    ctx.setTransform(dpr * t.A, 0, 0, dpr * t.A, dpr * t.E, dpr * t.F);
    const { bx, by, bw, bh } = layout;

    if (this.boardImg) {
      // pânza tablei: culori mai vii, verdele mai natural (fără să se schimbe desenul)
      ctx.save();
      ctx.filter = BOARD_FILTER;
      ctx.drawImage(this.boardImg, bx, by, bw, bh);
      ctx.restore();
    } else {
      ctx.fillStyle = '#2d4d32';
      ctx.fillRect(bx, by, bw, bh);
    }

    this.drawDecks(ctx, bx, by, bw, bh);
    this.drawHighlight(ctx, bx, by, bw, bh);
    this.drawMarkers(ctx, bx, by, bw, bh);
    this.drawPawns(ctx, bx, by, bw, bh);
  }

  drawDecks(ctx, bx, by, bw, bh) {
    const now = performance.now();
    for (const kind of ['loto', 'telex']) {
      const r = this.catalog.decks[kind];
      const img = this.deckImgs[kind];
      if (!r || !img) continue;
      const active = this.model.drawKind === kind;
      const pulse = active ? 1 + 0.07 * (0.5 - 0.5 * Math.cos((2 * Math.PI * (now % 1200)) / 1200)) : 1;
      const W = bw * r.w;
      const H = bh * r.h;
      const X = bx + bw * r.x;
      const Y = by + bh * r.y;
      ctx.save();
      ctx.translate(X + W / 2, Y + H / 2);
      ctx.scale(pulse, pulse);
      ctx.translate(-W / 2, -H / 2);
      // teanc 3D: cartonașul de sus acoperă exact cadrul negru (fără colțuri rămase);
      // cele de dedesubt ies în sus-dreapta, cu marginile lor vizibile
      const off = Math.min(W, H) * DECK_OFFSET;   // cât ies cartonașele de dedesubt
      const step = off / (DECK_LAYERS - 1);
      const cardAt = (i) => {
        const k = DECK_LAYERS - 1 - i;             // 0 pentru cartonașul de sus
        return [k * step, -k * step];
      };
      const poly = (pts, fill) => {
        ctx.beginPath();
        ctx.moveTo(pts[0][0], pts[0][1]);
        for (const q of pts.slice(1)) ctx.lineTo(q[0], q[1]);
        ctx.closePath();
        ctx.fillStyle = fill;
        ctx.fill();
      };
      // umbra teancului pe masă și marginile vizibile (sus și dreapta)
      ctx.save();
      ctx.shadowColor = 'rgba(0,0,0,0.55)';
      ctx.shadowBlur = bh * 0.06;
      ctx.shadowOffsetX = bh * 0.03;
      ctx.shadowOffsetY = bh * 0.04;
      poly([[0, 0], [off, -off], [off + W, -off], [W, 0]], '#bdb292');        // latura de sus
      poly([[W, 0], [off + W, -off], [off + W, H - off], [W, H]], '#e2d9bf');  // latura dreaptă
      ctx.restore();
      // cartonașele, din spate spre față
      for (let i = 0; i < DECK_LAYERS; i++) {
        const [ox, oy] = cardAt(i);
        const top = i === DECK_LAYERS - 1;
        ctx.save();
        ctx.shadowColor = 'rgba(0,0,0,0.45)';
        ctx.shadowBlur = bh * 0.008;
        ctx.shadowOffsetX = bh * 0.004;
        ctx.shadowOffsetY = bh * 0.006;
        ctx.drawImage(img, ox, oy, W, H);
        ctx.restore();
        ctx.lineWidth = top && active ? Math.max(2, bh * 0.004) : Math.max(1, bh * 0.0012);
        ctx.strokeStyle = top && active ? '#f0d20e' : 'rgba(40,30,20,0.6)';
        ctx.strokeRect(ox, oy, W, H);
      }
      // lumină pe cartonașul de sus
      const gl = ctx.createLinearGradient(0, 0, W, H);
      gl.addColorStop(0, 'rgba(255,255,255,0.28)');
      gl.addColorStop(0.45, 'rgba(255,255,255,0)');
      gl.addColorStop(1, 'rgba(0,0,0,0.18)');
      ctx.fillStyle = gl;
      ctx.fillRect(0, 0, W, H);
      if (active) {
        const label = 'TRAGE';
        const fs = Math.max(12, bh * 0.034);
        ctx.font = `900 ${fs}px system-ui, sans-serif`;
        const tw = ctx.measureText(label).width + fs;
        const th = fs * 1.6;
        ctx.fillStyle = 'rgba(0,0,0,0.65)';
        roundRect(ctx, W / 2 - tw / 2, H / 2 - th / 2, tw, th, fs * 0.4);
        ctx.fill();
        ctx.fillStyle = '#fff';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText(label, W / 2, H / 2 + 1);
      }
      ctx.restore();
    }
  }

  drawHighlight(ctx, bx, by, bw, bh) {
    const idx = this.model.landedTile;
    if (idx == null) return;
    const pos = this.catalog.positions[idx];
    if (!pos) return;
    const tt = (performance.now() % 1100) / 1100;
    const cx = bx + bw * pos[0];
    const cy = by + bh * pos[1];
    const W = bw * 0.11;
    const H = bh * 0.14;
    const r = Math.min(W, H) / 2 * (0.85 + 0.15 * Math.sin(tt * 2 * Math.PI));
    const color = this.model.highlightColor;
    const g = ctx.createRadialGradient(cx, cy, 0, cx, cy, r);
    g.addColorStop(0, withAlpha(color, 0.38 * (1 - tt * 0.6)));
    g.addColorStop(0.7, withAlpha(color, 0.18 * (1 - tt * 0.6)));
    g.addColorStop(1, withAlpha(color, 0));
    ctx.fillStyle = g;
    ctx.beginPath();
    ctx.arc(cx, cy, r, 0, Math.PI * 2);
    ctx.fill();
    ctx.lineWidth = Math.max(2, r * 0.09);
    ctx.strokeStyle = withAlpha(color, 0.85 * (1 - tt * 0.5));
    ctx.beginPath();
    ctx.arc(cx, cy, r * 0.82, 0, Math.PI * 2);
    ctx.stroke();
  }

  /** Marcajele proprietăților: o căsuță deținută, în culoarea proprietarului. Fără nivel (anexele nu sunt publice). */
  drawMarkers(ctx, bx, by, bw, bh) {
    for (const p of this.model.players) {
      const color = PAWN_BY_KEY[p.color]?.hex ?? '#888';
      for (const propId of Object.keys(p.ownedProperties)) {
        const tile = this.model.propertyTiles[propId];
        const pos = tile != null ? this.catalog.positions[tile] : null;
        if (!pos) continue;
        const w = bw * 0.06;
        const h = bh * 0.075;
        ctx.beginPath();
        ctx.ellipse(bx + bw * pos[0], by + bh * pos[1], w / 2, h / 2, 0, 0, Math.PI * 2);
        ctx.fillStyle = color;
        ctx.fill();
        ctx.lineWidth = 2;
        ctx.strokeStyle = color;
        ctx.stroke();
      }
    }
  }

  drawPawns(ctx, bx, by, bw, bh) {
    const players = this.model.players;
    const N = this.catalog.positions.length;
    const byTile = new Map();
    for (const p of players) {
      const arr = byTile.get(p.position) ?? [];
      arr.push(p);
      byTile.set(p.position, arr);
    }
    const phase = (performance.now() / 2600) * 2 * Math.PI;
    for (const p of players) {
      const a = this.anims.get(p.id);
      if (!a) continue;
      const mates = byTile.get(p.position) ?? [p];
      const idx = Math.max(0, mates.findIndex((m) => m.id === p.id));
      const slots = CLUSTER[Math.min(4, mates.length)] ?? CLUSTER[1];
      const [dx, dy, sizeScale] = slots[Math.min(idx, slots.length - 1)];

      // poziția pe traseu, cu interpolare între căsuțe
      const v = a.v;
      let fx0;
      let fy0;
      if (v < 0) {
        const s = this.catalog.startMarker;
        const t = Math.min(1, Math.max(0, v + 1));
        fx0 = s[0] + (this.catalog.positions[0][0] - s[0]) * t;
        fy0 = s[1] + (this.catalog.positions[0][1] - s[1]) * t;
      } else {
        const base = Math.floor(v);
        const frac = v - base;
        const i0 = mod(base, N);
        const i1 = (i0 + 1) % N;
        const q0 = this.catalog.positions[i0];
        const q1 = this.catalog.positions[i1];
        fx0 = q0[0] + (q1[0] - q0[0]) * frac;
        fy0 = q0[1] + (q1[1] - q0[1]) * frac;
      }
      const hop = Math.min(1, Math.max(0, Math.sin(Math.PI * (v - Math.floor(v)))));
      const anchorX = bx + bw * (fx0 + dx);
      const anchorY = by + bh * (fy0 + dy + 0.008);

      const tileH = bh * 0.045 * sizeScale;
      const pawnH = tileH * 2.36;
      const pawnW = pawnH * PAWN_ASPECT;
      const shadowH = bh * 0.013;
      const bob = p.id === this.model.activePlayerId ? Math.sin(phase) * 0.0035 : 0;   // fracție din înălțime

      // umbra rămâne pe tablă, se micșorează când pionul e sus
      ctx.save();
      ctx.translate(anchorX, anchorY);
      const sh = 1 - 0.2 * hop;
      ctx.globalAlpha = Math.max(0.2, 1 - 0.35 * hop);
      const g = ctx.createRadialGradient(0, 0, 0, 0, 0, pawnW * 0.62);
      g.addColorStop(0, 'rgba(0,0,0,0.55)');
      g.addColorStop(1, 'rgba(0,0,0,0)');
      ctx.fillStyle = g;
      ctx.save();
      ctx.scale(sh, 1 - 0.25 * hop);
      ctx.beginPath();
      ctx.ellipse(0, 0, pawnW * 0.62, shadowH * 0.9, 0, 0, Math.PI * 2);
      ctx.fill();
      ctx.restore();
      ctx.globalAlpha = 1;

      // pionul: zboară (alungire) și aterizează (turtire)
      const lift = -bh * (0.03 * hop + bob);
      const sy = (1 + 0.06 * hop) * (1 - 0.1 * a.squash);
      const sx = (1 - 0.03 * hop) * (1 + 0.07 * a.squash);
      ctx.translate(0, lift);
      ctx.scale(sx, sy);
      drawPawnShape(ctx, pawnW, pawnH, PAWN_BY_KEY[p.color]?.hex ?? '#888', p.name);
      ctx.restore();
    }
  }
}

function withAlpha(hex, a) {
  const n = parseInt(hex.slice(1), 16);
  const r = (n >> 16) & 255;
  const g = (n >> 8) & 255;
  const b = n & 255;
  return `rgba(${r},${g},${b},${Math.max(0, Math.min(1, a))})`;
}

function roundRect(ctx, x, y, w, h, r) {
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.arcTo(x + w, y, x + w, y + h, r);
  ctx.arcTo(x + w, y + h, x, y + h, r);
  ctx.arcTo(x, y + h, x, y, r);
  ctx.arcTo(x, y, x + w, y, r);
  ctx.closePath();
}

/** Pionul desenat simetric, cu baza în (0,0) și vârful la -H. Nu depinde de fotografii, deci nu se strâmbă. */
function drawPawnShape(ctx, W, H, hex, name = '') {
  const dark = shade(hex, -0.35);
  const light = shade(hex, 0.3);
  ctx.beginPath();
  ctx.moveTo(-0.5 * W, 0);
  ctx.lineTo(0.5 * W, 0);
  ctx.bezierCurveTo(0.5 * W, -0.3 * H, 0.16 * W, -0.36 * H, 0.16 * W, -0.6 * H);
  ctx.lineTo(-0.16 * W, -0.6 * H);
  ctx.bezierCurveTo(-0.16 * W, -0.36 * H, -0.5 * W, -0.3 * H, -0.5 * W, 0);
  ctx.closePath();
  const grad = ctx.createLinearGradient(-0.5 * W, 0, 0.5 * W, 0);
  grad.addColorStop(0, light);
  grad.addColorStop(0.55, hex);
  grad.addColorStop(1, dark);
  ctx.fillStyle = grad;
  ctx.strokeStyle = dark;
  ctx.lineWidth = Math.max(1, H * 0.018);
  ctx.lineJoin = 'round';
  ctx.fill();
  ctx.stroke();

  // numele jucătorului, scris pe corpul pionului (se micșorează ca să încapă)
  if (name) {
    let fs = H * 0.13;
    const font = (size) => { ctx.font = `800 ${size}px "Segoe UI", system-ui, sans-serif`; };
    font(fs);
    while (fs > H * 0.04 && ctx.measureText(name).width > W * 0.62) {
      fs -= H * 0.005;
      font(fs);
    }
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.lineJoin = 'round';
    ctx.lineWidth = Math.max(1, fs * 0.25);
    ctx.strokeStyle = 'rgba(0,0,0,0.6)';
    ctx.strokeText(name, 0, -0.3 * H);
    ctx.fillStyle = '#fff';
    ctx.fillText(name, 0, -0.3 * H);
  }

  ctx.beginPath();
  ctx.arc(0, -0.78 * H, 0.27 * W, 0, Math.PI * 2);
  ctx.fillStyle = grad;
  ctx.fill();
  ctx.stroke();

  // reflexie pe cap, în stânga-sus
  ctx.fillStyle = 'rgba(255,255,255,0.35)';
  ctx.beginPath();
  ctx.ellipse(-0.09 * W, -0.86 * H, 0.09 * W, 0.05 * H, -0.5, 0, Math.PI * 2);
  ctx.fill();
}

/** Culoarea amestecată cu alb (amount > 0) sau cu negru (amount < 0). */
function shade(hex, amount) {
  const n = parseInt(hex.slice(1), 16);
  const mix = (c) => (amount >= 0 ? c + (255 - c) * amount : c * (1 + amount));
  const r = Math.round(mix((n >> 16) & 255));
  const g = Math.round(mix((n >> 8) & 255));
  const b = Math.round(mix(n & 255));
  return `rgb(${r},${g},${b})`;
}
