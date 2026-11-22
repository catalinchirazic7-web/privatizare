import { test } from 'node:test';
import assert from 'node:assert/strict';
import { Game } from '../js/game/game.js';
import { SnapshotCodec } from '../js/online/protocol.js';
import { loadRealCatalog } from './helpers.mjs';
const cat = loadRealCatalog();
function setup() {
  const g = new Game(cat);
  g.startGame([{name:'Ana',colorKey:'ROSU'}, {name:'Bot',colorKey:'ALBASTRU',isBot:true}], null, null, 1, 'old', true);
  return g;
}
test('mod rapid cumpără anexele rămase doar după cumpărarea titlului', () => {
  const g = setup(), p = cat.actions.poli, player = g.game.players[0];
  g.offer = {playerId:player.id,propertyId:p.id,option:{type:'TITLE',price:p.titlePrice}};
  assert.equal(g.buyAllAnnexes(), false);
  g.acceptOffer();
  assert.equal(g.game.players[0].ownedProperties[p.id],0);
  g.offer = {playerId:player.id,propertyId:p.id,option:{type:'ANNEX',annex:p.annexes[0]}};
  const prior = g.game.players[0].money;
  assert.equal(g.buyAllAnnexes(), true);
  assert.equal(g.game.players[0].ownedProperties[p.id],p.annexes.length);
  assert.equal(g.game.players[0].money, prior - p.annexes.reduce((sum,a)=>sum+a.price,0));
});
test('mod rapid nu cumpără parțial dacă fondurile sunt insuficiente', () => {
  const g=setup(), p=cat.actions.poli;
  g.game.players[0].ownedProperties[p.id]=0;
  g.game.players[0].money=p.annexes[0].price;
  g.offer={playerId:'p0',propertyId:p.id,option:{type:'ANNEX',annex:p.annexes[0]}};
  assert.equal(g.buyAllAnnexes(),false);
  assert.equal(g.game.players[0].ownedProperties[p.id],0);
});
test('mod rapid este păstrat la salvare și reluare', () => {
  const g=setup(), restored=new Game(cat);
  restored.restore(SnapshotCodec.decode(SnapshotCodec.encode(g.snapshot())),null);
  assert.equal(restored.rapidMode,true);
});

test('mod rapid cumpără exact două anexe în ordine', () => {
  const g=setup(), p=cat.actions.poli;
  g.game.players[0].ownedProperties[p.id]=0;
  g.offer={playerId:'p0',propertyId:p.id,option:{type:'ANNEX',annex:p.annexes[0]}};
  const prior=g.game.players[0].money;
  assert.equal(g.buyAnnexes(2),true);
  assert.equal(g.game.players[0].ownedProperties[p.id],2);
  assert.equal(g.game.players[0].money,prior-p.annexes[0].price-p.annexes[1].price);
});
