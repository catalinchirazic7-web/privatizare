import { test } from 'node:test';
import assert from 'node:assert/strict';
import { PROPERTY_STORIES } from '../js/game/propertyStories.js';
import { loadRealCatalog } from './helpers.mjs';

test('fiecare acțiune are o prezentare și un simbol', () => {
  for (const id of Object.keys(loadRealCatalog().actions)) {
    assert.ok(PROPERTY_STORIES[id]?.[0], `lipsește simbolul pentru ${id}`);
    assert.ok(PROPERTY_STORIES[id]?.[1], `lipsește prezentarea pentru ${id}`);
  }
});
