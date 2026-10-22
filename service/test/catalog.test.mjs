import test from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {normalizeCatalog,closingSnapshot,ingredientTotals,businessDateInput,revision} from '../src/catalog.mjs';
import {catalogFixture} from './catalog-fixture.mjs';
test('catalog normalization is canonical without changing IDs/formula or input object',()=>{
  const {catalog}=catalogFixture(),original=structuredClone(catalog);
  const a=normalizeCatalog(catalog);catalog.menus.reverse();catalog.ingredients.reverse();catalog.menus[0].ingredientIds.reverse();
  assert.deepEqual(normalizeCatalog(catalog),a);assert.notDeepEqual(catalog,original);
  assert.deepEqual(normalizeCatalog(original),a);
});
test('catalog refuses duplicate IDs/recipes, missing categories and inactive recipe references',()=>{
  for(const alter of [c=>c.ingredients.push(c.ingredients[0]),c=>c.menus[0].ingredientIds.push(c.menus[0].ingredientIds[0]),
    c=>c.menus[0].ingredientIds.push(randomUUID()),c=>c.menuCategories=[],c=>c.ingredientCategories=[],
    c=>c.ingredients[0].active=false,c=>c.menus[0].active='true',c=>c.ingredients[0].id='bad',
    c=>c.menus[0].name=' ',c=>c.menus[0].sortOrder=0.5,c=>c.menus[0].ingredientIds=[],c=>c.actorId=randomUUID()]){
    const {catalog}=catalogFixture();alter(catalog);assert.throws(()=>normalizeCatalog(catalog));
  }
});
test('inactive menus do not enter new closing snapshots; old snapshots remain independent',()=>{
  const {catalog,ids}=catalogFixture(),old=closingSnapshot(catalog);catalog.menus[0].active=false;
  const newer=closingSnapshot(catalog);assert.equal(old.menus.length,2);assert.equal(newer.menus.length,1);
  assert.equal(newer.ingredients.some(row=>row.id===ids.cocoa),false);
  catalog.menus[1].name='Renamed';assert.equal(old.menus.find(m=>m.id===ids.latte).name,'Latte');
});
test('ingredient formula matches Android baseline and handles zero/max quantities',()=>{
  const {catalog,ids}=catalogFixture(),snapshot=closingSnapshot(catalog);
  const entries=[{menuId:ids.cappuccino,quantity:2},{menuId:ids.latte,quantity:1}];
  const totals=Object.fromEntries(ingredientTotals(snapshot,entries).map(row=>[row.name,row.total]));
  assert.deepEqual(totals,{'Biji kopi':3,'Fresh milk':3,'Powder cokelat':2});
  assert.equal(ingredientTotals(snapshot,entries.map(row=>({...row,quantity:9999}))).find(row=>row.id===ids.coffee).total,19998);
  assert.deepEqual(ingredientTotals(snapshot,entries.map(row=>({...row,quantity:0}))),[]);
});
test('inconsistent stored entries fail closed instead of producing partial ingredient reports',()=>{
  const {catalog,ids}=catalogFixture(),snapshot=closingSnapshot(catalog);
  for(const entries of [[],[{menuId:ids.cappuccino,quantity:2},{menuId:ids.cappuccino,quantity:1}],
    [{menuId:ids.cappuccino,quantity:-1},{menuId:ids.latte,quantity:0}],
    [{menuId:randomUUID(),quantity:1},{menuId:ids.latte,quantity:0}]])assert.throws(()=>ingredientTotals(snapshot,entries));
});
test('ISO business dates and revisions reject normalization, ambiguous dates and unsafe integers',()=>{
  for(const input of [null,'2026-02-30','2026-2-01','0000-01-01','today','2025-02-29'])assert.throws(()=>businessDateInput(input));
  assert.equal(businessDateInput('2024-02-29'),'2024-02-29');
  for(const input of [null,-1,1.2,'1',Number.MAX_SAFE_INTEGER+1])assert.throws(()=>revision(input));
  assert.equal(revision(0),0);assert.throws(()=>revision(0,1));
});
