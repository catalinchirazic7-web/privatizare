// Ajutoare pentru DOM și imagini (fără framework).

/** Creează un element: el('button', { class: 'btn', onclick: fn, text: '…' }, child…). */
export function el(tag, props = {}, ...children) {
  const node = document.createElement(tag);
  for (const [k, v] of Object.entries(props ?? {})) {
    if (v === undefined || v === null || v === false) continue;
    if (k === 'class') node.className = v;
    else if (k === 'text') node.textContent = v;
    else if (k === 'html') node.innerHTML = v;
    else if (k.startsWith('on')) node.addEventListener(k.slice(2), v);
    else if (k === 'style' && typeof v === 'object') Object.assign(node.style, v);
    else if (v === true) node.setAttribute(k, '');
    else node.setAttribute(k, String(v));
  }
  for (const c of children.flat()) {
    if (c === null || c === undefined || c === false) continue;
    node.append(c instanceof Node ? c : document.createTextNode(String(c)));
  }
  return node;
}

export function clear(node) {
  while (node.firstChild) node.removeChild(node.firstChild);
  return node;
}

const loaded = new Map();   // src -> Image, după încărcare
const pending = new Map();  // src -> Promise<Image|null>

/** Încarcă o imagine o singură dată. Rezolvă cu Image sau null dacă lipsește. */
export function loadImage(src) {
  if (loaded.has(src)) return Promise.resolve(loaded.get(src));
  if (!pending.has(src)) {
    pending.set(src, new Promise((resolve) => {
      const img = new Image();
      img.decoding = 'async';
      img.onload = () => {
        loaded.set(src, img);
        resolve(img);
      };
      img.onerror = () => resolve(null);
      img.src = src;
    }));
  }
  return pending.get(src);
}

/** Imaginea deja încărcată (sincron), sau null. */
export function cachedImage(src) {
  return loaded.get(src) ?? null;
}

/** Formatează sume: 12 345 ECU. */
export function fmt(n) {
  return Math.round(n).toLocaleString('ro-RO').replace(/\s/g, ' ');
}

export function ecu(n) {
  return `${fmt(n)} ECU`;
}
