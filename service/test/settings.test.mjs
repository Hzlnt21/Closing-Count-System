import test from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import pg from 'pg';
import {configFromEnvironment,assertOwnedDatabase} from '../src/config.mjs';
import {migrate} from '../src/migrate.mjs';
import {createApp} from '../src/app.mjs';
import {hashPassword} from '../src/passwords.mjs';
import {catalogFixture} from './catalog-fixture.mjs';

test('CCS outlet settings transactions and frozen dates',async t=>{
 const config=configFromEnvironment(),pool=new pg.Pool({connectionString:config.databaseUrl,max:8,connectionTimeoutMillis:5000,statement_timeout:10000});
 const outlet=randomUUID(),foreign=randomUUID(),prefix='qas'+Date.now(),password='QA Settings!2026';
 const ids=Object.fromEntries(['admin','admin2','employee','supervisor'].map(k=>[k,randomUUID()])),tokens={},internal=[];
 let server,base,failId,oldClosing,winner;
 const observed={query:pool.query.bind(pool),connect:async()=>{const c=await pool.connect();return{release:()=>c.release(),query:async(sql,values)=>{
  if(failId&&sql.startsWith('INSERT INTO ccs.operation_receipts')&&values[2]===failId){failId=null;throw Error('Owned settings receipt failure');}return c.query(sql,values);
 }}}};
 async function api(method,path,body,role='admin'){
  const r=await fetch(base+'/api/v1'+path,{method,headers:{Authorization:'Bearer '+tokens[role],...(body?{'Content-Type':'application/json'}:{})},body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(10000)});
  return{status:r.status,body:await r.json()};
 }
 const command=(rev,name='Kedai Baru')=>({operationId:randomUUID(),expectedRevision:rev,name,timezone:'Asia/Makassar',cutoffMinutes:240});
 try{
  await assertOwnedDatabase(pool,config);await migrate(pool,config);const hash=await hashPassword(password);
  await pool.query('INSERT INTO ccs.outlets(id,name) VALUES ($1,$2),($3,$4)',[outlet,'Kedai Awal',foreign,'QA foreign']);
  for(const [role,id]of Object.entries(ids)){
   await pool.query('INSERT INTO ccs.accounts(id,username,display_name,password_hash) VALUES ($1,$2,$3,$4)',[id,prefix+'.'+role,role,hash]);
   await pool.query('INSERT INTO ccs.memberships(outlet_id,account_id,role) VALUES ($1,$2,$3)',[outlet,id,role.startsWith('admin')?'ADMIN':role.toUpperCase()]);
  }
  const catalog=catalogFixture().catalog;
  await pool.query('INSERT INTO ccs.catalog_versions(id,outlet_id,revision,snapshot,created_by) VALUES ($1,$2,1,$3::jsonb,$4)',[randomUUID(),outlet,JSON.stringify(catalog),ids.admin]);
  server=await createApp({pool:observed,onInternalError:e=>internal.push(e.code)});await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));base='http://127.0.0.1:'+server.address().port;
  for(const role of Object.keys(ids)){const r=await fetch(base+'/api/v1/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:prefix+'.'+role,password})});assert.equal(r.status,200);tokens[role]=(await r.json()).data.accessToken;}
  oldClosing=(await api('POST',`/outlets/${outlet}/closings`,{businessDate:'2026-10-01'})).body.data;
  await t.test('role/outlet and malformed input are denied without writing',async()=>{
   for(const role of ['employee','supervisor'])assert.equal((await api('POST',`/outlets/${outlet}/settings`,command(0),'employee'===role?'employee':'supervisor')).status,403);
   assert.equal((await api('POST',`/outlets/${foreign}/settings`,command(0))).status,403);
   for(const body of [{...command(0),timezone:'Not/AZone'},{...command(0),cutoffMinutes:1440},{...command(0),cutoffMinutes:1.5},{...command(0),name:' '},{...command(0),expectedRevision:null}])assert.equal((await api('POST',`/outlets/${outlet}/settings`,body)).status,400);
   assert.equal((await api('GET',`/outlets/${outlet}`)).body.data.settingsRevision,0);
  });
  await t.test('two administrators editing the same reviewed revision produce one conflict',async()=>{
   const operations=['admin','admin2'].map(role=>({role,body:command(0,'Kedai '+role)}));
   const results=await Promise.all(operations.map(async op=>({...op,...await api('POST',`/outlets/${outlet}/settings`,op.body,op.role)})));
   assert.deepEqual(results.map(r=>r.status).sort(),[200,409]);winner=results.find(r=>r.status===200);
   assert.equal(winner.body.data.settingsRevision,1); // response body replaces request body in spread
  });
  await t.test('old closings retain timezone/cutoff while new closing uses latest settings',async()=>{
   const old=(await api('GET','/closings/'+oldClosing.id)).body.data;
   assert.equal(old.timezone,'Asia/Jakarta');assert.equal(old.cutoffMinutes,0);
   const fresh=(await api('POST',`/outlets/${outlet}/closings`,{businessDate:'2026-10-02'})).body.data;
   assert.equal(fresh.timezone,'Asia/Makassar');assert.equal(fresh.cutoffMinutes,240);
   const next=await api('POST',`/outlets/${outlet}/settings`,command(1,'Kedai revisi 2'));assert.equal(next.status,200);
  });
  await t.test('receipt failure rolls back settings and audit before retry',async()=>{
   const op=command(2,'Kedai setelah rollback');failId=op.operationId;
   assert.equal((await api('POST',`/outlets/${outlet}/settings`,op)).status,500);
   assert.equal((await api('GET',`/outlets/${outlet}`)).body.data.settingsRevision,2);
   assert.equal((await pool.query('SELECT count(*)::int AS n FROM ccs.outlet_settings_audit WHERE outlet_id=$1',[outlet])).rows[0].n,2);
   const retry=await api('POST',`/outlets/${outlet}/settings`,op);assert.equal(retry.status,200);assert.equal(retry.body.data.settingsRevision,3);
   assert.deepEqual(internal,['INTERNAL_ERROR']);internal.length=0;
  });
  await t.test('lost reply retry is immutable and permission is checked again',async()=>{
   const response=winner.body.data;
   const original={operationId:response.operationId,expectedRevision:0,name:response.name,timezone:response.timezone,cutoffMinutes:response.cutoffMinutes};
   const replay=await api('POST',`/outlets/${outlet}/settings`,original,winner.role);assert.equal(replay.status,200);assert.deepEqual(replay.body.data,response);
   assert.equal((await api('POST',`/outlets/${outlet}/settings`,{...original,name:'Different'},winner.role)).status,409);
   await pool.query('UPDATE ccs.memberships SET active=false WHERE outlet_id=$1 AND account_id=$2',[outlet,ids[winner.role]]);
   assert.equal((await api('POST',`/outlets/${outlet}/settings`,original,winner.role)).status,403);
   assert.equal((await pool.query('SELECT settings_revision::text FROM ccs.outlets WHERE id=$1',[outlet])).rows[0].settings_revision,'3');
   assert.equal((await pool.query('SELECT count(*)::int AS n FROM ccs.outlet_settings_audit WHERE outlet_id=$1',[outlet])).rows[0].n,3);
   await assert.rejects(()=>pool.query('UPDATE ccs.outlet_settings_audit SET revision=revision WHERE outlet_id=$1',[outlet]),{code:'23514'});
  });
  assert.deepEqual(internal,[]);
 }finally{if(server)await new Promise(resolve=>server.close(resolve));await pool.end();}
});
