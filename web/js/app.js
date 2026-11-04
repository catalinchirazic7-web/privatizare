// Punctul de intrare: încarcă catalogul, leagă ecranele și porneste partidele (locale sau online).
// Port din AppRoot (GameScreen.kt) și MainActivity.kt.

import { loadCatalog } from './core/model.js';
import { Game } from './game/game.js';
import { GameScreen } from './game/gameScreen.js';
import { rollDie } from './core/dice.js';
import { storage } from './storage.js';
import { sound } from './audio.js';
import * as menus from './ui/menus.js';
import { OnlineController } from './online/controller.js';

const $ = (id) => document.getElementById(id);
const SCREENS = ['intro', 'home', 'setup', 'online', 'lobby', 'rules', 'game', 'outro'];
const DECISION_DIALOGS = ['BUY', 'BANK_FEE', 'RENT', 'DEBT', 'LIQUIDATION_HANDOVER', 'HANDOVER'];

const app = {
  catalog: null,
  game: null,
  gameScreen: null,
  online: null,
  screen: null,
  setupView: null,
  onlineView: null,
  wakeLock: null,

  /* ---------- ecrane ---------- */

  show(name) {
    if (this.screen === 'intro' && name !== 'intro') $('intro-video').pause();
    if (this.screen === 'outro' && name !== 'outro') $('outro-video').pause();
    for (const s of SCREENS) $(`scr-${s}`).classList.toggle('active', s === name);
    this.screen = name;
    this.updateRotateHint();
    if (name === 'home') menus.refreshHome();
    if (name === 'setup') this.setupView.render();
    if (name === 'online') this.onlineView.enter();
    if (name === 'lobby') menus.renderLobby(this);
    if (name === 'intro') this.playVideo('intro-video', () => this.show('home'));
    if (name === 'outro') this.playVideo('outro-video', () => this.show('home'));
  },

  playVideo(id, onEnd) {
    const v = $(id);
    v.currentTime = 0;
    v.preload = 'auto';
    v.onended = onEnd;
    const p = v.play();
    if (p && p.catch) {
      p.catch(() => {
        // redarea cu sunet poate fi blocată de browser: pornim mut
        v.muted = true;
        v.play().catch(() => onEnd());
      });
    }
  },

  /* ---------- meniuri ---------- */

  /** @param {1|2} dice  modul de joc ales din meniu: un zar sau două zaruri */
  openSetup(dice = 1) {
    this.requestLandscape();
    this.setupView.dice = dice;
    this.show('setup');
  },

  async openOnline() {
    if (!this.online) {
      try {
        const { FirebaseRoomBackend } = await import('./online/firebaseBackend.js');
        this.online = new OnlineController(FirebaseRoomBackend.connect(), this.game);
        this.online.onChange(() => this.onOnlineChange());
      } catch (e) {
        this.toast(`Serviciul online nu a putut porni: ${e.message}`);
        return;
      }
    }
    this.requestLandscape();
    this.show('online');
  },

  onOnlineChange() {
    const c = this.online;
    if (c.inGame) {
      if (this.screen !== 'game') this.enterGame(true);
      return;
    }
    if (c.isInRoom) {
      if (this.screen === 'online') this.show('lobby');
      else if (this.screen === 'lobby') menus.renderLobby(this);
      return;
    }
    if (this.screen === 'lobby') this.show('home');
    else if (this.screen === 'online') this.onlineView.render();
  },

  /* ---------- partide ---------- */

  startLocal(setup, money, time, diceCount = 1, boardLayout = 'old') {
    sound.unlock();
    this.requestLandscape();
    this.gameScreen.setLayout(boardLayout);
    this.game.startGame(setup, money, time, diceCount, boardLayout);
    this.enterGame(false);
  },

  continueGame() {
    const saved = storage.loadGame();
    if (!saved) return;
    this.requestLandscape();
    this.game.restore(saved.snapshot, saved.leftMs);
    this.enterGame(false);
  },

  enterGame(online) {
    this.show('game');
    this.gameScreen.start({ online: online ? this.online : null });
    try {
      history.pushState({ game: true }, '');
    } catch {
      /* istoricul nu e disponibil */
    }
  },

  /** Ieșirea în meniu. fromHistory = apelat de butonul Înapoi (istoricul e deja refăcut). */
  exitGame(fromHistory = false) {
    if (this.screen !== 'game') return;
    this.gameScreen.stop();
    if (!fromHistory && history.state?.game) history.back();
    this.show('home');
    if (this.online && (this.online.isInRoom || this.online.inGame)) this.online.leave();
    this.releaseWakeLock();
  },

  /* ---------- ecran complet și orientare ---------- */

  /** Pe Android Chrome: ecran complet și orizontal. iOS nu permite; acolo se afișează indicația de rotire. */
  requestLandscape() {
    try {
      if (!document.fullscreenElement && document.documentElement.requestFullscreen) {
        document.documentElement.requestFullscreen({ navigationUI: 'hide' }).catch(() => {});
      }
    } catch {
      /* fără ecran complet */
    }
    try {
      screen.orientation?.lock?.('landscape').catch(() => {});
    } catch {
      /* blocarea orientării nu e permisă */
    }
    try {
      navigator.wakeLock?.request?.('screen').then((l) => (this.wakeLock = l)).catch(() => {});
    } catch {
      /* fără wake lock */
    }
  },

  releaseWakeLock() {
    this.wakeLock?.release?.().catch?.(() => {});
    this.wakeLock = null;
  },

  updateRotateHint() {
    const portraitNarrow = window.matchMedia('(orientation: portrait) and (max-width: 900px)').matches;
    const videoScreen = this.screen === 'intro' || this.screen === 'outro';
    $('rotate-hint').hidden = !portraitNarrow || videoScreen;
  },

  toast(text) {
    const t = $('toast');
    t.textContent = text;
    t.hidden = false;
    clearTimeout(this.toastTimer);
    this.toastTimer = setTimeout(() => (t.hidden = true), 3200);
  },
}

