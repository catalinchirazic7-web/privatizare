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

/* ---------- Robot ---------- */

/** Cumpără titlul liber dacă soldul acoperă prețul. */
export function botDecideTitle(buyer, property) {
  return buyer.money >= property.titlePrice;
}

/** Cumpără următoarea anexă dacă soldul acoperă prețul ei. */
export function botDecideAnnex(buyer, annex) {
  return buyer.money >= annex.price;
}

/**
 * Vânzările necesare pentru o datorie: întâi anexele (de la cea mai înaltă), apoi titlurile,
 * și doar până acoperă datoria. Gol dacă soldul acoperă deja.
 */
export function botLiquidationSales(debtor, debt, actions) {
  let money = debtor.money;
  if (money >= debt) return [];
  const sales = [];
  const titlesToSell = [];

  for (const [propId, level] of Object.entries(debtor.ownedProperties)) {
    const prop = actions[propId];
    if (!prop) continue;
    let lvl = level;
    while (lvl > 0) {
      if (money >= debt) break;
      const annex = prop.annexes.find((a) => a.index === lvl);
      sales.push({ propertyId: propId, annexIndex: lvl });
      money += sellBackValue(annex.price);
      lvl -= 1;
    }
    if (lvl === 0) titlesToSell.push(propId);
  }

  for (const propId of titlesToSell) {
    if (money >= debt) break;
    const prop = actions[propId];
    sales.push({ propertyId: propId, annexIndex: null });
    money += sellBackValue(prop.titlePrice);
  }
  return sales;
}
