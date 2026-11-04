// Sunete sintetizate (Web Audio), fără fișiere: zar, pas, monede, cartonaș, alertă.
// AudioContext se pornește după prima atingere (cerința browserelor, inclusiv iOS).

const KEY = 'privatizare.sunet';

let ctx = null;
let enabled = readEnabled();

function readEnabled() {
  try {
    return localStorage.getItem(KEY) !== 'off';
  } catch {
    return true;
  }
}

function context() {
  if (!enabled) return null;
  if (!ctx) {
    const AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return null;
    ctx = new AC();
  }
  // „suspended” (iOS, fundal) sau „interrupted” (apel, altă aplicație): reluăm ori de câte ori nu rulează
  if (ctx.state !== 'running') ctx.resume().catch(() => {});
  return ctx;
}

export const sound = {
  get enabled() {
    return enabled;
  },
  toggle() {
    enabled = !enabled;
    try {
      localStorage.setItem(KEY, enabled ? 'on' : 'off');
    } catch {
      /* fără persistență */
    }
    return enabled;
  },
  /** Apelat la prima atingere: deblochează audio pe iOS/Safari. */
  unlock() {
    context();
  },
  dice() {
    noise(0.28, 0.25, 2400, 'bandpass');
  },
  step() {
    tone(620, 0.05, 'sine', 0.06);
  },
  land() {
    tone(160, 0.14, 'sine', 0.22, 90);
  },
  coin() {
    tone(880, 0.09, 'triangle', 0.12);
    setTimeout(() => tone(1320, 0.12, 'triangle', 0.1), 70);
  },
  card() {
    noise(0.35, 0.12, 900, 'lowpass');
  },
  buy() {
    tone(523, 0.08, 'square', 0.07);
    setTimeout(() => tone(784, 0.08, 'square', 0.07), 90);
    setTimeout(() => tone(1046, 0.16, 'square', 0.07), 180);
  },
  alert() {
    tone(180, 0.22, 'sawtooth', 0.09, 140);
  },
};

function tone(freq, dur, type, vol, endFreq = null) {
  const c = context();
  if (!c) return;
  const o = c.createOscillator();
  const g = c.createGain();
  o.type = type;
  o.frequency.setValueAtTime(freq, c.currentTime);
  if (endFreq) o.frequency.exponentialRampToValueAtTime(endFreq, c.currentTime + dur);
  g.gain.setValueAtTime(vol, c.currentTime);
  g.gain.exponentialRampToValueAtTime(0.0008, c.currentTime + dur);
  o.connect(g).connect(c.destination);
  o.start();
  o.stop(c.currentTime + dur + 0.02);
}

function noise(dur, vol, cutoff, filterType) {
  const c = context();
  if (!c) return;
  const len = Math.floor(c.sampleRate * dur);
  const buf = c.createBuffer(1, len, c.sampleRate);
  const data = buf.getChannelData(0);
  for (let i = 0; i < len; i++) data[i] = (Math.random() * 2 - 1) * (1 - i / len);
  const src = c.createBufferSource();
  src.buffer = buf;
  const f = c.createBiquadFilter();
  f.type = filterType;
  f.frequency.value = cutoff;
  const g = c.createGain();
  g.gain.value = vol;
  src.connect(f).connect(g).connect(c.destination);
  src.start();
}
