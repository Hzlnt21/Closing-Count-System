import test from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import pg from 'pg';
import {configFromEnvironment,assertOwnedDatabase} from '../src/config.mjs';
import {migrate} from '../src/migrate.mjs';
import {createApp} from '../src/app.mjs';
import {hashPassword} from '../src/passwords.mjs';
import {LoginLimiter} from '../src/limiter.mjs';

test('CCS team operations real database and HTTP',async t=>{
 const config=configFromEnvironment(),pool=new pg.Pool({connectionString:config.databaseUrl,max:8,statement_timeout:10000});
 const outlet=randomUUID(),foreign=randomUUID(),prefix='qat'+Date.now(),password='QA Team!2026Long';
 const ids=Object.fromEntries(['admin','admin2','employee','supervisor'].map(k=>[k,randomUUID()])),tokens={},internal=[];
 let base,server,failId,created,tick=0;
 const observed={query:pool.query.bind(pool),connect:async()=>{const c=await pool.connect();return{release:()=>c.release(),query:async(sql,values)=>{
  if(failId&&sql.startsWith('INSERT INTO ccs.operation_receipts')&&values[2]===failId){failId=null;throw Error('Owned team receipt failure');}return c.query(sql,values);
 }}}};
 async function api(method,path,body,role='admin'){
  const r=await fetch(base+'/api/v1'+path,{method,headers:{Authorization:'Bearer '+tokens[role],...(body?{'Content-Type':'application/json'}:{})},body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(10000)});
  return{status:r.status,body:await r.json()};
 }
 const create=(suffix,role='EMPLOYEE')=>({operationId:randomUUID(),kind:'CREATE_ACCOUNT',payload:{username:prefix+'.'+suffix,displayName:'Dummy '+suffix,role,password}});
 const edit=(id,rev,role,active=true)=>({operationId:randomUUID(),kind:'UPDATE_MEMBERSHIP',payload:{accountId:id,expectedRevision:rev,role,active}});
 const send=(op,role='admin')=>api('POST',`/outlets/${outlet}/team-operations`,op,role);
 try{
  await assertOwnedDatabase(pool,config);await migrate(pool,config);const hash=await hashPassword(password);
  await pool.query('INSERT INTO ccs.outlets(id,name) VALUES ($1,$2),($3,$4)',[outlet,'Team QA '+prefix,foreign,'Foreign QA']);
  for(const [role,id]of Object.entries(ids)){
   await pool.query('INSERT INTO ccs.accounts(id,username,display_name,password_hash) VALUES ($1,$2,$3,$4)',[id,prefix+'.'+role,role,hash]);
   await pool.query('INSERT INTO ccs.memberships(outlet_id,account_id,role) VALUES ($1,$2,$3)',[outlet,id,role.startsWith('admin')?'ADMIN':role.toUpperCase()]);
  }
  for(const suffix of ['a-b','ab','a_b']){const id=randomUUID();
   await pool.query('INSERT INTO ccs.accounts(id,username,display_name,password_hash) VALUES ($1,$2,$3,$4)',[id,prefix+'.'+suffix,'Punctuation '+suffix,hash]);
   await pool.query("INSERT INTO ccs.memberships(outlet_id,account_id,role) VALUES ($1,$2,'EMPLOYEE')",[outlet,id]);
  }
  server=await createApp({pool:observed,limiter:new LoginLimiter({now:()=>Date.now()+(tick++)*61000}),onInternalError:e=>internal.push(e.code)});
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));base='http://127.0.0.1:'+server.address().port;
  for(const role of Object.keys(ids)){const r=await fetch(base+'/api/v1/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:prefix+'.'+role,password})});assert.equal(r.status,200);tokens[role]=(await r.json()).data.accessToken;}
  await t.test('roster permissions pagination and public-only fields',async()=>{
   for(const role of ['employee','supervisor'])assert.equal((await api('GET',`/outlets/${outlet}/team`,null,role)).status,403);
   assert.equal((await api('GET',`/outlets/${foreign}/team`)).status,403);
   let after='',all=[];do{const r=await api('GET',`/outlets/${outlet}/team?limit=1`+(after?'&after='+after:''));assert.equal(r.status,200);all.push(...r.body.data.items);after=r.body.data.nextAfter;}while(after);
   assert.equal(all.length,7);assert.equal(new Set(all.map(m=>m.id)).size,7);assert.deepEqual(all.map(m=>m.username),all.map(m=>m.username).sort());assert.equal(JSON.stringify(all).includes('password'),false);
   assert.equal((await api('GET',`/outlets/${outlet}/team?after=bad%20cursor`)).status,400);
  });
  await t.test('duplicate create retries retain one account audit and receipt; changed secret is refused',async()=>{
   const op=create('new');const responses=await Promise.all([send(op),send(op)]);assert.deepEqual(responses.map(r=>r.status),[200,200]);assert.deepEqual(responses[0].body.data,responses[1].body.data);
   created=responses[0].body.data.member;
   const lookup=await api('GET',`/outlets/${outlet}/team-operations/${op.operationId}`);assert.equal(lookup.status,200);assert.deepEqual(lookup.body.data,responses[0].body.data);
   assert.equal(JSON.stringify(lookup.body).includes(password),false);assert.equal(JSON.stringify(lookup.body).includes('passwordHash'),false);
   assert.equal((await send({...op,payload:{...op.payload,password:'DifferentSecret!2026'}})).status,409);
   assert.equal((await send(create('new'))).status,409);
   const login=await fetch(base+'/api/v1/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:created.username,password})});assert.equal(login.status,200);
   assert.equal((await pool.query("SELECT count(*)::int AS n FROM ccs.membership_audit WHERE subject_id=$1 AND action='CREATE_ACCOUNT'",[created.id])).rows[0].n,1);
  });
  await t.test('two administrator changes cannot overwrite the same reviewed member revision',async()=>{
   const ops=[edit(created.id,0,'SUPERVISOR'),edit(created.id,0,'EMPLOYEE',false)];
   const results=await Promise.all(ops.map((op,i)=>send(op,i?'admin2':'admin')));assert.deepEqual(results.map(r=>r.status).sort(),[200,409]);
   const member=(await api('GET',`/outlets/${outlet}/team/${created.id}`)).body.data;assert.equal(member.revision,1);
  });
  await t.test('legacy membership writes also advance revision for native stale-review checks',async()=>{
   const before=(await api('GET',`/outlets/${outlet}/team/${ids.employee}`)).body.data;
   assert.equal((await api('PUT',`/outlets/${outlet}/memberships/${ids.employee}`,{role:'SUPERVISOR',active:true})).status,200);
   assert.equal((await send(edit(ids.employee,before.revision,'EMPLOYEE'))).body.error.code,'MEMBERSHIP_CONFLICT');
  });
  await t.test('receipt write failure rolls back account membership and audit together',async()=>{
   const op=create('rollback');failId=op.operationId;assert.equal((await send(op)).status,500);
   assert.equal((await pool.query('SELECT count(*)::int AS n FROM ccs.accounts WHERE username=$1',[op.payload.username])).rows[0].n,0);
   assert.equal((await api('GET',`/outlets/${outlet}/team-operations/${op.operationId}`)).status,404);
   assert.equal((await send(op)).status,200);assert.deepEqual(internal,['INTERNAL_ERROR']);internal.length=0;
  });
  await t.test('self access-loss receipt is recoverable without exposing roster or other receipts',async()=>{
   const op=edit(ids.admin2,0,'EMPLOYEE',false);const result=await send(op,'admin2');assert.equal(result.status,200);
   assert.equal((await api('GET',`/outlets/${outlet}/team-operations/${op.operationId}`,null,'admin2')).status,200);
   assert.equal((await api('GET',`/outlets/${outlet}/team`,null,'admin2')).status,403);
   assert.equal((await api('GET',`/outlets/${outlet}/team-operations/${randomUUID()}`,null,'admin2')).status,403);
   assert.equal((await send(op,'admin2')).status,403);
  });
  await t.test('last administrator and malformed/foreign/unauthorized updates are rejected',async()=>{
   assert.equal((await send(edit(ids.admin,0,'EMPLOYEE',false))).body.error.code,'LAST_ADMIN_REQUIRED');
   assert.equal((await send(edit(ids.admin,0,'ADMIN'),'supervisor')).status,403);
   assert.equal((await send(edit(randomUUID(),0,'EMPLOYEE'))).status,404);
   assert.equal((await send({...edit(ids.admin,0,'ADMIN'),payload:{accountId:ids.admin,expectedRevision:0,role:'OWNER',active:true}})).status,400);
   assert.equal((await send(create('short'), 'employee')).status,403);
   await assert.rejects(()=>pool.query('UPDATE ccs.membership_audit SET action=action WHERE outlet_id=$1',[outlet]),{code:'23514'});
  });
  assert.deepEqual(internal,[]);
 }finally{if(server)await new Promise(resolve=>server.close(resolve));await pool.end();}
});