/* ---------- pornire ---------- */

async function boot() {
  window.addEventListener('error', (e) => app.toast(`Eroare: ${e.message}`));

  app.catalog = await loadCatalog('./data/');
  app.game = new Game(app.catalog, { botDie: () => rollDie() });
  app.gameScreen = new GameScreen({
    root: $('scr-game'),
    catalog: app.catalog,
    game: app.game,
    onExit: () => app.exitGame(),
  });

  app.setupView = new menus.SetupView(app);
  app.onlineView = new menus.OnlineMenuView(app);
  menus.initHome(app);
  menus.initRules();
  menus.bindLobby(app);
  menus.refreshHome();

  $('intro-skip').addEventListener('click', () => app.show('home'));
  $('outro-done').addEventListener('click', () => app.show('home'));

  // butoanele „← Înapoi” (configurare, JOC ONLINE, regulament) merg la ecranul indicat în data-back
  document.querySelectorAll('[data-back]').forEach((btn) => {
    btn.addEventListener('click', () => app.show(btn.dataset.back));
  });

  // Butonul Înapoi al telefonului: în joc ne întoarce în meniu (ca în aplicația Android)
  window.addEventListener('popstate', () => {
    if (app.screen === 'game') {
      if (app.gameScreen.needsDecision()) {
        history.pushState({ game: true }, '');
        app.toast('Răspunde mai întâi la fereastra din joc.');
        return;
      }
      app.exitGame(true);
    } else if (app.screen && app.screen !== 'home' && app.screen !== 'intro') {
      app.show('home');
    }
  });

  for (const mq of [window.matchMedia('(orientation: portrait)'), window.matchMedia('(max-width: 900px)')]) {
    mq.addEventListener?.('change', () => app.updateRotateHint());
  }
  window.addEventListener('resize', () => app.updateRotateHint());

  // fiecare atingere reia sunetul, dacă browserul l-a oprit între timp
  document.addEventListener('pointerdown', () => sound.unlock());

  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('service-worker.js').catch((e) => console.warn('Service worker indisponibil', e));
  }

  app.show('intro');
}

boot().catch((e) => {
  console.error(e);
  $('scr-home').classList.add('active');
  app.screen = 'home';
  app.toast(`Jocul nu a putut porni: ${e.message}`);
});
