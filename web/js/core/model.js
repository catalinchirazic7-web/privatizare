// Date de bază: culorile pionilor, tipurile de căsuțe și încărcarea catalogului din JSON.
// Port din data/model/*.kt și GameRepository.kt.

/** Cele 8 culori. `img` e sprite-ul pionului din img/. Cheia coincide cu numele din Kotlin (persistă în multiplayer). */
export const PAWN_COLORS = Object.freeze([
  { key: 'ROSU', name: 'Roșu', hex: '#D32F2F', img: 'img/pawn_rosu.webp', dark: false },
  { key: 'ALBASTRU', name: 'Albastru', hex: '#1976D2', img: 'img/pawn_albastru.webp', dark: false },
  { key: 'GALBEN', name: 'Galben', hex: '#FBC02D', img: 'img/pawn_galben.webp', dark: false },
  { key: 'VERDE', name: 'Verde', hex: '#388E3C', img: 'img/pawn_verde.webp', dark: false },
  { key: 'MOV', name: 'Mov', hex: '#7B1FA2', img: 'img/pawn_mov.webp', dark: false },
  { key: 'PORTOCALIU', name: 'Portocaliu', hex: '#F57C00', img: 'img/pawn_portocaliu.webp', dark: false },
  { key: 'NEGRU', name: 'Negru', hex: '#263238', img: 'img/pawn_negru.webp', dark: false },
  { key: 'ALB_ARGINTIU', name: 'Alb/Argintiu', hex: '#ECEFF1', img: 'img/pawn_alb.webp', dark: true },
]);

export const PAWN_BY_KEY = Object.freeze(Object.fromEntries(PAWN_COLORS.map((c) => [c.key, c])));

export const TILE = Object.freeze({
  START: 'START',
  PROPERTY: 'PROPERTY',
  LOTO: 'LOTO',
  TELEX: 'TELEX',
  BANK: 'BANK',
  BANK_DIVIDEND: 'BANK_DIVIDEND',
  CASA: 'CASA',
  SALA: 'SALA',
  RECHEMAT: 'RECHEMAT',
});

/**
 * Încarcă cele trei fișiere JSON și le transformă în structuri folosite de motor.
 * @returns {Promise<{board, positions, decks, startMarker, actions, loto, telex, cardArt}>}
 */
/** Cele două table: aceleași căsuțe și reguli, altă fotografie și alte poziții. */
export const BOARD_LAYOUTS = Object.freeze({
  old: { image: 'img/tabla_privatizare.webp', aspect: 1600 / 1131 },
  new: { image: 'img/tabla_noua.webp', aspect: 1600 / 1073 },
});

export async function loadCatalog(baseUrl = './data/') {
  const [boardJson, boardNewJson, actionsJson, cardsJson] = await Promise.all([
    fetchJson(`${baseUrl}board.json`),
    fetchJson(`${baseUrl}board_new.json`),
    fetchJson(`${baseUrl}actions.json`),
    fetchJson(`${baseUrl}cards.json`),
  ]);
  return buildCatalog(boardJson, actionsJson, cardsJson, boardNewJson);
}

/** Transformă JSON-urile (deja citite) în catalogul folosit de motor. Funcție pură. */
export function buildCatalog(boardJson, actionsJson, cardsJson, boardNewJson = null) {
  const loto = parseLoto(cardsJson.loto);
  const telex = parseTelex(cardsJson.telex);
  const catalog = {
    board: parseBoard(boardJson.spaces),
    actions: parseActions(actionsJson.properties),
    loto,
    telex,
    cardArt: cardArtMap(loto, telex),
    layouts: {
      old: layoutOf(boardJson, 'old'),
      new: boardNewJson ? layoutOf(boardNewJson, 'new') : layoutOf(boardJson, 'old'),
    },
  };
  applyLayout(catalog, 'old');
  return catalog;
}

function layoutOf(json, name) {
  return {
    positions: parsePositions(json.spaces),
    decks: parseDecks(json.decks ?? {}),
    startMarker: json._meta?.start ? [json._meta.start.x, json._meta.start.y] : [0.125, 0.908],
    ...BOARD_LAYOUTS[name],
  };
}

