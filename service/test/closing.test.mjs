import test from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import pg from 'pg';
import {configFromEnvironment,assertOwnedDatabase} from '../src/config.mjs';
import {migrate} from '../src/migrate.mjs';
import {createApp} from '../src/app.mjs';
import {hashPassword} from '../src/passwords.mjs';
import {catalogFixture} from './catalog-fixture.mjs';

test('CCS-02B/03 real catalog and collaborative closing',async t=>{
  const config=configFromEnvironment(),pool=new pg.Pool({connectionString:config.databaseUrl,max:8,connectionTimeoutMillis:5000,statement_timeout:10000});
  const prefix='qa'+Date.now()+randomUUID().slice(0,6),outlet=randomUUID(),otherOutlet=randomUUID();
  const accounts=Object.fromEntries(['admin','employee','colleague'].map(role=>[role,randomUUID()]));
  const tokens={},internal=[],{catalog,ids}=catalogFixture();
  let server,base,closingId,initialCatalog,duplicateAck,duplicateRequest,failReceipt=null,readGate=null;
  // A driver wrapper injects one failure after real entry/audit writes, before receipt.
  // A separate read barrier permits a concurrent real DB commit during GET's snapshot.
  const observedPool={query:pool.query.bind(pool),connect:async()=>{
    const client=await pool.connect();return {release:()=>client.release(),query:async(sql,values)=>{
      if(failReceipt&&sql.startsWith('INSERT INTO ccs.operation_receipts')&&values[2]===failReceipt){failReceipt=null;throw Error('Explicit QA receipt-write failure');}
      const result=await client.query(sql,values);
      if(readGate?.armed&&sql.startsWith('SELECT id,outlet_id,business_date::text')&&!sql.includes('FOR UPDATE')&&values[0]===readGate.id){
        readGate.armed=false;readGate.reached();await readGate.wait;
      }
      return result;
    }};
  }};
  async function api(method,path,body,role='employee'){
    const response=await fetch(base+'/api/v1'+path,{method,headers:{...(tokens[role]?{Authorization:'Bearer '+tokens[role]}:{}),...(body!==undefined?{'Content-Type':'application/json'}:{})},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(10000)});
    return {status:response.status,body:await response.json()};
  }
  const get=async id=>{const r=await api('GET','/closings/'+id);assert.equal(r.status,200);return r.body.data;};
  const operation=(menuId,body,role='employee',id=closingId)=>api('POST',`/closings/${id}/entries/${menuId}/operations`,body,role);
  const receiptCount=async op=>(await pool.query('SELECT count(*)::int AS n FROM ccs.operation_receipts WHERE outlet_id=$1 AND operation_id=$2',[outlet,op])).rows[0].n;
  const auditCount=async op=>(await pool.query('SELECT count(*)::int AS n FROM ccs.closing_audit WHERE closing_id=$1 AND operation_id=$2',[closingId,op])).rows[0].n;
  const bodyFor=(view,menuId,quantity,operationId=randomUUID())=>({operationId,generation:view.generation,expectedEntryRevision:view.entries.find(e=>e.menuId===menuId).entryRevision,quantity});
  try{
    await assertOwnedDatabase(pool,config);await migrate(pool,config);
    const password='QA Closing!2026',passwordHash=await hashPassword(password),client=await pool.connect();
    try{
      await client.query('BEGIN');await client.query('INSERT INTO ccs.outlets(id,name) VALUES ($1,$2),($3,$4)',[outlet,'Kedai closing '+prefix,otherOutlet,'Other '+prefix]);
      for(const role of Object.keys(accounts)){
        await client.query('INSERT INTO ccs.accounts(id,username,display_name,password_hash) VALUES ($1,$2,$3,$4)',[accounts[role],prefix+'.'+role,'QA '+role,passwordHash]);
        await client.query('INSERT INTO ccs.memberships(outlet_id,account_id,role) VALUES ($1,$2,$3)',[outlet,accounts[role],role==='admin'?'ADMIN':'EMPLOYEE']);
      }
      await client.query('COMMIT');
    }catch(e){await client.query('ROLLBACK');throw e;}finally{client.release();}
    server=await createApp({pool:observedPool,onInternalError:e=>internal.push(e.code)});
    await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));base='http://127.0.0.1:'+server.address().port;
    for(const role of Object.keys(accounts)){
      const result=await api('POST','/auth/login',{username:prefix+'.'+role,password},'anonymous');assert.equal(result.status,200);tokens[role]=result.body.data.accessToken;
    }
    await t.test('catalog administration is scoped and invalid references cannot publish',async()=>{
      const empty=await api('GET',`/outlets/${outlet}/catalog`);assert.equal(empty.status,200);assert.equal(empty.body.data.revision,0);
      const body={operationId:randomUUID(),expectedRevision:0,catalog};
      assert.equal((await api('POST',`/outlets/${outlet}/catalog`,body)).status,403);
      const bad=structuredClone(body);bad.catalog.menus[0].ingredientIds=[randomUUID()];
      assert.equal((await api('POST',`/outlets/${outlet}/catalog`,bad,'admin')).status,400);
      const oversized=structuredClone(body);oversized.catalog.menus[0].name='x'.repeat(600000);
      assert.equal((await api('POST',`/outlets/${outlet}/catalog`,oversized,'admin')).status,413);
      assert.equal((await api('POST',`/outlets/${outlet}/closings`,{businessDate:'2026-10-22'})).body.error.code,'CATALOG_REQUIRED');
    });
    const publishOperation=randomUUID();
    await t.test('simultaneous identical catalog operations create one immutable version and receipt',async()=>{
      const body={operationId:publishOperation,expectedRevision:0,catalog};
      const results=await Promise.all([api('POST',`/outlets/${outlet}/catalog`,body,'admin'),api('POST',`/outlets/${outlet}/catalog`,body,'admin')]);
      for(const r of results)assert.equal(r.status,200);assert.deepEqual(results[0].body.data,results[1].body.data);initialCatalog=results[0].body.data;
      assert.equal(initialCatalog.revision,1);assert.equal(await receiptCount(publishOperation),1);
      assert.equal((await pool.query('SELECT count(*)::int AS n FROM ccs.catalog_versions WHERE outlet_id=$1',[outlet])).rows[0].n,1);
      assert.equal((await api('POST',`/outlets/${outlet}/catalog`,{...body,expectedRevision:1},'admin')).body.error.code,'OPERATION_ID_REUSED');
    });
    await t.test('two employees creating the same outlet/date obtain a single closing and creation event',async()=>{
      const results=await Promise.all(['employee','colleague'].map(role=>api('POST',`/outlets/${outlet}/closings`,{businessDate:'2026-10-22'},role)));
      assert.deepEqual(results.map(r=>r.status).sort(),[200,201]);assert.equal(results[0].body.data.id,results[1].body.data.id);closingId=results[0].body.data.id;
      assert.equal((await pool.query("SELECT count(*)::int AS n FROM ccs.closing_audit WHERE closing_id=$1 AND action='CREATE'",[closingId])).rows[0].n,1);
      await assert.rejects(()=>pool.query(`INSERT INTO ccs.closings(id,outlet_id,business_date,catalog_version_id,snapshot,timezone,cutoff_minutes,created_by)
        SELECT $1,outlet_id,business_date,catalog_version_id,snapshot,timezone,cutoff_minutes,created_by FROM ccs.closings WHERE id=$2`,[randomUUID(),closingId]),{code:'23505'});
    });
    await t.test('different-menu edits merge with individual attribution and original ingredient formula',async()=>{
      const before=await get(closingId),results=await Promise.all([operation(ids.cappuccino,bodyFor(before,ids.cappuccino,2)),operation(ids.latte,bodyFor(before,ids.latte,1),'colleague')]);
      for(const r of results)assert.equal(r.status,200);
      const view=await get(closingId);assert.equal(view.closingRevision,2);assert.ok(view.entries.every(e=>e.entryRevision===1));
      assert.equal(view.entries.find(e=>e.menuId===ids.cappuccino).updatedBy.id,accounts.employee);
      assert.equal(view.entries.find(e=>e.menuId===ids.latte).updatedBy.id,accounts.colleague);
      assert.deepEqual(Object.fromEntries(view.ingredientTotals.map(i=>[i.name,i.total])),{'Biji kopi':3,'Fresh milk':3,'Powder cokelat':2});
    });
    await t.test('same-menu concurrent edits yield one success and a useful revision conflict',async()=>{
      const before=await get(closingId),results=await Promise.all([operation(ids.cappuccino,bodyFor(before,ids.cappuccino,4)),operation(ids.cappuccino,bodyFor(before,ids.cappuccino,5),'colleague')]);
      assert.deepEqual(results.map(r=>r.status).sort(),[200,409]);const rejected=results.find(r=>r.status===409),accepted=results.find(r=>r.status===200);
      assert.equal(rejected.body.error.code,'ENTRY_CONFLICT');assert.equal(rejected.body.error.details.current.quantity,accepted.body.data.entry.quantity);
      assert.equal(rejected.body.error.details.current.updatedBy.id,accepted.body.data.entry.updatedBy.id);
      assert.equal((await get(closingId)).closingRevision,before.closingRevision+1);
    });
    await t.test('concurrent retry of an identical operation commits one edit, audit and receipt',async()=>{
      const before=await get(closingId);duplicateRequest=bodyFor(before,ids.latte,7);
      const results=await Promise.all([operation(ids.latte,duplicateRequest),operation(ids.latte,duplicateRequest)]);
      for(const r of results)assert.equal(r.status,200);assert.deepEqual(results[0].body.data,results[1].body.data);duplicateAck=results[0].body.data;
      assert.equal(await receiptCount(duplicateRequest.operationId),1);assert.equal(await auditCount(duplicateRequest.operationId),1);
      assert.equal((await get(closingId)).closingRevision,before.closingRevision+1);
    });
    await t.test('reused IDs with different payload/resource are rejected; another actor cannot borrow a receipt',async()=>{
      assert.equal((await operation(ids.latte,{...duplicateRequest,quantity:8})).body.error.code,'OPERATION_ID_REUSED');
      assert.equal((await operation(ids.latte,duplicateRequest,'colleague')).body.error.code,'ENTRY_CONFLICT');
      const before=await get(closingId);
      assert.equal((await operation(ids.latte,bodyFor(before,ids.latte,8,publishOperation),'admin')).body.error.code,'OPERATION_ID_REUSED');
    });
    await t.test('discarding a successful acknowledgement and retrying does not apply again',async()=>{
      const before=await get(closingId),body=bodyFor(before,ids.latte,8);
      assert.equal((await operation(ids.latte,body)).status,200); // Simulate a client not persisting this ack.
      const replay=await operation(ids.latte,body);assert.equal(replay.status,200);
      assert.equal(await auditCount(body.operationId),1);assert.equal(await receiptCount(body.operationId),1);
      assert.equal((await get(closingId)).closingRevision,before.closingRevision+1);
    });
    await t.test('failure after real entry/audit SQL rolls back both and allows retry of the same ID',async()=>{
      const before=await get(closingId),body=bodyFor(before,ids.latte,9);failReceipt=body.operationId;
      const failed=await operation(ids.latte,body);assert.equal(failed.status,500);assert.equal(failed.body.error.code,'INTERNAL_ERROR');
      assert.equal(failed.body.error.message.includes('receipt-write'),false);
      const after=await get(closingId);assert.equal(after.closingRevision,before.closingRevision);assert.deepEqual(after.entries,before.entries);
      assert.equal(await receiptCount(body.operationId),0);assert.equal(await auditCount(body.operationId),0);
      assert.equal((await operation(ids.latte,body)).status,200);assert.equal(await auditCount(body.operationId),1);
      assert.deepEqual(internal,['INTERNAL_ERROR']);internal.length=0;
    });
    await t.test('ABA edits still conflict even when current quantity matches the old value again',async()=>{
      const before=await get(closingId),entry=before.entries.find(e=>e.menuId===ids.latte),stale=bodyFor(before,ids.latte,11);
      assert.equal((await operation(ids.latte,bodyFor(before,ids.latte,entry.quantity+1))).status,200);
      const changed=await get(closingId);assert.equal((await operation(ids.latte,bodyFor(changed,ids.latte,entry.quantity))).status,200);
      const result=await operation(ids.latte,stale,'colleague');assert.equal(result.status,409);assert.equal(result.body.error.code,'ENTRY_CONFLICT');
      assert.equal(result.body.error.details.current.quantity,entry.quantity);
    });
    await t.test('invalid quantities/fields/menu IDs cannot mutate entries or create receipts',async()=>{
      const before=await get(closingId);
      for(const quantity of [-1,0.5,'1',null,10000])assert.equal((await operation(ids.latte,bodyFor(before,ids.latte,quantity))).status,400);
      assert.equal((await operation(ids.latte,{...bodyFor(before,ids.latte,2),actorId:accounts.admin})).status,400);
      assert.equal((await operation(randomUUID(),{operationId:randomUUID(),generation:before.generation,expectedEntryRevision:0,quantity:2})).status,404);
      const after=await get(closingId);assert.equal(after.closingRevision,before.closingRevision);assert.deepEqual(after.entries,before.entries);
    });
    await t.test('same-quantity no-op is receipted without extra revision or audit',async()=>{
      const before=await get(closingId),quantity=before.entries.find(e=>e.menuId===ids.latte).quantity,body=bodyFor(before,ids.latte,quantity);
      assert.equal((await operation(ids.latte,body)).status,200);assert.equal(await receiptCount(body.operationId),1);assert.equal(await auditCount(body.operationId),0);
      assert.equal((await get(closingId)).closingRevision,before.closingRevision);
    });
    await t.test('consistent GET snapshot survives a concurrent edit between header and entry reads',async()=>{
      const before=await get(closingId);let reached,release;
      const atBarrier=new Promise(r=>reached=r),wait=new Promise(r=>release=r);readGate={id:closingId,armed:true,reached,release,wait};
      const pending=api('GET','/closings/'+closingId);
      try{
        await Promise.race([atBarrier,pending.then(()=>{throw Error('Read barrier not reached');})]);
        const body=bodyFor(before,ids.cappuccino,before.entries.find(e=>e.menuId===ids.cappuccino).quantity+1);
        assert.equal((await operation(ids.cappuccino,body,'colleague')).status,200);release();
        const held=await pending;assert.equal(held.status,200);assert.equal(held.body.data.closingRevision,before.closingRevision);assert.deepEqual(held.body.data.entries,before.entries);
        assert.equal((await get(closingId)).closingRevision,before.closingRevision+1);
      }finally{release();readGate=null;}
    });
    await t.test('state/generation guards reject new stale operations while old receipts remain acknowledgements only',async()=>{
      const before=await get(closingId),quantity=before.entries.find(e=>e.menuId===ids.latte).quantity;
      await pool.query("UPDATE ccs.closings SET state='SUBMITTED',submitted_by=$2,submitted_at=clock_timestamp(),submitted_actor=$3::jsonb WHERE id=$1",[closingId,accounts.employee,JSON.stringify({id:accounts.employee,username:prefix+'.employee',displayName:'QA employee'})]);
      assert.equal((await operation(ids.latte,bodyFor(before,ids.latte,quantity))).body.error.code,'CLOSING_STATE_CONFLICT');
      await pool.query("UPDATE ccs.closings SET state='DRAFT',generation=generation+1,submitted_by=NULL,submitted_at=NULL,submitted_actor=NULL WHERE id=$1",[closingId]);
      assert.equal((await operation(ids.latte,bodyFor(before,ids.latte,quantity))).body.error.code,'GENERATION_CONFLICT');
      const replay=await operation(ids.latte,duplicateRequest);assert.equal(replay.status,200);assert.deepEqual(replay.body.data,duplicateAck);
      assert.equal((await get(closingId)).generation,before.generation+1);
    });
    await t.test('catalog changes affect future closings while old recipes/names/results stay frozen',async()=>{
      const before=await get(closingId),next=structuredClone(catalog);next.menus[0].name='New cappuccino';next.menus[0].ingredientIds=[ids.coffee];next.menus[1].active=false;
      const published=await api('POST',`/outlets/${outlet}/catalog`,{operationId:randomUUID(),expectedRevision:1,catalog:next},'admin');assert.equal(published.status,200);
      const stale=await api('POST',`/outlets/${outlet}/catalog`,{operationId:randomUUID(),expectedRevision:1,catalog:next},'admin');assert.equal(stale.body.error.code,'CATALOG_CONFLICT');
      const old=await get(closingId);assert.deepEqual(old.catalog,before.catalog);assert.deepEqual(old.ingredientTotals,before.ingredientTotals);
      const created=await api('POST',`/outlets/${outlet}/closings`,{businessDate:'2026-10-23'});assert.equal(created.status,201);assert.equal(created.body.data.entries.length,1);
      const view=created.body.data;assert.equal((await operation(ids.cappuccino,bodyFor(view,ids.cappuccino,3),'employee',view.id)).status,200);
      const newer=await get(view.id);assert.equal(newer.ingredientTotals.length,1);assert.equal(newer.ingredientTotals[0].total,3);
      assert.equal(newer.catalog.menus[0].name,'New cappuccino');
    });
    await t.test('database constraints preserve history and reject cross-outlet catalog linkage',async()=>{
      await assert.rejects(()=>pool.query('UPDATE ccs.catalog_versions SET snapshot=snapshot WHERE id=$1',[initialCatalog.versionId]),{code:'23514'});
      await assert.rejects(()=>pool.query("UPDATE ccs.closings SET snapshot=jsonb_set(snapshot,'{menus,0,name}','\"Tampered\"') WHERE id=$1",[closingId]),{code:'23514'});
      await assert.rejects(()=>pool.query('DELETE FROM ccs.closing_audit WHERE closing_id=$1',[closingId]),{code:'23514'});
      await assert.rejects(()=>pool.query('UPDATE ccs.operation_receipts SET response=response WHERE outlet_id=$1',[outlet]),{code:'23514'});
      await assert.rejects(()=>pool.query(`INSERT INTO ccs.closings(id,outlet_id,business_date,catalog_version_id,snapshot,timezone,cutoff_minutes,created_by)
        SELECT $1,$2,business_date,catalog_version_id,snapshot,timezone,cutoff_minutes,created_by FROM ccs.closings WHERE id=$3`,[randomUUID(),otherOutlet,closingId]),{code:'23503'});
    });
    await t.test('business date validates strictly and outlet settings are frozen at creation',async()=>{
      for(const businessDate of ['2026-02-30','2026-1-01',null])assert.equal((await api('POST',`/outlets/${outlet}/closings`,{businessDate})).status,400);
      await pool.query("UPDATE ccs.outlets SET timezone='UTC',cutoff_minutes=240 WHERE id=$1",[outlet]);
      const next=await api('POST',`/outlets/${outlet}/closings`,{businessDate:'2026-10-24'});assert.equal(next.status,201);assert.equal(next.body.data.timezone,'UTC');
      assert.equal((await get(closingId)).timezone,'Asia/Jakarta');
    });
    await t.test('revoked or disabled users cannot replay receipts or access another outlet',async()=>{
      const foreignCatalog=randomUUID(),foreignClosing=randomUUID();
      await pool.query('INSERT INTO ccs.catalog_versions(id,outlet_id,revision,snapshot,created_by) SELECT $1,$2,1,snapshot,created_by FROM ccs.catalog_versions WHERE id=$3',[foreignCatalog,otherOutlet,initialCatalog.versionId]);
      await pool.query(`INSERT INTO ccs.closings(id,outlet_id,business_date,catalog_version_id,snapshot,timezone,cutoff_minutes,created_by)
        SELECT $1,$2,business_date,$3,snapshot,timezone,cutoff_minutes,created_by FROM ccs.closings WHERE id=$4`,[foreignClosing,otherOutlet,foreignCatalog,closingId]);
      await pool.query('INSERT INTO ccs.closing_entries(closing_id,menu_id) SELECT $1,menu_id FROM ccs.closing_entries WHERE closing_id=$2',[foreignClosing,closingId]);
      assert.equal((await api('GET',`/outlets/${otherOutlet}/catalog`)).status,403);
      assert.equal((await api('POST',`/outlets/${otherOutlet}/closings`,{businessDate:'2026-10-22'})).status,403);
      assert.equal((await api('GET','/closings/'+randomUUID())).status,403);
      assert.equal((await api('GET','/closings/'+foreignClosing)).status,403);
      assert.equal((await api('GET','/closings/'+foreignClosing+'/audit')).status,403);
      assert.equal((await operation(ids.latte,{operationId:randomUUID(),generation:1,expectedEntryRevision:0,quantity:9},'employee',foreignClosing)).status,403);
      const membership=`/outlets/${outlet}/memberships/${accounts.employee}`;
      assert.equal((await api('PUT',membership,{role:'EMPLOYEE',active:false},'admin')).status,200);
      assert.equal((await operation(ids.latte,duplicateRequest)).status,403);
      assert.equal((await api('GET','/closings/'+closingId+'/audit')).status,403);
      assert.equal((await api('PUT',membership,{role:'EMPLOYEE',active:true},'admin')).status,200);
      await pool.query('UPDATE ccs.accounts SET active=false WHERE id=$1',[accounts.employee]);
      assert.equal((await operation(ids.latte,duplicateRequest)).body.error.code,'ACCOUNT_DISABLED');
      await pool.query('UPDATE ccs.accounts SET active=true WHERE id=$1',[accounts.employee]);
    });
    await t.test('history and attribution audit cursor pages contain all scoped rows without duplicates',async()=>{
      const history=[];let before=null;
      do{const r=await api('GET',`/outlets/${outlet}/closings?limit=2${before?'&before='+before:''}`);assert.equal(r.status,200);history.push(...r.body.data.items);before=r.body.data.nextBefore;}while(before);
      const expected=(await pool.query('SELECT id FROM ccs.closings WHERE outlet_id=$1 ORDER BY business_date DESC',[outlet])).rows.map(r=>r.id);
      assert.deepEqual(history.map(r=>r.id),expected);
      const audit=[];let after=null;
      do{const r=await api('GET',`/closings/${closingId}/audit?limit=2${after?'&after='+after:''}`);assert.equal(r.status,200);audit.push(...r.body.data.items);after=r.body.data.nextAfter;}while(after);
      const expectedAudit=(await pool.query('SELECT id::text FROM ccs.closing_audit WHERE closing_id=$1 ORDER BY ccs.closing_audit.id',[closingId])).rows.map(r=>r.id);
      assert.deepEqual(audit.map(r=>r.id),expectedAudit);assert.ok(audit.every(r=>r.actor_id&&r.actor_name&&r.actor_username));
      assert.ok(audit.every((row,index)=>index===0||BigInt(row.id)>BigInt(audit[index-1].id)));
    });
    await t.test('synthetic bigint audit IDs cross text-sort boundaries without precision loss or missing pages',async()=>{
      const result=await api('POST',`/outlets/${outlet}/closings`,{businessDate:'2026-10-25'});assert.equal(result.status,201);
      const id=result.body.data.id,large=(1000000000000000000n+BigInt('0x'+randomUUID().replaceAll('-','').slice(0,12))).toString();
      // This separate fixture intentionally inserts two labelled synthetic CREATE
      // records to exercise numeric cursor boundaries, not lifecycle behavior.
      const columns='closing_id,outlet_id,actor_id,actor_username,actor_name,action,closing_revision,generation';
      await pool.query(`INSERT INTO ccs.closing_audit(${columns},reason) SELECT ${columns},'QA synthetic cursor fixture' FROM ccs.closing_audit WHERE closing_id=$1 AND reason IS NULL`,[id]);
      await pool.query(`INSERT INTO ccs.closing_audit(id,${columns},reason) OVERRIDING SYSTEM VALUE SELECT $1,${columns},'QA synthetic bigint cursor fixture' FROM ccs.closing_audit WHERE closing_id=$2 AND reason IS NULL`,[large,id]);
      const seen=[];let after=null;
      do{const r=await api('GET',`/closings/${id}/audit?limit=1${after?'&after='+after:''}`);assert.equal(r.status,200);seen.push(...r.body.data.items.map(row=>row.id));after=r.body.data.nextAfter;}while(after);
      const expected=(await pool.query('SELECT id::text FROM ccs.closing_audit WHERE closing_id=$1 ORDER BY ccs.closing_audit.id',[id])).rows.map(row=>row.id);
      assert.deepEqual(seen,expected);assert.equal(seen.length,3);assert.equal(seen.at(-1),large);
      assert.ok(seen.every((value,index)=>index===0||BigInt(value)>BigInt(seen[index-1])));
    });
    assert.deepEqual(internal,[]);
  }finally{readGate?.release();if(server?.listening)await new Promise(resolve=>server.close(resolve));await pool.end();}
});
