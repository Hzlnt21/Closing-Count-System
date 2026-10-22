import test from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import pg from 'pg';
import {configFromEnvironment,assertOwnedDatabase} from '../src/config.mjs';
import {migrate} from '../src/migrate.mjs';
import {createApp} from '../src/app.mjs';
import {hashPassword} from '../src/passwords.mjs';
import {catalogFixture} from './catalog-fixture.mjs';

test('CCS-04 real lifecycle and immutable reports',async t=>{
  const config=configFromEnvironment(),pool=new pg.Pool({connectionString:config.databaseUrl,max:8,connectionTimeoutMillis:5000,statement_timeout:10000});
  const prefix='qa'+Date.now()+randomUUID().slice(0,6),outlet=randomUUID(),foreignOutlet=randomUUID();
  const accounts=Object.fromEntries(['employee','supervisor','admin'].map(role=>[role,randomUUID()])),tokens={},internal=[];
  const {catalog,ids}=catalogFixture();let base,server,dateCounter=0,mainId,firstReport,firstSubmit,firstLock,firstReopen,failReport=null,failReceipt=null,waitingOperation=null;
  const observedPool={query:pool.query.bind(pool),connect:async()=>{
    const c=await pool.connect();return {release:()=>c.release(),query:async(sql,values)=>{
      if(waitingOperation&&sql.startsWith('SELECT pg_advisory_xact_lock')&&values[0].endsWith(waitingOperation.id))waitingOperation.reached();
      if(failReport&&sql.startsWith('INSERT INTO ccs.closing_reports')&&values[1]===failReport){failReport=null;throw Error('QA report insert failure');}
      if(failReceipt&&sql.startsWith('INSERT INTO ccs.operation_receipts')&&values[2]===failReceipt){failReceipt=null;throw Error('QA receipt failure after report');}
      return c.query(sql,values);
    }};
  }};
  async function api(method,path,body,role='employee'){
    const r=await fetch(base+'/api/v1'+path,{method,headers:{...(tokens[role]?{Authorization:'Bearer '+tokens[role]}:{}),...(body!==undefined?{'Content-Type':'application/json'}:{})},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(10000)});
    return {status:r.status,body:await r.json()};
  }
  async function view(id){const r=await api('GET','/closings/'+id);assert.equal(r.status,200);return r.body.data;}
  async function makeClosing(){const businessDate=new Date(Date.UTC(2026,10,++dateCounter)).toISOString().slice(0,10);const r=await api('POST',`/outlets/${outlet}/closings`,{businessDate});assert.equal(r.status,201);return r.body.data;}
  const input=(v,action,extra={})=>({operationId:randomUUID(),generation:v.generation,expectedRevision:v.closingRevision,deviceSyncState:'SYNCED',...(['return','reopen'].includes(action)?{reason:'QA count correction'}:{}),...extra});
  const act=(id,action,body,role='supervisor')=>api('POST',`/closings/${id}/${action}`,body,role);
  const edit=(v,menuId,quantity,role='employee')=>api('POST',`/closings/${v.id}/entries/${menuId}/operations`,{operationId:randomUUID(),generation:v.generation,expectedEntryRevision:v.entries.find(e=>e.menuId===menuId).entryRevision,quantity},role);
  const reports=async id=>(await api('GET',`/closings/${id}/reports`)).body.data;
  const countReports=async id=>(await pool.query('SELECT count(*)::int AS n FROM ccs.closing_reports WHERE closing_id=$1',[id])).rows[0].n;
  const countOp=async(id,op)=>(await pool.query('SELECT count(*)::int AS n FROM ccs.closing_audit WHERE closing_id=$1 AND operation_id=$2',[id,op])).rows[0].n;
  const submit=async id=>{const v=await view(id),b=input(v,'submit');const r=await act(id,'submit',b,'employee');assert.equal(r.status,200);return {body:b,result:r.body.data};};
  async function login(role){const r=await api('POST','/auth/login',{username:prefix+'.'+role,password:'QA Closing!2026'},'anonymous');assert.equal(r.status,200);tokens[role]=r.body.data.accessToken;}
  try{
    await assertOwnedDatabase(pool,config);await migrate(pool,config);
    const passwordHash=await hashPassword('QA Closing!2026'),c=await pool.connect();
    try{await c.query('BEGIN');await c.query('INSERT INTO ccs.outlets(id,name) VALUES ($1,$2),($3,$4)',[outlet,'Lifecycle '+prefix,foreignOutlet,'Foreign '+prefix]);
      for(const role of Object.keys(accounts)){
        await c.query('INSERT INTO ccs.accounts(id,username,display_name,password_hash) VALUES ($1,$2,$3,$4)',[accounts[role],prefix+'.'+role,'QA '+role,passwordHash]);
        await c.query('INSERT INTO ccs.memberships(outlet_id,account_id,role) VALUES ($1,$2,$3)',[outlet,accounts[role],role.toUpperCase()]);
      }await c.query('COMMIT');
    }catch(e){await c.query('ROLLBACK');throw e;}finally{c.release();}
    server=await createApp({pool:observedPool,onInternalError:e=>internal.push(e.code)});await new Promise(r=>server.listen(0,'127.0.0.1',r));base='http://127.0.0.1:'+server.address().port;
    for(const role of Object.keys(accounts))await login(role);
    const published=await api('POST',`/outlets/${outlet}/catalog`,{operationId:randomUUID(),expectedRevision:0,catalog},'admin');assert.equal(published.status,200);
    await t.test('employee submits exact synced revision and server preserves submitter/time',async()=>{
      const v=await makeClosing();mainId=v.id;assert.equal((await edit(v,ids.cappuccino,2)).status,200);assert.equal((await edit(await view(mainId),ids.latte,1)).status,200);
      firstSubmit=await submit(mainId);assert.equal(firstSubmit.result.state,'SUBMITTED');assert.equal(firstSubmit.result.submittedBy.id,accounts.employee);assert.ok(firstSubmit.result.submittedAt);
      const submitted=await view(mainId);assert.equal(submitted.closingRevision,3);assert.equal(submitted.lockedBy,null);
      assert.equal((await edit(submitted,ids.latte,3)).body.error.code,'CLOSING_STATE_CONFLICT');
    });
    await t.test('employee cannot review, lock, return or reopen even with spoofed reviewer fields',async()=>{
      const v=await view(mainId);
      for(const action of ['lock','return','reopen'])assert.equal((await act(mainId,action,input(v,action),'employee')).status,403);
      assert.equal((await act(mainId,'lock',{...input(v,'lock'),reviewerId:accounts.admin})).status,400);
      assert.equal((await act(mainId,'lock',{...input(v,'lock'),ingredientTotals:[]})).status,400);assert.equal(await countReports(mainId),0);
    });
    await t.test('supervisor locks server-calculated data and saves one immutable generation report',async()=>{
      const v=await view(mainId),body=input(v,'lock'),r=await act(mainId,'lock',body);assert.equal(r.status,200);firstLock={body,result:r.body.data};
      assert.equal(r.body.data.state,'LOCKED');assert.equal(r.body.data.lockedBy.id,accounts.supervisor);assert.equal(r.body.data.closingRevision,4);assert.equal(await countReports(mainId),1);
      const report=await api('GET',`/closings/${mainId}/reports/1`);assert.equal(report.status,200);firstReport=report.body.data;
      assert.equal(firstReport.reviewedBy.id,accounts.supervisor);assert.equal(firstReport.closing.submittedBy.id,accounts.employee);assert.equal(firstReport.closing.state,'LOCKED');
      assert.deepEqual(Object.fromEntries(firstReport.closing.ingredientTotals.map(i=>[i.name,i.total])),{'Biji kopi':3,'Fresh milk':3,'Powder cokelat':2});
      assert.equal(firstReport.id,r.body.data.report.id);assert.equal(firstReport.reviewedAt,r.body.data.lockedAt);
    });
    await t.test('duplicate lock/submit receipts remain historical acknowledgements without duplicate reports',async()=>{
      const replay=await act(mainId,'lock',firstLock.body);assert.equal(replay.status,200);assert.deepEqual(replay.body.data,firstLock.result);
      const submitReplay=await act(mainId,'submit',firstSubmit.body,'employee');assert.equal(submitReplay.status,200);assert.deepEqual(submitReplay.body.data,firstSubmit.result);
      assert.equal((await view(mainId)).state,'LOCKED');assert.equal(await countReports(mainId),1);assert.equal(await countOp(mainId,firstLock.body.operationId),1);
      assert.equal((await act(mainId,'lock',input(await view(mainId),'lock'))).body.error.code,'CLOSING_STATE_CONFLICT');
    });
    await t.test('reopen requires reason and increments generation without altering the previous report',async()=>{
      const before=await view(mainId);
      for(const reason of ['', '  ',null,12,'x'.repeat(501)])assert.equal((await act(mainId,'reopen',input(before,'reopen',{reason}))).status,400);
      const body=input(before,'reopen',{reason:'  Actual count correction  '}),r=await act(mainId,'reopen',body);assert.equal(r.status,200);firstReopen={body,result:r.body.data};
      assert.equal(r.body.data.generation,2);assert.equal(r.body.data.state,'DRAFT');assert.equal(r.body.data.submittedAt,null);assert.equal(r.body.data.lockedAt,null);
      const old=(await api('GET',`/closings/${mainId}/reports/1`)).body.data;assert.deepEqual(old,firstReport);
      assert.equal((await edit(before,ids.cappuccino,3)).body.error.code,'GENERATION_CONFLICT');
      const audit=(await pool.query("SELECT reason FROM ccs.closing_audit WHERE closing_id=$1 AND action='REOPEN'",[mainId])).rows[0];assert.equal(audit.reason,'Actual count correction');
    });
    await t.test('second lock creates a new report while recipe/name/reviewer history stays unchanged',async()=>{
      const changedCatalog=structuredClone(catalog);changedCatalog.menus[0].name='New master name';changedCatalog.menus[0].ingredientIds=[ids.coffee];
      assert.equal((await api('POST',`/outlets/${outlet}/catalog`,{operationId:randomUUID(),expectedRevision:1,catalog:changedCatalog},'admin')).status,200);
      await pool.query("UPDATE ccs.accounts SET display_name='Renamed reviewer' WHERE id=$1",[accounts.supervisor]);
      const draft=await view(mainId);assert.equal((await edit(draft,ids.cappuccino,3)).status,200);await submit(mainId);
      const v=await view(mainId),r=await act(mainId,'lock',input(v,'lock'),'admin');assert.equal(r.status,200);assert.equal(await countReports(mainId),2);
      const latest=(await api('GET',`/closings/${mainId}/reports/2`)).body.data;
      assert.equal(latest.reviewedBy.id,accounts.admin);assert.equal(latest.closing.catalog.menus.find(m=>m.id===ids.cappuccino).name,'Cappuccino');
      assert.deepEqual(Object.fromEntries(latest.closing.ingredientTotals.map(i=>[i.name,i.total])),{'Biji kopi':4,'Fresh milk':4,'Powder cokelat':3});
      assert.deepEqual((await api('GET',`/closings/${mainId}/reports/1`)).body.data,firstReport);
      assert.equal((await act(mainId,'reopen',firstReopen.body)).status,200);assert.equal((await view(mainId)).state,'LOCKED');assert.equal((await view(mainId)).generation,2);
    });
    await t.test('submitted return starts a new generation and preserves reason/audit without a fake report',async()=>{
      const v=await makeClosing();await submit(v.id);const before=await view(v.id);
      const r=await act(v.id,'return',input(before,'return',{reason:'Review requires count verification'}));assert.equal(r.status,200);assert.equal(r.body.data.generation,2);assert.equal(await countReports(v.id),0);
      const stale=await act(v.id,'submit',input(before,'submit'),'employee');assert.equal(stale.body.error.code,'GENERATION_CONFLICT');
      await submit(v.id);const locked=await act(v.id,'lock',input(await view(v.id),'lock'));assert.equal(locked.status,200);
      assert.equal((await api('GET',`/closings/${v.id}/reports/1`)).status,404);assert.equal((await reports(v.id)).items[0].generation,2);
    });
    await t.test('local pending/conflict declaration and stale revision cannot submit or lock',async()=>{
      const v=await makeClosing();
      for(const deviceSyncState of ['PENDING','CONFLICT','SYNCING','AUTH_REQUIRED'])assert.equal((await act(v.id,'submit',input(v,'submit',{deviceSyncState}),'employee')).body.error.code,'LOCAL_CHANGES_PENDING');
      assert.equal((await edit(v,ids.cappuccino,1)).status,200);
      assert.equal((await act(v.id,'submit',input(v,'submit'),'employee')).body.error.code,'REVISION_CONFLICT');
      await submit(v.id);const submitted=await view(v.id);
      assert.equal((await act(v.id,'lock',input(submitted,'lock',{expectedRevision:submitted.closingRevision-1}))).body.error.code,'REVISION_CONFLICT');
      assert.equal(await countReports(v.id),0);
    });
    await t.test('concurrent edit and submit cannot both accept the same stale global revision',async()=>{
      const v=await makeClosing(),responses=await Promise.all([edit(v,ids.cappuccino,2),act(v.id,'submit',input(v,'submit'),'employee')]);
      assert.deepEqual(responses.map(r=>r.status).sort(),[200,409]);const final=await view(v.id);
      if(final.state==='SUBMITTED')assert.equal(final.entries.find(e=>e.menuId===ids.cappuccino).quantity,0);
      else{assert.equal(final.state,'DRAFT');assert.equal(final.entries.find(e=>e.menuId===ids.cappuccino).quantity,2);}
    });
    await t.test('simultaneous different lock operations produce one report and one accepted review',async()=>{
      const v=await makeClosing();await submit(v.id);const ready=await view(v.id),responses=await Promise.all([act(v.id,'lock',input(ready,'lock')),act(v.id,'lock',input(ready,'lock'),'admin')]);
      assert.deepEqual(responses.map(r=>r.status).sort(),[200,409]);assert.equal(await countReports(v.id),1);
      assert.equal(responses.find(r=>r.status===409).body.error.code,'REVISION_CONFLICT');
    });
    await t.test('simultaneous identical lock retry returns one review/report/receipt',async()=>{
      const v=await makeClosing();await submit(v.id);const body=input(await view(v.id),'lock'),responses=await Promise.all([act(v.id,'lock',body),act(v.id,'lock',body)]);
      for(const r of responses)assert.equal(r.status,200);assert.deepEqual(responses[0].body.data,responses[1].body.data);assert.equal(await countReports(v.id),1);assert.equal(await countOp(v.id,body.operationId),1);
    });
    await t.test('return versus lock serializes to one accepted outcome',async()=>{
      const v=await makeClosing();await submit(v.id);const ready=await view(v.id),responses=await Promise.all([act(v.id,'return',input(ready,'return')),act(v.id,'lock',input(ready,'lock'),'admin')]);
      assert.deepEqual(responses.map(r=>r.status).sort(),[200,409]);const final=await view(v.id);
      assert.ok(['GENERATION_CONFLICT','REVISION_CONFLICT'].includes(responses.find(r=>r.status===409).body.error.code));
      assert.equal(await countReports(v.id),final.state==='LOCKED'?1:0);
    });
    await t.test('report insert failure rolls back lock metadata before a safe retry',async()=>{
      const v=await makeClosing();await submit(v.id);const before=await view(v.id),body=input(before,'lock');failReport=v.id;
      const failed=await act(v.id,'lock',body);assert.equal(failed.status,500);assert.equal((await view(v.id)).state,'SUBMITTED');assert.equal((await view(v.id)).closingRevision,before.closingRevision);
      assert.equal(await countReports(v.id),0);assert.equal(await countOp(v.id,body.operationId),0);
      assert.equal((await act(v.id,'lock',body)).status,200);assert.equal(await countReports(v.id),1);assert.deepEqual(internal,['INTERNAL_ERROR']);internal.length=0;
    });
    await t.test('receipt failure after report/audit SQL rolls back all and same operation can retry',async()=>{
      const v=await makeClosing();await submit(v.id);const before=await view(v.id),body=input(before,'lock');failReceipt=body.operationId;
      assert.equal((await act(v.id,'lock',body)).status,500);assert.equal((await view(v.id)).state,'SUBMITTED');assert.equal(await countReports(v.id),0);assert.equal(await countOp(v.id,body.operationId),0);
      assert.equal((await act(v.id,'lock',body)).status,200);assert.equal(await countReports(v.id),1);assert.deepEqual(internal,['INTERNAL_ERROR']);internal.length=0;
    });
    await t.test('queued review rechecks membership after initial authorization',async()=>{
      const v=await makeClosing();await submit(v.id);const body=input(await view(v.id),'lock'),holder=await pool.connect();let reached;
      const seen=new Promise(r=>reached=r);waitingOperation={id:body.operationId,reached};
      try{
        await holder.query('BEGIN');await holder.query('SELECT pg_advisory_xact_lock(hashtextextended($1,0))',['ccs-operation:'+outlet+':'+accounts.supervisor+':'+body.operationId]);
        const pending=act(v.id,'lock',body);await Promise.race([seen,pending.then(()=>{throw Error('Operation queue barrier not reached');})]);
        assert.equal((await api('PUT',`/outlets/${outlet}/memberships/${accounts.supervisor}`,{role:'EMPLOYEE',active:true},'admin')).status,200);
        await holder.query('COMMIT');assert.equal((await pending).status,403);assert.equal(await countReports(v.id),0);
      }finally{waitingOperation=null;await holder.query('ROLLBACK').catch(()=>{});holder.release();await api('PUT',`/outlets/${outlet}/memberships/${accounts.supervisor}`,{role:'SUPERVISOR',active:true},'admin');}
    });
    await t.test('queued review also rechecks a session logged out while waiting',async()=>{
      const v=await makeClosing();await submit(v.id);const body=input(await view(v.id),'lock'),holder=await pool.connect();let reached;
      const seen=new Promise(r=>reached=r);waitingOperation={id:body.operationId,reached};
      try{
        await holder.query('BEGIN');await holder.query('SELECT pg_advisory_xact_lock(hashtextextended($1,0))',['ccs-operation:'+outlet+':'+accounts.supervisor+':'+body.operationId]);
        const pending=act(v.id,'lock',body);await Promise.race([seen,pending.then(()=>{throw Error('Operation queue barrier not reached');})]);
        assert.equal((await api('POST','/auth/logout',{},'supervisor')).status,200);await holder.query('COMMIT');assert.equal((await pending).status,401);assert.equal(await countReports(v.id),0);
      }finally{waitingOperation=null;await holder.query('ROLLBACK').catch(()=>{});holder.release();await login('supervisor');}
    });
    await t.test('report pages are generation-descending, scoped and do not include full payloads',async()=>{
      const first=await api('GET',`/closings/${mainId}/reports?limit=1`);assert.equal(first.status,200);assert.equal(first.body.data.items[0].generation,2);assert.equal(first.body.data.nextBeforeGeneration,2);assert.equal(Object.hasOwn(first.body.data.items[0],'closing'),false);
      const next=await api('GET',`/closings/${mainId}/reports?limit=1&beforeGeneration=2`);assert.equal(next.body.data.items[0].generation,1);assert.equal(next.body.data.nextBeforeGeneration,null);
      for(const suffix of ['0','-1','1.5','9007199254740992'])assert.equal((await api('GET',`/closings/${mainId}/reports/${suffix}`)).status,400);
      assert.equal((await api('GET',`/closings/${mainId}/reports/99`)).status,404);
    });
    await t.test('database enforces immutable reports and consistent state/reviewer metadata',async()=>{
      await assert.rejects(()=>pool.query('UPDATE ccs.closing_reports SET snapshot=snapshot WHERE closing_id=$1',[mainId]),{code:'23514'});
      await assert.rejects(()=>pool.query('DELETE FROM ccs.closing_reports WHERE closing_id=$1',[mainId]),{code:'23514'});
      const v=await makeClosing();await assert.rejects(()=>pool.query("UPDATE ccs.closings SET state='SUBMITTED' WHERE id=$1",[v.id]),{code:'23514'});
      await assert.rejects(()=>pool.query('UPDATE ccs.closings SET locked_actor=$2::jsonb WHERE id=$1',[mainId,JSON.stringify({id:accounts.employee})]),{code:'23514'});
      assert.deepEqual((await api('GET',`/closings/${mainId}/reports/1`)).body.data,firstReport);
    });
    await t.test('reused lifecycle IDs with altered reason/kind cannot apply another action',async()=>{
      const v=await makeClosing(),submitted=input(v,'submit');assert.equal((await act(v.id,'submit',submitted)).status,200);const ready=await view(v.id);
      assert.equal((await act(v.id,'lock',{...input(ready,'lock'),operationId:submitted.operationId})).body.error.code,'OPERATION_ID_REUSED');
      assert.equal((await act(v.id,'submit',{...submitted,expectedRevision:ready.closingRevision})).body.error.code,'OPERATION_ID_REUSED');
      const body=input(ready,'return',{reason:'First reason'});assert.equal((await act(v.id,'return',body)).status,200);
      assert.equal((await act(v.id,'return',{...body,reason:'Different reason'})).body.error.code,'OPERATION_ID_REUSED');
    });
    await t.test('report and lifecycle routes deny foreign outlet and revoked membership without exposing history',async()=>{
      const foreignCatalog=randomUUID(),foreignClosing=randomUUID();
      await pool.query('INSERT INTO ccs.catalog_versions(id,outlet_id,revision,snapshot,created_by) SELECT $1,$2,1,snapshot,created_by FROM ccs.catalog_versions WHERE outlet_id=$3 ORDER BY revision LIMIT 1',[foreignCatalog,foreignOutlet,outlet]);
      await pool.query(`INSERT INTO ccs.closings(id,outlet_id,business_date,catalog_version_id,snapshot,timezone,cutoff_minutes,created_by)
        SELECT $1,$2,business_date,$3,snapshot,timezone,cutoff_minutes,created_by FROM ccs.closings WHERE id=$4`,[foreignClosing,foreignOutlet,foreignCatalog,mainId]);
      assert.equal((await api('GET',`/closings/${foreignClosing}/reports`)).status,403);assert.equal((await api('GET',`/closings/${foreignClosing}/reports/1`)).status,403);
      assert.equal((await act(foreignClosing,'submit',input({generation:1,closingRevision:0},'submit'),'employee')).status,403);
      assert.equal((await api('PUT',`/outlets/${outlet}/memberships/${accounts.supervisor}`,{role:'EMPLOYEE',active:true},'admin')).status,200);
      assert.equal((await act(mainId,'lock',firstLock.body)).status,403);
      assert.equal((await api('GET',`/closings/${mainId}/reports/1`,undefined,'supervisor')).status,200);
      assert.equal((await api('PUT',`/outlets/${outlet}/memberships/${accounts.supervisor}`,{role:'EMPLOYEE',active:false},'admin')).status,200);
      assert.equal((await api('GET',`/closings/${mainId}/reports/1`,undefined,'supervisor')).status,403);
    });
    assert.deepEqual(internal,[]);
  }finally{if(server?.listening)await new Promise(r=>server.close(r));await pool.end();}
});
