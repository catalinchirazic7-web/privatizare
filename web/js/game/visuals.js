// Identitatea vizuală a căsuțelor: iconița (emoji, cu fotografia ca prim plan), culoarea de brand și textele scurte.
// Port din TileVisuals.kt.

const C = {
  NAVY: '#0D47A1', CRIMSON: '#C62828', MAGENTA: '#AD1457', PURPLE: '#6A1B9A', VIOLET: '#7B1FA2',
  DEEP_PURPLE: '#4527A0', INDIGO: '#283593', SKY: '#0277BD', CYAN: '#00838F', TEAL: '#00695C',
  MINT: '#00897B', GREEN: '#2E7D32', FOREST: '#1B5E20', OLIVE: '#33691E', AMBER: '#F9A825',
  ORANGE: '#EF6C00', RUST: '#D84315', BROWN: '#5D4037', TAUPE: '#8D6E63', STEEL: '#455A64',
  SLATE: '#37474F', BLUE_GREY: '#546E7A', GOLD: '#8D6E00',
};

/** Firma: iconița emoji, culoarea de brand și numele scurt (fără S.C./S.A./S.R.L.). */
const PROPERTY = {
  continental: ['🏨', C.NAVY, 'CONTINENTAL'],
  ambasador: ['🛎', C.CRIMSON, 'AMBASADOR'],
  maria: ['🍽', C.MAGENTA, 'MARIA'],
  lipscani: ['🏬', C.PURPLE, 'LIPSCANI'],
  piata_romana: ['⛲', C.GREEN, 'PIAȚA ROMÂNĂ'],
  piata_victoriei: ['🎭', C.ORANGE, 'PIAȚA VICTORIEI'],
  unirii: ['🏛', C.GOLD, 'UNIRII'],
  taberei: ['🏕', C.OLIVE, 'TABEREI'],
  lahovary: ['🏢', C.SLATE, 'LAHOVARY'],
  poli: ['⚽', C.MINT, 'POLI'],
  timisoreana: ['🍺', C.AMBER, 'TIMIȘOREANA'],
  prichindel: ['🧸', C.MAGENTA, 'PRICHINDEL'],
  padurea_verde: ['🌲', C.FOREST, 'PĂDUREA VERDE'],
  televiziune: ['📺', C.DEEP_PURPLE, 'TELEVIZIUNE'],
  helicon: ['🎵', C.VIOLET, 'HELICON'],
  memorii: ['💾', C.CYAN, 'MEMORII'],
  celuloza: ['📄', C.BROWN, 'CELULOZA'],
  metalo_tim: ['⚙', C.STEEL, 'METALO-TIM'],
  ohm: ['🔌', C.INDIGO, 'OHM'],
  dura: ['🔧', C.BLUE_GREY, 'DURA'],
  guban: ['🔨', C.TAUPE, 'GUBAN'],
  electromotor: ['⚡', C.AMBER, 'ELECTROMOTOR'],
  telefoane: ['☎', C.TEAL, 'TELEFOANE'],
  circumvalatiunii: ['🛣', C.INDIGO, 'CIRCUMVALAȚIUNII'],
  calea_sagului: ['🚉', C.RUST, 'CALEA ȘAGULUI'],
  atelier_cfr: ['🚂', C.CRIMSON, 'ATELIERE CFR'],
  aviatica: ['✈', C.SKY, 'AVIATICA'],
};

/** Căsuțele speciale. */
const SPECIAL = {
  START: ['🏁', C.GOLD, 'START'],
  LOTO: ['🎰', C.MAGENTA, 'LOTO'],
  TELEX: ['📠', C.SKY, 'TELEX'],
  BANK: ['🏦', C.TEAL, 'BANCA'],
  BANK_DIVIDEND: ['💰', C.GOLD, 'DIVIDENDE'],
  CASA: ['🛋', C.MINT, 'CASA DE ODIHNĂ'],
  SALA: ['👔', C.SLATE, 'SALA CONSFAȚUIRI'],
  RECHEMAT: ['↩', C.RUST, 'RECHEMAT URGENT'],
};

const HINT = {
  START: 'Treci pe aici și încasezi banii de la Bancă.',
  LOTO: 'Trage un cartonaș LOTO: câștig sau pierdere, după noroc.',
  TELEX: 'Trage un cartonaș TELEX: mutări, plăți și beneficii.',
  BANK: 'Aici se fac plățile și încasările către Bancă.',
  BANK_DIVIDEND: 'Banca îți achită dividendele cuvenite.',
  CASA: 'Stai două ture: pionul se odihnește. Ieși în ordinea în care ai ajuns.',
  SALA: 'Ședință la consiliu: urmează instrucțiunile.',
  RECHEMAT: 'Te întorci urgent la START.',
};

export function shortPropertyName(name) {
  let s = name.trim();
  for (const p of ['S.C.', 'S.A.', 'S.R.L.', 'SC ', 'SA ', 'SRL ']) {
    if (s.toUpperCase().startsWith(p)) s = s.slice(p.length).trim();
  }
  return s.replace(/[. ]+$/, '') || name;
}

export function propertyVisual(propertyId, fallbackName = propertyId) {
  const v = PROPERTY[propertyId];
  if (v) return { icon: v[0], accent: v[1], shortName: v[2] };
  return { icon: '🏢', accent: C.SLATE, shortName: shortPropertyName(fallbackName) };
}

/** Vizualul unei căsuțe din board (proprietate sau specială). */
export function tileVisual(tile, actions) {
  if (tile.type === 'PROPERTY') {
    const name = actions[tile.propertyId]?.name ?? tile.propertyId;
    return propertyVisual(tile.propertyId, name);
  }
  const v = SPECIAL[tile.type] ?? ['🏢', C.SLATE, tile.name];
  return { icon: v[0], accent: v[1], shortName: v[2] };
}

export function tileHint(tile) {
  return tile.type === 'PROPERTY' ? null : HINT[tile.type] ?? null;
}
