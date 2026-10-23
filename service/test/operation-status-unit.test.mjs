import test from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {operationStatusInput} from '../src/operation-status-routes.mjs';

test('operation status query preserves canonical input order up to100 IDs',()=>{
 const ids=Array.from({length:100},()=>randomUUID());
 assert.deepEqual(operationStatusInput({operationIds:ids}),ids);
 assert.deepEqual(operationStatusInput({operationIds:[ids[1].toUpperCase(),ids[0]]}),[ids[1],ids[0]]);
});
test('operation status query rejects excess fields, coercion, duplicates and oversized batches',()=>{
 const id=randomUUID();
 for(const value of [null,[],{}, {operationIds:null},{operationIds:id},{operationIds:[]},{operationIds:[1]},
  {operationIds:[id,id.toUpperCase()]},{operationIds:[id],actorId:randomUUID()},{operationIds:[id],password:'not-accepted'},
  {operationIds:Array.from({length:101},()=>randomUUID())},{operationIds:['not-a-uuid']}]) {
  assert.throws(()=>operationStatusInput(value),error=>error.status===400&&error.code==='VALIDATION_ERROR');
 }
});
