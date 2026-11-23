// Punctul de intrare: încarcă catalogul, leagă ecranele și porneste partidele (locale sau online).
// Port din AppRoot (GameScreen.kt) și MainActivity.kt.

import { loadCatalog } from './core/model.js';
import { Game } from './game/game.js';
import { GameScreen } from './game/gameScreen.js';
import { rollBotDie } from './core/dice.js';
import { storage } from './storage.js';
import { sound } from './audio.js';
import * as menus from './ui/menus.js';
import { OnlineController } from './online/controller.js';

const $ = (id) => document.getElementById(id);
const SCREENS = ['intro', 'home', 'setup', 'online', 'lobby', 'rules', 'game', 'outro'];
const DECISION_DIALOGS = ['BUY', 'BANK_FEE', 'RENT', 'DEBT', 'LIQUIDATION_HANDOVER', 'HANDOVER'];

const coverClips = ['media/coperta.mp4', 'media/coperta_lunga.mp4'];
let coverIndex = 0;
function nextCover() {
  const cover = $('home-cover');
  coverIndex = (coverIndex + 1) % coverClips.length;
  const title = document.querySelector('#scr-home .cover-title');
  if (title) title.hidden = coverIndex !== 0;
  cover.src = coverClips[coverIndex];
  cover.load();
  if (app.screen === 'home') cover.play().catch(() => {});
}
$('home-cover').addEventListener('ended', nextCover);


