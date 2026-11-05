// Service worker minimal: păstrează jocul utilizabil offline după prima încărcare.
// - fișierele aplicației se iau mereu din rețea (cu revalidare); din cache doar când nu e rețea;
// - pagina principală se cere mereu din rețea când există (ca să primești versiunile noi);
// - videourile și Firebase nu se cache-uiesc (sunt mari sau sunt live).
// La o schimbare de cod, mărește VERSION.

const VERSION = 'privatizare-web-v1.04';

const SHELL = [
  './',
  './index.html',
  './manifest.json',
  './css/style.css',
  './js/app.js',
  './js/config.js',
  './js/audio.js',
  './js/storage.js',
  './js/core/actions.js',
  './js/core/cards.js',
  './js/core/dice.js',
  './js/core/model.js',
  './js/core/rules.js',
  './js/core/turnEngine.js',
  './js/game/board.js',
  './js/game/game.js',
  './js/game/gameScreen.js',
  './js/game/visuals.js',
  './js/online/controller.js',
  './js/online/events.js',
  './js/online/protocol.js',
  './js/ui/dom.js',
  './js/ui/menus.js',
  './data/board.json',
  './data/actions.json',
  './data/cards.json',
  './img/logo_privatizare.webp',
  './img/tabla_privatizare.webp',
  './img/deck_back_loto.webp',
  './img/deck_back_telex.webp',
  './img/regulament.jpg',
  './img/donate_qr.png',
  './icons/icon-192.png',
  './icons/icon-512.png',
  './icons/icon-180.png',
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(VERSION)
      .then((cache) => cache.addAll(SHELL))
      .then(() => self.skipWaiting()),
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== VERSION).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener('fetch', (event) => {
  const req = event.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  if (url.origin !== self.location.origin) return;          // Firebase și CDN-urile: direct din rețea
  if (url.pathname.includes('/media/')) return;             // videourile: direct din rețea

  if (req.mode === 'navigate') {
    event.respondWith(
      fetch(req)
        .then((res) => {
          const copy = res.clone();
          caches.open(VERSION).then((c) => c.put('./index.html', copy));
          return res;
        })
        .catch(() => caches.match('./index.html')),
    );
    return;
  }

  // rețea întâi (cu revalidare, ca să nu rămână fișiere vechi în cache-ul HTTP); cache doar offline
  event.respondWith(
    fetch(req, { cache: 'no-cache' })
      .then((res) => {
        if (res.ok && res.type === 'basic') {
          const copy = res.clone();
          caches.open(VERSION).then((c) => c.put(req, copy));
        }
        return res;
      })
      .catch(() => caches.match(req)),
  );
});
