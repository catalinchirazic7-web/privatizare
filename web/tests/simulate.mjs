// Simulare headless: patru roboți joacă partide întregi. Un CatalinRobot contra trei roboți clasici,
// cu locul lui rotit între partide ca să nu conteze poziția. Rulare: node tests/simulate.mjs [partide]
import { Game } from '../js/game/game.js';
import { PAWN_COLORS } from '../js/core/model.js';
import { rollBotDie, CATALIN_LUCK } from '../js/core/dice.js';
import { loadRealCatalog, seededRng } from './helpers.mjs';

const GAMES = Number(process.argv[2] ?? 300);
const MAX_ACTIONS = 200000;  // plasă de siguranță: o partidă care nu se termină e raportată
const TURN_CAP = 150;        // limita de timp, în ture
// „classic” = control: toate locurile joacă robotul clasic, fără CatalinRobot
const CATALIN = process.argv[3] !== 'classic';
const cat = loadRealCatalog();

function playOne(gameIdx) {
  const rng = seededRng(1000 + gameIdx);
  const catSeat = gameIdx % 4;
  const setup = [0, 1, 2, 3].map((i) => ({
    name: i === catSeat ? 'Catalin' : `Clasic${i}`,
    colorKey: PAWN_COLORS[i].key,
    isBot: true,
    catalin: CATALIN && i === catSeat,
  }));
  const game = new Game(cat, {
    rng,
    botDie: (luck = 0) => rollBotDie(rng, luck),
  });
  // startGame cere cel puțin un om; îl pornim cu un om și apoi toate locurile devin roboți
  game.startGame([{ ...setup[0], isBot: false, catalin: false }, ...setup.slice(1)], null, null, 1, 'old');
  for (const [i, p] of game.game.players.entries()) {
    p.isBot = true;
    p.catalin = CATALIN && i === catSeat;
  }
  let actions = 0;
  let endedBy = 'faliment';
  while (game.winnerName === null && actions < MAX_ACTIONS) {
    // limita de timp a jocului, în ture: la final câștigă cel cu valoarea netă cea mai mare (timeUp)
    if (game.game.turn > TURN_CAP) { game.timeUp(); endedBy = 'timp'; break; }
    if (!game.botNextAction()) break;
    actions += 1;
  }
  return {
    catSeat,
    finished: game.winnerName !== null,
    endedBy,
    winner: game.winnerName,
    catalinWon: game.winnerName === setup[catSeat].name,
    actions,
  };
}

const results = Array.from({ length: GAMES }, (_, i) => playOne(i));
const finished = results.filter((r) => r.finished);
const catWins = finished.filter((r) => r.catalinWon).length;
const avgActions = Math.round(results.reduce((s, r) => s + r.actions, 0) / results.length);

const byTime = results.filter((r) => r.endedBy === 'timp').length;
console.log(`partide: ${GAMES}, terminate: ${finished.length}, blocate: ${GAMES - finished.length}, prin timp: ${byTime}`);
console.log(`CatalinRobot câștigă ${catWins}/${finished.length} = ${(100 * catWins / Math.max(1, finished.length)).toFixed(1)}% (egalitate: 25%)`);
console.log(`acțiuni medii pe partidă: ${avgActions}, luck: ${CATALIN_LUCK}`);
