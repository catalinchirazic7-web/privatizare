// Constante de reguli și distribuția sumelor de start.
// Port din GameRules.kt — valorile urmează regulamentul.

export const GameRules = Object.freeze({
  /** Poziția de plecare (săgeata START). Primul pas duce la CONTINENTAL (index 0). */
  START_POSITION: -1,
  /** Bonus la fiecare trecere prin START. */
  START_BONUS: 3000,
  /** BANCA: dobânda plătită jucătorului. */
  BANK_INTEREST: 5000,
  /** BANCA ACHITĂ DIVIDENDE. */
  DIVIDEND: 2000,
  /** SALA CONSFĂTUIRILOR: taxă plătită la Bancă. */
  SALA_CONSFATUIRI_FEE: 1000,
  /** CASA DE ODIHNĂ: ture pierdute. */
  CASA_ODIHNA_SKIP_TURNS: 2,
  /** Banca răscumpără acțiunile la această proporție (%). */
  RESALE_RATIO_PERCENT: 80,
});

/** Tirajul fizic al bancnotelor: denominare -> număr de bucăți. Total 500 000 ECU. */
export const BILL_SUPPLY = Object.freeze({ 50: 80, 100: 160, 500: 160, 1000: 240, 2000: 80 });

export const STARTING_MONEY_STEP = 50_000;
export const TOTAL_SUPPLY = 500_000;

/** Cea mai mare sumă de start aleasă (pași de 50k) care încape în tiraj pentru n jucători. */
export function maxCustomStartingMoney(playerCount) {
  return Math.floor(TOTAL_SUPPLY / playerCount / STARTING_MONEY_STEP) * STARTING_MONEY_STEP;
}

function sumBills(bills) {
  return Object.entries(bills).reduce((acc, [denom, n]) => acc + Number(denom) * n, 0);
}

/** Împărțire egală: fiecare denominare se împarte întreg; restul rămâne la Bancă. */
export function distributeStartingMoney(playerCount, moneyPerPlayer = null) {
  if (!(playerCount >= 2 && playerCount <= 4)) throw new Error(`Între 2 și 4 jucători, nu ${playerCount}`);
  if (moneyPerPlayer === null) {
    const billsPerPlayer = {};
    const bankBills = {};
    for (const [denom, total] of Object.entries(BILL_SUPPLY)) {
      billsPerPlayer[denom] = Math.floor(total / playerCount);
      bankBills[denom] = total % playerCount;
    }
    return {
      playerCount,
      billsPerPlayer,
      playerMoney: sumBills(billsPerPlayer),
      bankBills,
      bankMoney: sumBills(bankBills),
    };
  }
  if (!(moneyPerPlayer > 0 && moneyPerPlayer % STARTING_MONEY_STEP === 0)) {
    throw new Error(`Sumă invalidă: ${moneyPerPlayer}`);
  }
  if (moneyPerPlayer > maxCustomStartingMoney(playerCount)) {
    throw new Error(`Suma ${moneyPerPlayer} depășește tirajul pentru ${playerCount} jucători`);
  }

  // Bancnotele se dau pe rând, câte una pe jucător, de la cea mai mare care mai încape.
  const bank = { ...BILL_SUPPLY };
  const owned = Array.from({ length: playerCount }, () => ({}));
  const remaining = Array(playerCount).fill(moneyPerPlayer);
  let progress = true;
  while (remaining.some((r) => r > 0) && progress) {
    progress = false;
    for (let i = 0; i < playerCount; i++) {
      if (remaining[i] === 0) continue;
      const bill = Object.keys(bank)
        .map(Number)
        .filter((d) => bank[d] > 0 && d <= remaining[i])
        .sort((a, b) => b - a)[0];
      if (bill === undefined) continue;
      bank[bill] -= 1;
      owned[i][bill] = (owned[i][bill] ?? 0) + 1;
      remaining[i] -= bill;
      progress = true;
    }
  }
  if (!remaining.every((r) => r === 0)) {
    throw new Error(`Tirajul nu acoperă ${moneyPerPlayer} ECU pentru fiecare jucător`);
  }
  const rest = {};
  for (const [d, n] of Object.entries(bank)) if (n > 0) rest[d] = n;
  return {
    playerCount,
    billsPerPlayer: owned[0],
    playerMoney: moneyPerPlayer,
    bankBills: rest,
    bankMoney: sumBills(rest),
  };
}

/** Bancnotele unei sume, pentru afișarea grămezilor (port din billCounts). */
export const BILL_DENOMINATIONS = [2000, 1000, 500, 100, 50];
const MAX_LAYERS = 6;

export function billCounts(money) {
  const total = Math.max(0, money);
  const counts = {};
  let rest = total;
  const share = Math.floor(total / BILL_DENOMINATIONS.length);
  for (const d of BILL_DENOMINATIONS) {
    const n = Math.min(Math.floor(share / d), Math.floor(rest / d));
    if (n > 0) {
      counts[d] = n;
      rest -= n * d;
    }
  }
  for (const d of BILL_DENOMINATIONS) {
    const n = Math.floor(rest / d);
    if (n > 0) {
      counts[d] = (counts[d] ?? 0) + n;
      rest -= n * d;
    }
  }
  return counts;
}

export { MAX_LAYERS };
