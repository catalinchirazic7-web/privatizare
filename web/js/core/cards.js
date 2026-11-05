// Pachetele LOTO și TELEX și rezultatul afișat pentru fiecare cartonaș.
// Port din CardDeck.kt și CardOutcome.kt.

/** Amestec Fisher-Yates pe o copie. */
export function shuffled(list, rng = Math.random) {
  const a = list.slice();
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(rng() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

/**
 * Pachet cu extragere FIFO din teancul amestecat. Cărțile trase merg în descărcare;
 * când teancul se golește, descărcarea se reamestecă și devine teanc nou.
 */
export class CardDeck {
  constructor(cards, rng = Math.random) {
    if (!cards.length) throw new Error('Pachet gol');
    this.rng = rng;
    this.pile = shuffled(cards, rng);
    this.discard = [];
  }

  get remaining() {
    return this.pile.length;
  }

  draw() {
    if (this.pile.length === 0) {
      this.pile = shuffled(this.discard, this.rng);
      this.discard = [];
    }
    const card = this.pile.shift();
    this.discard.push(card);
    return card;
  }

  /** Scoate din teanc cartonașul cu valoarea cea mai mare pentru [valueOf] (la egalitate: primul). */
  drawBest(valueOf) {
    if (this.pile.length === 0) {
      this.pile = shuffled(this.discard, this.rng);
      this.discard = [];
    }
    let bi = 0;
    for (let i = 1; i < this.pile.length; i += 1) {
      if (valueOf(this.pile[i]) > valueOf(this.pile[bi])) bi = i;
    }
    const [card] = this.pile.splice(bi, 1);
    this.discard.push(card);
    return card;
  }
}

/** Valoarea pentru jucător a unui cartonaș LOTO: câștigul e plus, pierderea minus. */
export function lotoValue(card) {
  const r = lotoResult(card);
  if (r.outcome === CardOutcome.GAIN) return r.amount ?? 0;
  if (r.outcome === CardOutcome.LOSS) return -(r.amount ?? 0);
  return 0;
}

/** Valoarea pentru jucător a unui cartonaș TELEX, după efectele lui. */
export function telexValue(card) {
  const r = telexResult(card.effects);
  if (r.outcome === CardOutcome.GAIN) return r.amount ?? 0;
  if (r.outcome === CardOutcome.LOSS) return -(r.amount ?? 0);
  return 0;
}

export const CardOutcome = Object.freeze({ GAIN: 'GAIN', LOSS: 'LOSS', NEUTRAL: 'NEUTRAL' });

/** LOTO: câștig (banca plătește), pierdere (jucătorul plătește) sau nimic. */
export function lotoResult(card) {
  if (card.kind === 'W') return { outcome: CardOutcome.GAIN, amount: card.amount };
  if (card.kind === 'L') return { outcome: CardOutcome.LOSS, amount: card.amount };
  return { outcome: CardOutcome.NEUTRAL, amount: null };
}

/**
 * TELEX: orice pierdere (plată, tură pierdută, mers înapoi) face cartonașul „pierdere”,
 * altfel orice câștig face „câștig”. Suma e totalul banilor, pe sensul rezultatului.
 */
export function telexResult(effects) {
  let gain = 0;
  let loss = 0;
  let anyGain = false;
  let anyLoss = false;
  for (const e of effects) {
    switch (e.type) {
      case 'pay_bank':
      case 'pay_owner_or_bank':
        loss += e.amount;
        anyLoss = true;
        break;
      case 'receive_bank':
      case 'receive_from_each_player':
        gain += e.amount;
        anyGain = true;
        break;
      case 'move':
        if (e.steps > 0) anyGain = true;
        else anyLoss = true;
        break;
      case 'skip_turns':
        anyLoss = true;
        break;
      default:
        break;
    }
  }
  if (anyLoss) return { outcome: CardOutcome.LOSS, amount: loss > 0 ? loss : null };
  if (anyGain) return { outcome: CardOutcome.GAIN, amount: gain > 0 ? gain : null };
  return { outcome: CardOutcome.NEUTRAL, amount: null };
}
