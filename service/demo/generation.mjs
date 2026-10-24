import fs from 'node:fs';
import path from 'node:path';
import {createHash,randomUUID,randomBytes} from 'node:crypto';
import {assertOwnedDatabase} from '../src/config.mjs';
import {hashPassword} from '../src/passwords.mjs';
import {normalizeCatalog} from '../src/catalog.mjs';
import {businessDate} from '../src/business-date.mjs';
import {createApp} from '../src/app.mjs';
import {appPool} from './database.mjs';
import {atomicJson,readJson} from '../../demo/private-state.mjs';
export const manifestBytes=fs.readFileSync(new URL('../../demo/manifest.v1.json',import.meta.url));
export const manifest=JSON.parse(manifestBytes),manifestSha=createHash('sha256').update(manifestBytes).digest('hex');
export async function controlSchema(pool,state) {
 await assertOwnedDatabase(pool,state.config);
 await pool.query('CREATE SCHEMA IF NOT EXISTS ccs_demo AUTHORIZATION ccs_portfolio_app');
 const owner=await pool.query("SELECT r.rolname FROM pg_namespace n JOIN pg_roles r ON r.oid=n.nspowner WHERE n.nspname='ccs_demo'");
 if(owner.rows[0]?.rolname!=='ccs_portfolio_app')throw Error('Refusing foreign demo control schema');
 await pool.query(`CREATE TABLE IF NOT EXISTS ccs_demo.generations (
  number integer PRIMARY KEY CHECK(number BETWEEN 1 AND 10000),outlet_id uuid NOT NULL UNIQUE REFERENCES ccs.outlets(id),
  manifest_sha text NOT NULL CHECK(length(manifest_sha)=64),anchor_date date NOT NULL,
  status text NOT NULL CHECK(status IN ('SEEDING','READY','ARCHIVED')),identity jsonb NOT NULL,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),ready_at timestamptz,archived_at timestamptz,
  CHECK((status='ARCHIVED')=(archived_at IS NOT NULL)))`);
 await pool.query("CREATE UNIQUE INDEX IF NOT EXISTS ccs_demo_one_current ON ccs_demo.generations((true)) WHERE status!='ARCHIVED'");
}
export async function currentGeneration(pool) {
 const {rows:[row]}=await pool.query("SELECT *,anchor_date::text AS anchor FROM ccs_demo.generations WHERE status!='ARCHIVED'");return row;
}
const dateOffset=(date,offset)=>{const d=new Date(date+'T12:00:00Z');d.setUTCDate(d.getUTCDate()+offset);return d.toISOString().slice(0,10);};
export function privateGeneration(state,number) {
 const file=path.join(state.dir,'generation-'+String(number).padStart(5,'0')+'.private.json');
 if(!fs.existsSync(file)) {
  const ids=Object.fromEntries(['ingredientCategory','menuCategory',...manifest.ingredients.map(i=>i.key),...manifest.menus.map(m=>m.key)].map(k=>[k,randomUUID()]));
  const operations=Object.fromEntries(['history.cappuccino','history.latte','history.submit','history.lock','current.cappuccino','current.latte'].map(k=>[k,randomUUID()]));
  const actors=Object.fromEntries(manifest.roles.map(r=>[r.key,{...r,id:randomUUID(),username:`demo.g${String(number).padStart(5,'0')}.${r.key}`,password:randomBytes(16).toString('hex')+'!Demo'}]));
  atomicJson(file,{format:'closing-count-demo-generation-private',version:1,number,instance:state.value.instance,manifestSha,anchor:businessDate(new Date(),manifest.timezone,manifest.cutoffMinutes),outlet:randomUUID(),catalogVersion:randomUUID(),ids,actors,operations});
 }
 if(fs.lstatSync(file).isSymbolicLink())throw Error('Refusing linked generation credentials');
 const value=readJson(file);
 if(value.format!=='closing-count-demo-generation-private'||value.version!==1||value.number!==number||value.instance!==state.value.instance||value.manifestSha!==manifestSha)throw Error('Generation identity/manifest does not match');
 for(const id of [value.outlet,value.catalogVersion,...Object.values(value.ids),...Object.values(value.operations),...Object.values(value.actors).map(a=>a.id)])if(!/^[a-f0-9-]{36}$/.test(id))throw Error('Generation UUID is malformed');
 for(const role of manifest.roles) {
  const a=value.actors[role.key];if(a.username!==`demo.g${String(number).padStart(5,'0')}.${role.key}`||a.role!==role.role||a.displayName!==role.displayName||!/^[a-f0-9]{32}!Demo$/.test(a.password))throw Error('Generation actor does not match');
 }
 return value;
}
const catalogFor=g=>normalizeCatalog({
 ingredientCategories:[{id:g.ids.ingredientCategory,name:'Bahan baku',sortOrder:0}],menuCategories:[{id:g.ids.menuCategory,name:'Coffee',sortOrder:0}],
 ingredients:manifest.ingredients.map((i,index)=>({id:g.ids[i.key],categoryId:g.ids.ingredientCategory,name:i.name,sortOrder:index,active:true})),
 menus:manifest.menus.map((m,index)=>({id:g.ids[m.key],categoryId:g.ids.menuCategory,name:m.name,sortOrder:index,active:true,ingredientIds:m.ingredients.map(k=>g.ids[k])}))
});
export const identityFor=g=>({number:g.number,outlet:g.outlet,catalogVersion:g.catalogVersion,ids:g.ids,actors:Object.fromEntries(Object.entries(g.actors).map(([k,{password,...a}])=>[k,a]))});
export async function seedGeneration(state,{reset=false,onProgress}={}) {
 if(onProgress!==undefined&&typeof onProgress!=='function')throw Error('Invalid local seed callback');
 const pool=appPool(state);
 try {
  await controlSchema(pool,state);const db=await pool.connect();let g,row;
  try {
   await db.query('BEGIN');await db.query("SELECT pg_advisory_xact_lock(hashtextextended('ccs-demo-generation',0))");
   row=await currentGeneration(db);
   if(reset&&row&&row.status!=='READY')throw Error('Resume the incomplete seed before resetting');
   if(row&&!reset) {
    g=privateGeneration(state,row.number);
    if(row.manifest_sha!==manifestSha||JSON.stringify(row.identity)!==JSON.stringify(identityFor(g))) {
     // jsonb reorders keys. Compare structurally by asking PostgreSQL itself.
     const {rows:[same]}=await db.query('SELECT $1::jsonb=$2::jsonb AS ok',[JSON.stringify(row.identity),JSON.stringify(identityFor(g))]);
     if(row.manifest_sha!==manifestSha||!same.ok)throw Error('Existing generation identity/manifest differs');
    }
    await db.query('COMMIT');
    if(row.status==='READY')return {generation:g.number,outlet:g.outlet,anchor:g.anchor,alreadySeeded:true};
   }else {
    const {rows:[last]}=await db.query('SELECT coalesce(max(number),0)::int AS number FROM ccs_demo.generations');
    if(last.number>=10000)throw Error('Demo generation limit reached; no history removed');
    g=privateGeneration(state,last.number+1);const catalog=catalogFor(g);
    if(row) {
     await db.query('SELECT id FROM ccs.outlets WHERE id=$1 FOR UPDATE',[row.outlet_id]);
     // Operator-only reset rotates an owned fixture, never edits immutable data.
     await db.query('UPDATE ccs.outlets SET active=false WHERE id=$1',[row.outlet_id]);
     await db.query('UPDATE ccs.accounts SET active=false WHERE id IN (SELECT account_id FROM ccs.memberships WHERE outlet_id=$1)',[row.outlet_id]);
     await db.query('UPDATE ccs.sessions SET revoked_at=coalesce(revoked_at,clock_timestamp()) WHERE account_id IN (SELECT account_id FROM ccs.memberships WHERE outlet_id=$1)',[row.outlet_id]);
     await db.query("UPDATE ccs_demo.generations SET status='ARCHIVED',archived_at=clock_timestamp() WHERE number=$1",[row.number]);
    }
    await db.query('INSERT INTO ccs.outlets(id,name,timezone,cutoff_minutes) VALUES ($1,$2,$3,$4)',[g.outlet,manifest.outletName+' g'+g.number,manifest.timezone,manifest.cutoffMinutes]);
    for(const actor of Object.values(g.actors)) {
     await db.query('INSERT INTO ccs.accounts(id,username,display_name,password_hash) VALUES ($1,$2,$3,$4)',[actor.id,actor.username,actor.displayName,await hashPassword(actor.password)]);
     await db.query('INSERT INTO ccs.memberships(outlet_id,account_id,role) VALUES ($1,$2,$3)',[g.outlet,actor.id,actor.role]);
    }
    await db.query('INSERT INTO ccs.catalog_versions(id,outlet_id,revision,snapshot,created_by) VALUES ($1,$2,1,$3::jsonb,$4)',[g.catalogVersion,g.outlet,JSON.stringify(catalog),g.actors.dewi.id]);
    await db.query("INSERT INTO ccs_demo.generations(number,outlet_id,manifest_sha,anchor_date,status,identity) VALUES ($1,$2,$3,$4,'SEEDING',$5::jsonb)",[g.number,g.outlet,manifestSha,g.anchor,JSON.stringify(identityFor(g))]);
    await db.query('COMMIT');
   }
  }catch(e){await db.query('ROLLBACK').catch(()=>{});throw e;}finally{db.release();}
  await onProgress?.({stage:'generation-committed',generation:g.number});
  await seedBaseline(pool,g,{onProgress});
  await pool.query("UPDATE ccs_demo.generations SET status='READY',ready_at=clock_timestamp() WHERE number=$1 AND status='SEEDING'",[g.number]);
  return {generation:g.number,outlet:g.outlet,anchor:g.anchor,alreadySeeded:false,baselineSeededThroughRealApi:true,priorGenerationArchived:reset&&!!row};
 }finally{await pool.end();}
}
export async function seedBaseline(pool,g,{onProgress}={}) {
 const internal=[];const server=await createApp({pool,environment:'DEMO',onInternalError:e=>internal.push(e.requestId)});
 await new Promise((resolve,reject)=>{server.once('error',reject);server.listen(0,'127.0.0.1',resolve);});
 const base='http://127.0.0.1:'+server.address().port,tokens={};
 async function api(method,route,body,actor='rani') {
  const response=await fetch(base+'/api/v1'+route,{method,headers:{...(tokens[actor]?{Authorization:'Bearer '+tokens[actor]}:{}),...(body===undefined?{}:{'Content-Type':'application/json'})},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(10000)});
  const value=await response.json();if(!response.ok)throw Error('Demo baseline API refused: '+response.status+'/'+(value.error?.code||'UNKNOWN'));return value.data;
 }
 try {
  for(const key of ['rani','dimas','arif'])tokens[key]=(await api('POST','/auth/login',{username:g.actors[key].username,password:g.actors[key].password},'anonymous')).accessToken;
  for(const kind of ['history','current']) {
   const expected=manifest.baseline[kind],date=dateOffset(g.anchor,expected.dayOffset);
   const c=await api('POST',`/outlets/${g.outlet}/closings`,{businessDate:date});
   for(const [key,actor] of [['cappuccino','rani'],['latte','dimas']]) {
    await api('POST',`/closings/${c.id}/entries/${g.ids[key]}/operations`,{operationId:g.operations[kind+'.'+key],generation:1,expectedEntryRevision:0,quantity:expected.quantities[key]},actor);
    await onProgress?.({stage:'quantity-committed',kind,menu:key,generation:g.number});
   }
   if(kind==='history') {
    await api('POST',`/closings/${c.id}/submit`,{operationId:g.operations['history.submit'],generation:1,expectedRevision:2,deviceSyncState:'SYNCED'});
    await api('POST',`/closings/${c.id}/lock`,{operationId:g.operations['history.lock'],generation:1,expectedRevision:3,deviceSyncState:'SYNCED'},'arif');
   }
  }
  if(internal.length)throw Error('Demo baseline encountered an internal server error');
 }finally {
  for(const actor of Object.keys(tokens))await api('POST','/auth/logout',{},actor).catch(()=>{});
  await new Promise(resolve=>server.close(resolve));
 }
}
