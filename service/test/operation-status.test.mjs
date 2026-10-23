import test from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID,createHash} from 'node:crypto';
import pg from 'pg';
import {configFromEnvironment,assertOwnedDatabase} from '../src/config.mjs';
import {migrate} from '../src/migrate.mjs';
import {createApp} from '../src/app.mjs';
import {hashPassword} from '../src/passwords.mjs';
import {LoginLimiter} from '../src/limiter.mjs';
import {catalogFixture} from './catalog-fixture.mjs';

test('PT03 scoped read-only operation status with real HTTP/PostgreSQL',async t=>{
 const config=configFromEnvironment(),pool=new pg.Pool({connectionString:config.databaseUrl,max:8,connectionTimeoutMillis:5000,statement_timeout:10000});
 const outlets=[randomUUID(),randomUUID(),randomUUID()],ids={employee:randomUUID(),colleague:randomUUID(),admin:randomUUID()},tokens={};
 const prefix='qao'+Date.now()+randomUUID().slice(0,5),password='QA Operation Status!2026';
 const fixture=catalogFixture(),internal=[],readonly=[];let server,base,tick=0,gate=null;
 const deferred=()=>{let resolve;const promise=new Promise(r=>resolve=r);return{promise,resolve}};
 const observed={query:pool.query.bind(pool),connect:async()=>{
  const client=await pool.connect();let reading=false;
  return{release:()=>client.release(),query:async(sql,values)=>{
   const result=await client.query(sql,values);
   if(sql.startsWith('BEGIN')&&sql.includes('READ ONLY')){
    reading=true;
    if(gate?.kind==='beforeSnapshot'){const g=gate;gate=null;g.enter.resolve();await g.release.promise;}
   }
   if(reading&&sql.includes('FROM ccs.sessions')&&gate?.kind==='afterAuth'){
    const g=gate;gate=null;g.enter.resolve();await g.release.promise;
   }
   if(reading&&sql.startsWith('SELECT operation_id FROM ccs.operation_receipts'))readonly.push((await client.query('SHOW transaction_read_only')).rows[0].transaction_read_only);
   return result;
  }};
 }};
 async function api(method,path,body,role='employee',extra={}){
  const r=await fetch(base+'/api/v1'+path,{method,headers:{...(role?{Authorization:'Bearer '+tokens[role]}:{}),...(body!==undefined?{'Content-Type':'application/json'}:{}),...extra},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(10000)});
  return{status:r.status,body:await r.json(),cache:r.headers.get('cache-control')};
 }
 const status=(list,role='employee',outlet=outlets[0])=>api('POST',`/outlets/${outlet}/operation-status`,{operationIds:list},role);
 async function login(role){const r=await api('POST','/auth/login',{username:prefix+'.'+role,password},null);assert.equal(r.status,200);tokens[role]=r.body.data.accessToken;}
 async function insertReceipt(db,outlet,actor,id){await db.query('INSERT INTO ccs.operation_receipts(outlet_id,actor_id,operation_id,fingerprint,response) VALUES ($1,$2,$3,$4,$5::jsonb)',[outlet,actor,id,'a'.repeat(64),JSON.stringify({privateReceiptMarker:'MUST_NOT_DISCLOSE',password:'SYNTHETIC_SECRET_SENTINEL'})]);}
 async function digest(){
  const data=[];
  for(const table of ['outlets','memberships','operation_receipts','closings','closing_audit','catalog_versions','outlet_settings_audit','membership_audit']){
   const key=table==='outlets'?'id':'outlet_id';const rows=await pool.query(`SELECT to_jsonb(x) AS value FROM ccs.${table} x WHERE ${key}=ANY($1::uuid[]) ORDER BY to_jsonb(x)::text`,[outlets]);data.push([table,rows.rows]);
  }
  data.push(['entries',(await pool.query('SELECT to_jsonb(e) AS value FROM ccs.closing_entries e JOIN ccs.closings c ON c.id=e.closing_id WHERE c.outlet_id=ANY($1::uuid[]) ORDER BY to_jsonb(e)::text',[outlets])).rows]);
  return createHash('sha256').update(JSON.stringify(data)).digest('hex');
 }
 async function guardedChange(change,expected){
  const g={kind:'beforeSnapshot',enter:deferred(),release:deferred()};gate=g;
  const response=status([known]);await g.enter.promise;
  try{await change();}finally{g.release.resolve();}
  assert.equal((await response).status,expected);
 }
 let known,settingsOp;
 try{
  await assertOwnedDatabase(pool,config);await migrate(pool,config);const hash=await hashPassword(password);
  for(const outlet of outlets)await pool.query('INSERT INTO ccs.outlets(id,name) VALUES ($1,$2)',[outlet,'Operation status QA '+prefix]);
  for(const [role,id]of Object.entries(ids)){
   await pool.query('INSERT INTO ccs.accounts(id,username,display_name,password_hash) VALUES ($1,$2,$3,$4)',[id,prefix+'.'+role,'Dummy '+role,hash]);
   for(const outlet of outlets.slice(0,2))await pool.query('INSERT INTO ccs.memberships(outlet_id,account_id,role) VALUES ($1,$2,$3)',[outlet,id,role==='admin'?'ADMIN':'EMPLOYEE']);
  }
  await pool.query('INSERT INTO ccs.catalog_versions(id,outlet_id,revision,snapshot,created_by) VALUES ($1,$2,1,$3::jsonb,$4)',[randomUUID(),outlets[0],JSON.stringify(fixture.catalog),ids.admin]);
  server=await createApp({pool:observed,limiter:new LoginLimiter({now:()=>Date.now()+(tick++)*61000}),onInternalError:e=>internal.push(e.code)});
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));base='http://127.0.0.1:'+server.address().port;
  for(const role of Object.keys(ids))await login(role);
  const created=await api('POST',`/outlets/${outlets[0]}/closings`,{businessDate:'2026-10-23'});assert.equal(created.status,201);
  known=randomUUID();const edit=await api('POST',`/closings/${created.body.data.id}/entries/${fixture.ids.latte}/operations`,{operationId:known,generation:1,expectedEntryRevision:0,quantity:4});assert.equal(edit.status,200);
  settingsOp=randomUUID();assert.equal((await api('POST',`/outlets/${outlets[0]}/settings`,{operationId:settingsOp,expectedRevision:0,name:'Status QA updated',timezone:'Asia/Jakarta',cutoffMinutes:0},'admin')).status,200);
  const otherActor=randomUUID(),otherOutlet=randomUUID();await insertReceipt(pool,outlets[0],ids.colleague,otherActor);await insertReceipt(pool,outlets[1],ids.employee,otherOutlet);
  const initial=await digest();
  await t.test('actual committed quantity and settings receipts are visible only to their actor/outlet',async()=>{
   const missing=randomUUID(),r=await status([missing,known,otherActor,otherOutlet,settingsOp]);assert.equal(r.status,200);assert.equal(r.cache,'no-store');
   assert.deepEqual(r.body.data,{outletId:outlets[0],items:[{operationId:missing,recorded:false},{operationId:known,recorded:true},{operationId:otherActor,recorded:false},{operationId:otherOutlet,recorded:false},{operationId:settingsOp,recorded:false}]});
   assert.deepEqual((await status([settingsOp,known],'admin')).body.data.items.map(i=>i.recorded),[true,false]);
   assert.equal((await status([otherActor],'colleague')).body.data.items[0].recorded,true);
   assert.equal((await status([otherOutlet],'employee',outlets[1])).body.data.items[0].recorded,true);
   for(const forbiddenText of ['MUST_NOT_DISCLOSE','SYNTHETIC_SECRET_SENTINEL','response','fingerprint','actorId'])assert.equal(JSON.stringify(r.body.data).includes(forbiddenText),false);
  });
  await t.test('canonical batch order/bounds and opaque missing/other-actor results',async()=>{
   const list=Array.from({length:100},()=>randomUUID());list[17]=known;const r=await status(list.map(x=>x.toUpperCase()));assert.equal(r.status,200);
   assert.deepEqual(r.body.data.items,list.map(operationId=>({operationId,recorded:operationId===known})));
   const r2=await status([otherActor,randomUUID()]);assert.deepEqual(r2.body.data.items.map(i=>i.recorded),[false,false]);
   assert.equal(await digest(),initial);
  });
  await t.test('anonymous/wrong outlet/malformed requests fail without changing business state',async()=>{
   assert.equal((await status([known],null)).status,401);
   assert.equal((await status([known],'employee',outlets[2])).status,403);
   for(const body of [{operationIds:[]},{operationIds:[known,known.toUpperCase()]},{operationIds:[5]},{operationIds:'invalid'},{operationIds:[known],actorId:ids.admin},{operationIds:Array.from({length:101},()=>randomUUID())}])assert.equal((await api('POST',`/outlets/${outlets[0]}/operation-status`,body)).status,400);
   assert.equal((await api('POST',`/outlets/${outlets[0]}/operation-status?actor=ignored`,{operationIds:[known]})).status,400);
   assert.equal((await api('POST',`/outlets/${outlets[0]}/operation-status`,{operationIds:[known],padding:'x'.repeat(9000)})).status,413);
   assert.equal((await api('POST',`/outlets/${outlets[0]}/operation-status`,{operationIds:[known]},'employee',{'Content-Type':'text/plain'})).status,415);
   assert.equal(await digest(),initial);
  });
  await t.test('membership/account/outlet denial is rechecked with current authorization',async()=>{
   await guardedChange(()=>pool.query('UPDATE ccs.memberships SET active=false WHERE outlet_id=$1 AND account_id=$2',[outlets[0],ids.employee]),403);
   await pool.query('UPDATE ccs.memberships SET active=true WHERE outlet_id=$1 AND account_id=$2',[outlets[0],ids.employee]);
   await guardedChange(()=>pool.query('UPDATE ccs.accounts SET active=false WHERE id=$1',[ids.employee]),403);await pool.query('UPDATE ccs.accounts SET active=true WHERE id=$1',[ids.employee]);
   await guardedChange(()=>pool.query('UPDATE ccs.outlets SET active=false WHERE id=$1',[outlets[0]]),403);await pool.query('UPDATE ccs.outlets SET active=true WHERE id=$1',[outlets[0]]);
  });
  await t.test('session revocation and expiry after outer authentication fail inside read transaction',async()=>{
   await guardedChange(()=>pool.query('UPDATE ccs.sessions SET revoked_at=clock_timestamp() WHERE account_id=$1',[ids.employee]),401);await login('employee');
   await guardedChange(()=>pool.query("UPDATE ccs.sessions SET expires_at=created_at+interval '1 millisecond' WHERE account_id=$1 AND revoked_at IS NULL",[ids.employee]),401);await login('employee');
  });
  await t.test('uncommitted operation is absent without waiting, then recorded after commit',async()=>{
   const writer=await pool.connect(),op=randomUUID();try{await writer.query('BEGIN');await insertReceipt(writer,outlets[0],ids.employee,op);
    assert.equal((await status([op])).body.data.items[0].recorded,false);await writer.query('COMMIT');assert.equal((await status([op])).body.data.items[0].recorded,true);
   }finally{await writer.query('ROLLBACK');writer.release();}
  });
  await t.test('consistent snapshot can miss a commit after its auth read and sees it next request',async()=>{
   const op=randomUUID(),g={kind:'afterAuth',enter:deferred(),release:deferred()};gate=g;
   const response=status([op]);await g.enter.promise;
   try{await insertReceipt(pool,outlets[0],ids.employee,op);}finally{g.release.resolve();}
   assert.equal((await response).body.data.items[0].recorded,false);assert.equal((await status([op])).body.data.items[0].recorded,true);
  });
  assert.ok(readonly.length>=10);assert.ok(readonly.every(mode=>mode==='on'));assert.deepEqual(internal,[]);
 }finally{if(gate)gate.release.resolve();if(server)await new Promise(resolve=>server.close(resolve));await pool.end();}
});
