// Explicit operator-only acceptance of the owned local DEMO runtime.
// It rotates fictional generations. It never selects a physical Android device.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';
import {createServer} from 'node:http';
import {createHash} from 'node:crypto';
import {loadState,packageRoot,readJson,stateDirectory} from '../../demo/private-state.mjs';
import {appPool,assertContainer} from './database.mjs';
import {currentGeneration,privateGeneration,manifest,seedBaseline} from './generation.mjs';
import {assertOwnedDatabase} from '../src/config.mjs';
const [directory,evidence,flag]=process.argv.slice(2);
assert.equal(flag,'--rotate-owned-demo');const state=loadState(directory),out=stateDirectory(evidence);
fs.mkdirSync(out,{recursive:true});assert(!fs.existsSync(path.join(out,'runtime-proof.json')),'Use a fresh acceptance evidence namespace');
const steps=[];let pool=appPool(state);
const jsonHash=value=>createHash('sha256').update(JSON.stringify(value)).digest('hex');
async function note(name,action){await action();steps.push({name,passed:true});console.log(JSON.stringify({step:name,passed:true}));}
function cli(action,ok=true) {
 const result=spawnSync(process.execPath,[path.join(packageRoot,'demo/runtime.mjs'),action,'--state-dir',state.dir],{encoding:'utf8',timeout:90000});
 if(!ok){assert.equal(result.status,1);return;}
 assert.equal(result.status,0,'Demo command '+action+' failed');return JSON.parse(result.stdout);
}
async function api(method,route,body,token) {
 const response=await fetch('http://127.0.0.1:4372/api/v1'+route,{method,headers:{...(token?{Authorization:'Bearer '+token}:{}),...(body===undefined?{}:{'Content-Type':'application/json'})},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(10000)});
 return {status:response.status,body:await response.json()};
}
async function login(g,key){const actor=g.actors[key],r=await api('POST','/auth/login',{username:actor.username,password:actor.password});assert.equal(r.status,200);return r.body.data.accessToken;}
async function frozen(outlet) {
 const value={};for(const table of ['catalog_versions','operation_receipts','closing_audit','closing_reports']){
  value[table]=(await pool.query(`SELECT to_jsonb(t)-'fingerprint' AS row FROM ccs.${table} t WHERE outlet_id=$1 ORDER BY to_jsonb(t)::text`,[outlet])).rows.map(r=>r.row);
 }
 return{sha256:jsonHash(value),counts:Object.fromEntries(Object.entries(value).map(([k,v])=>[k,v.length]))};
}
async function baseline(g) {
 const rows=(await pool.query('SELECT id,business_date::text,state,generation::int,revision::int FROM ccs.closings WHERE outlet_id=$1 ORDER BY business_date',[g.outlet])).rows;assert.equal(rows.length,2);
 for(const kind of ['history','current']) {
  const expected=manifest.baseline[kind],d=new Date(g.anchor+'T12:00:00Z');d.setUTCDate(d.getUTCDate()+expected.dayOffset);
  const c=rows.find(r=>r.business_date===d.toISOString().slice(0,10));assert(c);assert.equal(c.state,expected.state);assert.equal(c.generation,1);assert.equal(c.revision,kind==='history'?4:2);
  const entries=(await pool.query('SELECT menu_id,quantity,updated_by FROM ccs.closing_entries WHERE closing_id=$1 ORDER BY menu_id',[c.id])).rows;
  assert.equal(entries.length,2);assert.equal(entries.reduce((sum,r)=>sum+r.quantity,0),expected.totalPortions);
  for(const [key,actor] of [['cappuccino','rani'],['latte','dimas']]){const row=entries.find(r=>r.menu_id===g.ids[key]);assert.equal(row.quantity,expected.quantities[key]);assert.equal(row.updated_by,g.actors[actor].id);}
  for(const ingredient of manifest.ingredients) {
   const total=entries.reduce((sum,e)=>{const menu=manifest.menus.find(m=>g.ids[m.key]===e.menu_id);return sum+(menu.ingredients.includes(ingredient.key)?e.quantity:0);},0);assert.equal(total,expected.ingredients[ingredient.key]);
  }
 }
 const frozenState=await frozen(g.outlet);assert.deepEqual(frozenState.counts,{catalog_versions:1,operation_receipts:6,closing_audit:8,closing_reports:1});return frozenState;
}
let first,initial,oldToken,second;
try {
 await assertOwnedDatabase(pool,state.config);assertContainer(state);
 const row=await currentGeneration(pool);assert.equal(row?.status,'READY');assert.equal(row.number,1,'This verifier expects the dedicated initial acceptance generation');first=privateGeneration(state,row.number);
 await note('independent-initial-baseline',async()=>{initial=await baseline(first);oldToken=await login(first,'rani');});
 await note('repeated-init-and-seed-preserve-current-data',async()=>{
  assert(cli('init').alreadySeeded);assert(cli('seed').alreadySeeded);assert.deepEqual(await baseline(first),initial);
 });
 await note('normal-api-seed-ids-replay-without-duplicate-history',async()=>{
  await seedBaseline(pool,first);assert.deepEqual(await baseline(first),initial);
 });
 await note('start-is-idempotent-and-identifies-same-owned-run',async()=>{
  const before=readJson(path.join(state.dir,'active-run.json'));assert(cli('start').alreadyRunning);assert.equal(readJson(path.join(state.dir,'active-run.json')).run,before.run);assert(cli('status').running);
 });
 await pool.end();pool=null;
 await note('two-stops-retain-volume-and-shutdown-owned-child',async()=>{
  assert.equal(cli('stop').running,false);assert.equal(cli('stop').alreadyStopped,true);assert.equal(cli('status').running,false);assert.equal(assertContainer(state).State.Running,false);
 });
 await note('foreign-api-listener-refused-before-database-start',async()=>{
  const before=jsonHash(readJson(path.join(state.dir,'active-run.json'))),foreign=createServer((_q,r)=>r.end(JSON.stringify({data:{environment:'DEMO',service:'closing-count'}})));
  await new Promise((resolve,reject)=>{foreign.once('error',reject);foreign.listen(4372,'127.0.0.1',resolve);});
  // spawnSync would block this verifier's own fake HTTP listener. Async child
  // keeps the foreign server responsive without weakening the ownership probe.
  try {
   const {spawn}=await import('node:child_process');const code=await new Promise((resolve,reject)=>{const c=spawn(process.execPath,[path.join(packageRoot,'demo/runtime.mjs'),'start','--state-dir',state.dir],{stdio:'ignore',windowsHide:true});c.once('error',reject);c.once('exit',resolve);});
   assert.equal(code,1);assert.equal(jsonHash(readJson(path.join(state.dir,'active-run.json'))),before);assert.equal(assertContainer(state).State.Running,false);
  }finally{await new Promise(resolve=>foreign.close(resolve));}
 });
 await note('restart-retains-data-and-prior-session',async()=>{
  assert(cli('start').running);pool=appPool(state);await assertOwnedDatabase(pool,state.config);assert.deepEqual(await baseline(first),initial);assert.equal((await api('GET','/me',undefined,oldToken)).status,200);
 });
 await note('reset-archives-generation-and-revokes-old-access',async()=>{
  const result=cli('reset');assert.equal(result.generation,2);assert(result.priorGenerationArchived);
  const row=await currentGeneration(pool);assert.equal(row.number,2);assert.equal(row.status,'READY');second=privateGeneration(state,2);
  assert.notEqual(second.outlet,first.outlet);await baseline(second);
  assert.deepEqual(await frozen(first.outlet),initial);
  const archived=(await pool.query('SELECT status,archived_at FROM ccs_demo.generations WHERE number=1')).rows[0];assert.equal(archived.status,'ARCHIVED');assert(archived.archived_at);
  assert.equal((await api('GET',`/outlets/${first.outlet}/catalog`,undefined,oldToken)).status,401);
  const actor=first.actors.rani;assert.equal((await api('POST','/auth/login',{username:actor.username,password:actor.password})).status,401);
  assert.equal((await pool.query('SELECT count(*)::int AS n FROM ccs.accounts WHERE id=ANY($1::uuid[]) AND active',[Object.values(first.actors).map(a=>a.id)])).rows[0].n,0);
  const token=await login(second,'rani');assert.equal((await api('GET',`/outlets/${first.outlet}/catalog`,undefined,token)).status,403);assert.equal((await api('GET',`/outlets/${second.outlet}/catalog`,undefined,token)).status,200);await api('POST','/auth/logout',{},token);
 });
 await note('repeated-seed-after-reset-preserves-new-baseline',async()=>{assert(cli('seed').alreadySeeded);await baseline(second);assert.deepEqual(await frozen(first.outlet),initial);});
 const events=path.join(state.dir,'server-events.jsonl');assert.equal(fs.existsSync(events)?fs.readFileSync(events,'utf8').trim():'','');
 const proof={passed:true,scope:'Local DEMO service/control-plane foundation; no native Android workflow or physical UAT claim',steps,logicalFixtureManifestVersion:manifest.version,firstGeneration:1,currentGeneration:2,archivedImmutableData:initial,seedUsesRealApi:true,rateClockSimulated:false,sourceCredentialsPrinted:false,physicalPhoneAccessed:false,nativeDemoJourneyComplete:false,portablePackageComplete:false,ccs08Complete:false,fullGoalComplete:false};
 fs.writeFileSync(path.join(out,'runtime-proof.json'),JSON.stringify(proof,null,2)+'\n');
 console.log(JSON.stringify({passed:true,scenarios:steps.length,ccs08Complete:false}));
}catch(error){console.error('Owned demo foundation acceptance failed: '+(error.code||error.name));process.exitCode=1;}
finally{if(pool)await pool.end();try{cli('stop');}catch{console.error('Owned demo cleanup needs inspection');process.exitCode=1;}}
