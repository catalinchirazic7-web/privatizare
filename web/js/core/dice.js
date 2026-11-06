// Un singur zar, uniform între 1 și 6. Sursa unică de zaruri (om, gazdă online, robot).

/** Șansa ca robotul CatalinRobot să arunce de două ori și să ia fața mai mare. */
export const CATALIN_LUCK = 0.25;

/** Șansa ca robotul CatalinRobot să-și aleagă zarul (și cartonașul), în loc să lase la întâmplare. */
export const CATALIN_CONTROL = 0.6;

/** O aruncare e mică (se rearuncă în modul ușor) când media pe zar e cel mult 2. */
export function isLowRoll(dice) {
  return dice.reduce((a, b) => a + b, 0) <= 2 * dice.length;
}

export function rollDie(rng = Math.random) {
  return 1 + Math.floor(rng() * 6);
}

/**
 * Zarul robotului. Cu probabilitatea [luck] aruncă de două ori și păstrează fața mai mare;
 * altfel o aruncare normală. Cu luck = 0 e identic cu rollDie.
 */
export function rollBotDie(rng = Math.random, luck = 0) {
  const first = rollDie(rng);
  if (luck <= 0 || rng() >= luck) return first;
  return Math.max(first, rollDie(rng));
}
