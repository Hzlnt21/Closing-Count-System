import test from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {lifecycleInput,lifecycleRole,lifecycleTransition} from '../src/lifecycle.mjs';
const body=()=>({operationId:randomUUID(),generation:1,expectedRevision:2,deviceSyncState:'SYNCED'});
test('lifecycle role matrix permits employee submit and supervisor/admin review actions only',()=>{
  for(const role of ['EMPLOYEE','SUPERVISOR','ADMIN'])lifecycleRole('submit',role);
  for(const action of ['return','lock','reopen']){assert.throws(()=>lifecycleRole(action,'EMPLOYEE'),{code:'OUTLET_FORBIDDEN'});for(const role of ['SUPERVISOR','ADMIN'])lifecycleRole(action,role);}
  assert.throws(()=>lifecycleRole('delete','ADMIN'));assert.throws(()=>lifecycleRole('lock','OWNER'));
});
test('lifecycle graph increments every revision and opens a new edit generation on return/reopen',()=>{
  const input=body();
  for(const [action,state,next,generation] of [['submit','DRAFT','SUBMITTED',1],['return','SUBMITTED','DRAFT',2],['lock','SUBMITTED','LOCKED',1],['reopen','LOCKED','DRAFT',2]]){
    assert.deepEqual(lifecycleTransition(action,{state,revision:'2',generation:'1'},input,'SUPERVISOR'),{state:next,revision:3,generation});
  }
});
test('return/reopen reasons are required, bounded and normalized without accepting injected reviewer/totals',()=>{
  for(const action of ['return','reopen']){
    for(const reason of ['', '   ',null,12,'x'.repeat(501)])assert.throws(()=>lifecycleInput(action,{...body(),reason}));
    assert.equal(lifecycleInput(action,{...body(),reason:'  Correct sale count  '}).reason,'Correct sale count');
    assert.throws(()=>lifecycleInput(action,body()));
  }
  for(const extra of [{reviewerId:randomUUID()},{totals:[]},{reason:'unrequested'}])assert.throws(()=>lifecycleInput('lock',{...body(),...extra}));
});
test('device sync declaration rejects all pending/conflict/auth states and unknown values',()=>{
  for(const deviceSyncState of ['LOCAL_SAVED','PENDING','SYNCING','CONFLICT','FAILED','AUTH_REQUIRED'])assert.throws(()=>lifecycleInput('submit',{...body(),deviceSyncState}),{code:'LOCAL_CHANGES_PENDING'});
  for(const deviceSyncState of [null,true,'yes'])assert.throws(()=>lifecycleInput('submit',{...body(),deviceSyncState}),{code:'VALIDATION_ERROR'});
  assert.equal(lifecycleInput('submit',body()).deviceSyncState,'SYNCED');
});
test('lifecycle rejects stale generation/revision with current server metadata',()=>{
  assert.throws(()=>lifecycleTransition('submit',{state:'DRAFT',revision:2,generation:2},body(),'EMPLOYEE'),{code:'GENERATION_CONFLICT'});
  assert.throws(()=>lifecycleTransition('lock',{state:'SUBMITTED',revision:3,generation:1},body(),'SUPERVISOR'),{code:'REVISION_CONFLICT'});
});
test('unsupported current states cannot be skipped by supplying the correct revision',()=>{
  for(const [action,state] of [['submit','LOCKED'],['lock','DRAFT'],['return','LOCKED'],['reopen','SUBMITTED']])assert.throws(()=>lifecycleTransition(action,{state,revision:2,generation:1},body(),'ADMIN'),{code:'CLOSING_STATE_CONFLICT'});
});
test('revision/generation limits fail without unsafe-number increments',()=>{
  const max=Number.MAX_SAFE_INTEGER;
  assert.throws(()=>lifecycleTransition('submit',{state:'DRAFT',revision:max,generation:1},{...body(),expectedRevision:max},'EMPLOYEE'),{code:'REVISION_LIMIT'});
  assert.throws(()=>lifecycleTransition('reopen',{state:'LOCKED',revision:2,generation:max},{...body(),generation:max},'ADMIN'),{code:'REVISION_LIMIT'});
});