const app = {
  catalog: null,
  game: null,
  gameScreen: null,
  online: null,
  onlineControllers: new Map(),
  screen: null,
  setupView: null,
  onlineView: null,
  wakeLock: null,

  /* ---------- ecrane ---------- */

  show(name) {
    if (this.screen === 'home' && name !== 'home') $('home-cover').pause();
    if (this.screen === 'intro' && name !== 'intro') $('intro-video').pause();
    if (this.screen === 'outro' && name !== 'outro') $('outro-video').pause();
    for (const s of SCREENS) $(`scr-${s}`).classList.toggle('active', s === name);
    this.screen = name;
    this.updateRotateHint();
    if (name === 'home') {
      $('toggle-cover')?.remove(); // elimină butonul din paginile mai vechi aflate în cache
      menus.refreshHome();
      const cover = $('home-cover');
      cover.muted = true;
      if (!window.matchMedia('(prefers-reduced-motion: reduce)').matches) cover.play().catch(() => {});
    }
    if (name === 'setup') this.setupView.render();
    if (name === 'online') this.onlineView.enter();
    if (name === 'lobby') menus.renderLobby(this);
    // intro-ul cu sunet pornește doar după o atingere (browserul blochează sunetul altfel)
    if (name === 'intro') this.playVideo('intro-video', () => { if (this.screen === 'intro') this.show('home'); });
    if (name === 'outro') {
      $('outro-message').hidden = true;
      $('outro-done').hidden = false;
      $('outro-video').onerror = () => this.finishExit();
      this.playVideo('outro-video', () => this.finishExit());
    }
  },

  finishExit() {
    if (this.screen !== 'outro') return;
    const video = $('outro-video');
    video.onended = null;
    video.onerror = null;
    video.pause();
    $('outro-done').hidden = true;
    $('outro-message').hidden = false;
    window.close();
  },

  playVideo(id, onEnd) {
    const v = $(id);
    v.currentTime = 0;
    v.muted = localStorage.getItem('privatizare.videoSound') === 'off';
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
  openSetup(dice = 1, rapid = false, marea = false) {
    this.requestLandscape();
    this.setupView.dice = dice;
    this.setupView.rapidMode = rapid;
    this.setupView.marea = marea;
    this.show('setup');
  },

  async openOnline(marea = false) {
    const layout = marea ? 'marea' : 'old';
    if (this.online?.boardLayout !== layout && (this.online?.busy || this.online?.isInRoom || this.online?.leaving)) {
      this.toast('Așteaptă închiderea conexiunii la masa anterioară.');
      return;
    }
    try {
      const { FirebaseRoomBackend } = await import('./online/firebaseBackend.js');
      if (!this.onlineControllers.has(layout)) {
        const ctl = new OnlineController(FirebaseRoomBackend.connect(), this.game, { boardLayout: layout });
        ctl.onChange(() => { if (this.online === ctl) this.onOnlineChange(); });
        this.onlineControllers.set(layout, ctl);
      }
      this.online = this.onlineControllers.get(layout);
    } catch (e) {
      this.toast(`Serviciul online nu a putut porni: ${e.message}`);
      return;
    }
    $('online-title').textContent = marea ? 'MAREA PRIVATIZARE · JOC ONLINE' : 'JOC ONLINE';
    this.requestLandscape();
    this.show('online');
    this.online.peekRoom($('online-code').value);
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
    else if (this.screen === 'home' && c.error) this.toast(c.error);
  },

  /* ---------- partide ---------- */

  startLocal(setup, money, time, diceCount = 1, boardLayout = 'old', rapidMode = false) {
    sound.unlock();
    this.requestLandscape();
    this.game.startGame(setup, money, time, diceCount, boardLayout, rapidMode);
    this.gameScreen.setLayout(this.game.boardLayout);
    this.gameScreen.board.resetPawns();   // joc nou: pionii pornesc instant de la start
    this.enterGame(false, true);
  },

  continueGame() {
    const saved = storage.loadGame();
    if (!saved) return;
    this.requestLandscape();
    this.game.restore(saved.snapshot, saved.leftMs);
    this.enterGame(false);
  },

  enterGame(online, opening = false) {
    this.show('game');
    const gameScreen = $('scr-game');
    const skip = $('skip-opening');
    gameScreen.classList.remove('opening-game');
    skip.hidden = true;
    if (opening && localStorage.getItem('privatizare.effects') !== 'off' && !window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      void gameScreen.offsetWidth;
      gameScreen.classList.add('opening-game');
      this.gameScreen.board.openingAt = performance.now();
      skip.hidden = false;
      const finish = () => { gameScreen.classList.remove('opening-game'); this.gameScreen.board.openingAt = null; skip.hidden = true; };
      skip.onclick = finish;
      setTimeout(finish, 2200);
    }
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
  app.game = new Game(app.catalog, { botDie: (luck = 0) => rollBotDie(Math.random, luck) });
  app.gameScreen = new GameScreen({
    root: $('scr-game'),
    catalog: app.game.catalog,
    game: app.game,
    onExit: () => app.exitGame(),
  });

  app.setupView = new menus.SetupView(app);
  app.onlineView = new menus.OnlineMenuView(app);
  menus.initHome(app);
  menus.initRules();
  menus.bindLobby(app);
  menus.refreshHome();

  // butonul rotund de sunet (stânga-sus): roșu = oprit, verde = pornit
  const soundFab = $('sound-fab');
  const syncSoundFab = () => {
    soundFab.classList.toggle('on', sound.enabled);
    soundFab.textContent = sound.enabled ? '🔊' : '🔇';
    soundFab.setAttribute('aria-label', sound.enabled ? 'Sunet pornit' : 'Sunet oprit');
  };
  soundFab.addEventListener('click', () => {
    sound.unlock();
    sound.toggle();
    syncSoundFab();
  });
  window.addEventListener('sound-changed', syncSoundFab);
  syncSoundFab();

  $('intro-skip').addEventListener('click', () => app.show('home'));
  $('intro-video').addEventListener('error', () => { if (app.screen === 'intro') app.show('home'); });
  $('outro-done').addEventListener('click', () => app.finishExit());

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
  // la întoarcerea în aplicație (alt tab, telefon deblocat) reluăm sunetul, fără să mai așteptăm o atingere
  document.addEventListener('visibilitychange', () => {
    if (!document.hidden) sound.unlock();
  });

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
