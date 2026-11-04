// Calcule pentru acțiuni, scor și deciziile robotului.
// Port din ActionCalculator.kt, Scoring.kt și BotEngine.kt.

import { GameRules } from './rules.js';

/** Chiria pentru un nivel: titlul plus profiturile anexelor cu index <= nivel. */
export function calculateRent(property, ownedLevel) {
  if (!(ownedLevel >= 0 && ownedLevel <= property.annexes.length)) throw new Error(`Nivel invalid: ${ownedLevel}`);
  const profits = property.annexes.filter((a) => a.index <= ownedLevel).reduce((s, a) => s + a.profit, 0);
  return property.titleTax + profits;
}

/** Următoarea anexă de cumpărat, sau null. */
export function nextAnnex(property, ownedLevel) {
  return property.annexes.find((a) => a.index === ownedLevel + 1) ?? null;
}

/** Suma primită de la Bancă la returnarea unei acțiuni (80%). */
export function sellBackValue(initialPrice) {
  return Math.floor((initialPrice * GameRules.RESALE_RATIO_PERCENT) / 100);
}

/** Valoarea întregii acțiuni cu [level] anexe, toate la 80%. */
export function wholeSaleValue(property, level) {
  return (
    sellBackValue(property.titlePrice) +
    property.annexes.filter((a) => a.index <= level).reduce((s, a) => s + sellBackValue(a.price), 0)
  );
}

/** Valoarea acțiunilor unui jucător, la 80%. */
export function holdingsValue(player, actions) {
  let sum = 0;
  for (const [propId, level] of Object.entries(player.ownedProperties)) {
    const prop = actions[propId];
    if (prop) sum += wholeSaleValue(prop, level);
  }
  return sum;
}

export function netWorth(player, actions) {
  return player.money + holdingsValue(player, actions);
}

/** Jucătorii sortați descrescător după scor. */
export function rankPlayers(players, actions) {
  return [...players].sort((a, b) => netWorth(b, actions) - netWorth(a, actions));
}