/** Pune în catalog pozițiile și fotografia unei table ('old' sau 'new'). */
export function applyLayout(catalog, name) {
  const L = catalog.layouts[name] ? name : 'old';
  const layout = catalog.layouts[L];
  catalog.layout = L;
  catalog.positions = layout.positions;
  catalog.decks = layout.decks;
  catalog.startMarker = layout.startMarker;
  catalog.boardImage = layout.image;
  catalog.boardAspect = layout.aspect;
  return catalog;
}

async function fetchJson(url) {
  const res = await fetch(url, { cache: 'no-cache' });
  if (!res.ok) throw new Error(`Nu pot încărca ${url} (${res.status})`);
  return res.json();
}

export function parseBoard(spaces) {
  return [...spaces]
    .sort((a, b) => a.index - b.index)
    .map((o) => {
      const base = { index: o.index, name: o.name };
      switch (o.type) {
        case 'START':
          return { ...base, type: TILE.START };
        case 'PROPRIETATE':
          return { ...base, type: TILE.PROPERTY, propertyId: o.propertyId };
        case 'LOTO':
          return { ...base, type: TILE.LOTO };
        case 'TELEX':
          return { ...base, type: TILE.TELEX };
        case 'BANCA':
          return { ...base, type: TILE.BANK };
        case 'BANCA_DIVIDEND':
          return { ...base, type: TILE.BANK_DIVIDEND };
        case 'COLT':
          if (o.name.startsWith('SALA')) return { ...base, type: TILE.SALA };
          if (o.name.startsWith('CASA')) return { ...base, type: TILE.CASA };
          if (o.name.startsWith('RECHEMAT')) return { ...base, type: TILE.RECHEMAT };
          throw new Error(`Colț necunoscut: ${o.name}`);
        default:
          throw new Error(`Tip de căsuță necunoscut: ${o.type}`);
      }
    });
}

export function parsePositions(spaces) {
  return [...spaces].sort((a, b) => a.index - b.index).map((o) => [o.x, o.y]);
}

export function parseDecks(decks) {
  const out = {};
  for (const [k, d] of Object.entries(decks)) out[k] = { x: d.x, y: d.y, w: d.w, h: d.h };
  return out;
}

export function parseActions(properties) {
  const out = {};
  for (const o of properties) {
    out[o.id] = {
      id: o.id,
      name: o.name,
      titlePrice: o.titlePrice,
      titleTax: o.titleTax,
      annexes: o.anexe.map((a, j) => ({ index: j + 1, price: a.price, profit: a.profit })),
    };
  }
  return out;
}

export function parseLoto(arr) {
  return arr.map((o) => {
    const kind = { W: 'W', L: 'L', N: 'N' }[o.kind];
    if (!kind) throw new Error('Tip LOTO necunoscut');
    return { id: o.id, kind, amount: o.amount ?? 0, text: o.text };
  });
}

export function parseTelex(arr) {
  return arr.map((o) => ({
    id: o.id,
    text: o.text,
    effects: o.effects.map((e) => ({ ...e })),
    beneficiaryType: o.beneficiaryType || null,
    targetCompanyId: o.targetCompanyId || null,
    handwritten: o.handwritten === true,
  }));
}

/** Id-ul cartonașului -> fotografia lui (cheia e id-ul; textul nu e unic). */
export function cardArtMap(loto, telex) {
  const out = {};
  for (const c of loto) out[c.id] = `carti/${c.id}.webp`;
  for (const c of telex) out[c.id] = `carti/${c.id}.webp`;
  return out;
}

/** Fotografiile unei firme: titlul (page 0) sau anexa n. */
export function actionArtPath(propertyId, annex = 0) {
  return annex <= 0 ? `carti/act_${propertyId}_t.webp` : `carti/act_${propertyId}_a${annex}.webp`;
}

export function propertyIconPath(propertyId) {
  return `carti/icon_${propertyId}.webp`;
}
