// Un singur zar, uniform între 1 și 6. Sursa unică de zaruri (om, gazdă online, robot).

export function rollDie(rng = Math.random) {
  return 1 + Math.floor(rng() * 6);
}
