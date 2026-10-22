// Real PostgreSQL + real HTTP. New fixture namespace per run; no resets or operational target.
import test from 'node:test';
import assert from 'node:assert/strict';
import {createHash,randomUUID} from 'node:crypto';
import pg from 'pg';
import {request} from 'node:http';
import {configFromEnvironment,assertOwnedDatabase} from '../src/config.mjs';
import {migrate} from '../src/migrate.mjs';
import {createApp} from '../src/app.mjs';
import {hashPassword} from '../src/passwords.mjs';
import {LoginLimiter} from '../src/limiter.mjs';

test('CCS-02 real database/HTTP acceptance',async t=>{
  const config=configFromEnvironment();
  const pool=new pg.Pool({connectionString:config.databaseUrl,max:8,connectionTimeoutMillis:5000,statement_timeout:10000});
  let server,clock=Date.now();const internal=[];
  const prefix='qa'+Date.now(),password='QA Closing!2026';
  const ids={outlet:randomUUID(),foreignOutlet:randomUUID(),employee:randomUUID(),supervisor:randomUUID(),admin:randomUUID(),admin2:randomUUID()};
  const headers=token=>token?{Authorization:'Bearer '+token}:{};
  let base,tokens={};
  async function api(method,route,body,token,extra={}){
    const response=await fetch(base+'/api/v1'+route,{method,headers:{...headers(token),...(body!==undefined?{'Content-Type':'application/json'}:{}),...extra},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(10000)});
    return {status:response.status,body:await response.json(),headers:response.headers};
  }
  const login=(role)=>api('POST','/auth/login',{username:prefix+'.'+role,password});
  const rollWindow=()=>{clock+=61000;}; // Explicit limiter-clock simulation; DB expiry uses real server time.
  try{
    const identity=await assertOwnedDatabase(pool,config);assert.equal(identity.restricted,true);
    await migrate(pool,config);const second=await migrate(pool,config);assert.equal(second.alreadyApplied,true);
    const hash=await hashPassword(password);
    const client=await pool.connect();
    try{
      await client.query('BEGIN');
      await client.query('INSERT INTO ccs.outlets(id,name) VALUES ($1,$2),($3,$4)',[ids.outlet,'Kedai QA '+prefix,ids.foreignOutlet,'Outlet lain '+prefix]);
      for(const role of ['employee','supervisor','admin','admin2']){
        await client.query('INSERT INTO ccs.accounts(id,username,display_name,password_hash) VALUES ($1,$2,$3,$4)',[ids[role],prefix+'.'+role,'QA '+role,hash]);
        await client.query('INSERT INTO ccs.memberships(outlet_id,account_id,role) VALUES ($1,$2,$3)',[ids.outlet,ids[role],role.startsWith('admin')?'ADMIN':role.toUpperCase()]);
      }
      await client.query('COMMIT');
    }catch(e){await client.query('ROLLBACK');throw e;}finally{client.release();}
    server=await createApp({pool,limiter:new LoginLimiter({now:()=>clock}),onInternalError:e=>internal.push(e.code)});
    await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));base='http://127.0.0.1:'+server.address().port;

    await t.test('guard rejects wrong ownership before a migration can write',async()=>{
      await assert.rejects(()=>migrate(pool,{...config,instance:'f'.repeat(48)}),{message:'Refusing unowned QA database'});
      assert.equal((await pool.query('SELECT count(*)::int AS n FROM ccs.migrations')).rows[0].n,second.migrations.length);
    });
    await t.test('anonymous/unknown/wrong password are denied; private data never echoed',async()=>{
      assert.equal((await api('GET','/me')).status,401);
      const unknown=await api('POST','/auth/login',{username:prefix+'.missing',password});
      const wrong=await api('POST','/auth/login',{username:prefix+'.employee',password:'not correct'});
      assert.equal(unknown.status,401);assert.equal(wrong.status,401);assert.equal(unknown.body.error.code,wrong.body.error.code);
      assert.equal(JSON.stringify(wrong.body).includes('not correct'),false);
      assert.equal(Object.hasOwn(wrong.body,'password'),false);
      rollWindow();
      for(const role of ['employee','supervisor','admin','admin2']){
        const result=await login(role);assert.equal(result.status,200);tokens[role]=result.body.data.accessToken;
        assert.equal(typeof tokens[role],'string');assert.equal(tokens[role].length,64);
        assert.equal(result.headers.get('cache-control'),'no-store');assert.equal(result.headers.get('access-control-allow-origin'),null);
      }
    });
    await t.test('individual identity and membership are fetched from database, never from actor headers',async()=>{
      const me=await api('GET','/me',undefined,tokens.employee,{'X-Actor-Id':ids.admin,'X-Role':'ADMIN'});
      assert.equal(me.status,200);assert.equal(me.body.data.id,ids.employee);assert.equal(me.body.data.outlets.length,1);
      assert.equal(me.body.data.outlets[0].role,'EMPLOYEE');
      assert.equal(JSON.stringify(me.body).includes('password_hash'),false);
      assert.equal((await api('GET',`/outlets/${ids.outlet}`,undefined,tokens.employee)).status,200);
      assert.equal((await api('GET',`/outlets/${ids.foreignOutlet}`,undefined,tokens.admin)).status,403);
      assert.equal((await api('GET',`/outlets/${randomUUID()}`,undefined,tokens.admin)).status,403);
    });
    await t.test('outlet business date uses server time and assigned timezone',async()=>{
      const result=await api('GET',`/outlets/${ids.outlet}/business-date`,undefined,tokens.employee);
      assert.equal(result.status,200);assert.equal(result.body.data.timezone,'Asia/Jakarta');
      assert.match(result.body.data.businessDate,/^\d{4}-\d{2}-\d{2}$/);assert.ok(new Date(result.body.data.serverTime).getTime()>0);
    });
    await t.test('supervisor/employee cannot administer accounts or memberships',async()=>{
      for(const role of ['employee','supervisor']){
        assert.equal((await api('GET',`/outlets/${ids.outlet}/memberships`,undefined,tokens[role])).status,403);
        assert.equal((await api('POST',`/outlets/${ids.outlet}/accounts`,{username:'ignored'},tokens[role])).status,403);
        assert.equal((await api('PUT',`/outlets/${ids.outlet}/memberships/${ids.employee}`,{role:'ADMIN',active:true},tokens[role])).status,403);
      }
    });
    let created;
    await t.test('administrator creates an individual account with hashed password and scoped audit',async()=>{
      const result=await api('POST',`/outlets/${ids.outlet}/accounts`,{username:prefix+'.new',displayName:'Barista QA',password,role:'EMPLOYEE'},tokens.admin);
      assert.equal(result.status,201);created=result.body.data.id;
      assert.equal(JSON.stringify(result.body).includes(password),false);
      const {rows:[stored]}=await pool.query('SELECT password_hash FROM ccs.accounts WHERE id=$1',[created]);
      assert.equal(stored.password_hash.startsWith('scrypt$v1$'),true);assert.notEqual(stored.password_hash,password);
      const {rows:[audit]}=await pool.query("SELECT actor_id,subject_id FROM ccs.membership_audit WHERE outlet_id=$1 AND action='CREATE_ACCOUNT'",[ids.outlet]);
      assert.equal(audit.actor_id,ids.admin);assert.equal(audit.subject_id,created);
      const duplicate=await api('POST',`/outlets/${ids.outlet}/accounts`,{username:prefix+'.new',displayName:'Another',password,role:'EMPLOYEE'},tokens.admin);
      assert.equal(duplicate.status,409);assert.equal(duplicate.body.error.code,'USERNAME_UNAVAILABLE');
    });
    await t.test('strict payload/type/role validation and cross-outlet administration cannot mutate records',async()=>{
      const route=`/outlets/${ids.outlet}/memberships/${created}`;
      for(const body of [{role:'OWNER',active:true},{role:'ADMIN',active:'true'},{role:'ADMIN',active:true,actorId:ids.admin}]){
        assert.equal((await api('PUT',route,body,tokens.admin)).status,400);
      }
      assert.equal((await api('PUT',`/outlets/${ids.foreignOutlet}/memberships/${created}`,{role:'ADMIN',active:true},tokens.admin)).status,403);
      assert.equal((await api('PUT',`/outlets/${ids.outlet}/memberships/${randomUUID()}`,{role:'ADMIN',active:true},tokens.admin)).status,404);
      assert.equal((await pool.query('SELECT role FROM ccs.memberships WHERE account_id=$1',[created])).rows[0].role,'EMPLOYEE');
    });
    await t.test('malformed, wrong-content-type and oversized JSON are rejected without dropping socket',async()=>{
      const route=`/api/v1/outlets/${ids.outlet}/memberships/${created}`;
      async function raw(body,type,chunked=false){return new Promise((resolve,reject)=>{
        const req=request(base+route,{method:'PUT',headers:{...headers(tokens.admin),'Content-Type':type,
          ...(chunked?{}:{'Content-Length':Buffer.byteLength(body)})}},res=>{
          const chunks=[];res.on('data',b=>chunks.push(b));res.on('end',()=>resolve({status:res.statusCode,body:JSON.parse(Buffer.concat(chunks).toString())}));
        });req.on('error',reject);if(chunked){req.write(body.slice(0,8000));req.write(body.slice(8000));req.end();}else req.end(body);
      });}
      assert.equal((await raw('{bad','application/json')).status,400);
      assert.equal((await raw('{}','text/plain')).status,415);
      const large=JSON.stringify({payload:'x'.repeat(20000)});
      assert.equal((await raw(large,'application/json')).status,413);
      assert.equal((await raw(large,'application/json',true)).status,413);
      assert.equal((await pool.query('SELECT role FROM ccs.memberships WHERE account_id=$1',[created])).rows[0].role,'EMPLOYEE');
    });
    await t.test('membership changes take effect immediately for existing sessions and are audited',async()=>{
      const route=`/outlets/${ids.outlet}/memberships/${ids.employee}`;
      assert.equal((await api('PUT',route,{role:'SUPERVISOR',active:true},tokens.admin)).status,200);
      assert.equal((await api('GET','/me',undefined,tokens.employee)).body.data.outlets[0].role,'SUPERVISOR');
      assert.equal((await api('PUT',route,{role:'SUPERVISOR',active:false},tokens.admin)).status,200);
      assert.equal((await api('GET',`/outlets/${ids.outlet}`,undefined,tokens.employee)).status,403);
      const me=await api('GET','/me',undefined,tokens.employee);assert.equal(me.body.data.outlets.length,0);
      assert.equal((await api('PUT',route,{role:'EMPLOYEE',active:true},tokens.admin)).status,200);
      assert.equal((await pool.query("SELECT count(*)::int AS n FROM ccs.membership_audit WHERE outlet_id=$1 AND subject_id=$2 AND action='UPDATE_MEMBERSHIP'",[ids.outlet,ids.employee])).rows[0].n,3);
    });
    await t.test('disabled accounts and expired/revoked sessions are denied despite valid opaque token',async()=>{
      await pool.query('UPDATE ccs.accounts SET active=false WHERE id=$1',[ids.supervisor]);
      const disabled=await api('GET','/me',undefined,tokens.supervisor);assert.equal(disabled.status,403);assert.equal(disabled.body.error.code,'ACCOUNT_DISABLED');
      await pool.query("UPDATE ccs.sessions SET created_at=clock_timestamp()-interval '10 hours',expires_at=clock_timestamp()-interval '2 hours' WHERE account_id=$1",[ids.employee]);
      assert.equal((await api('GET','/me',undefined,tokens.employee)).status,401);
      assert.equal((await api('POST','/auth/logout',{},tokens.admin2)).status,200);
      assert.equal((await api('GET','/me',undefined,tokens.admin2)).status,401);
      const hash=createHash('sha256').update(tokens.admin).digest('hex');
      assert.equal((await pool.query('SELECT count(*)::int AS n FROM ccs.sessions WHERE token_hash=$1',[hash])).rows[0].n,1);
      assert.equal((await pool.query('SELECT count(*)::int AS n FROM ccs.sessions WHERE token_hash=$1',[tokens.admin])).rows[0].n,0);
    });
    await t.test('server restart preserves valid DB session and fixture state',async()=>{
      await new Promise(resolve=>server.close(resolve));
      server=await createApp({pool,limiter:new LoginLimiter({now:()=>clock}),onInternalError:e=>internal.push(e.code)});
      await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));base='http://127.0.0.1:'+server.address().port;
      assert.equal((await api('GET','/me',undefined,tokens.admin)).body.data.id,ids.admin);
    });
    await t.test('session cap is transactional across login windows and stores no raw token',async()=>{
      rollWindow();const sessions=[];
      for(let i=0;i<6;i++){if(i===4)rollWindow();const result=await login('employee');assert.equal(result.status,200);sessions.push(result.body.data.accessToken);}
      assert.equal((await api('GET','/me',undefined,sessions[0])).status,401);
      assert.equal((await api('GET','/me',undefined,sessions[5])).status,200);
      assert.equal((await pool.query('SELECT count(*)::int AS n FROM ccs.sessions WHERE account_id=$1 AND revoked_at IS NULL AND expires_at>clock_timestamp()',[ids.employee])).rows[0].n,5);
      rollWindow();
      const parallel=await Promise.all(Array.from({length:5},()=>login('employee')));
      for(const result of parallel)assert.equal(result.status,200);
      assert.equal((await api('GET','/me',undefined,sessions[5])).status,401);
      for(const result of parallel)assert.equal((await api('GET','/me',undefined,result.body.data.accessToken)).status,200);
      assert.equal((await pool.query('SELECT count(*)::int AS n FROM ccs.sessions WHERE account_id=$1 AND revoked_at IS NULL AND expires_at>clock_timestamp()',[ids.employee])).rows[0].n,5);
    });
    await t.test('real HTTP login throttle ignores spoofed forwarded-for and returns retry header',async()=>{
      rollWindow();for(let i=0;i<5;i++)assert.equal((await api('POST','/auth/login',{username:prefix+'.missing',password},undefined,{'X-Forwarded-For':'192.0.2.'+i})).status,401);
      const result=await api('POST','/auth/login',{username:prefix+'.missing',password},undefined,{'X-Forwarded-For':'192.0.2.250'});
      assert.equal(result.status,429);assert.equal(result.headers.get('retry-after'),'60');
      for(let i=0;i<4;i++)assert.equal((await api('POST','/auth/login',{username:prefix+'.miss'+i,password})).status,401);
      assert.equal((await api('POST','/auth/login',{username:prefix+'.last',password})).status,429);
      rollWindow();assert.equal((await login('admin2')).status,200);
    });
    let remainingAdmin;
    await t.test('concurrent administrator demotion leaves one active admin with atomic audit',async()=>{
      const login2=await login('admin2');assert.equal(login2.status,200);tokens.admin2=login2.body.data.accessToken;
      const results=await Promise.all(['admin','admin2'].map(role=>api('PUT',`/outlets/${ids.outlet}/memberships/${ids[role]}`,{role:'EMPLOYEE',active:true},tokens[role])));
      assert.deepEqual(results.map(r=>r.status).sort(),[200,409]);
      remainingAdmin=['admin','admin2'][results.findIndex(r=>r.status===409)];
      assert.equal((await pool.query("SELECT count(*)::int AS n FROM ccs.memberships WHERE outlet_id=$1 AND role='ADMIN' AND active",[ids.outlet])).rows[0].n,1);
      assert.equal((await pool.query("SELECT count(*)::int AS n FROM ccs.membership_audit WHERE outlet_id=$1 AND subject_id=ANY($2::uuid[])",[ids.outlet,[ids.admin,ids.admin2]])).rows[0].n,1);
    });
    await t.test('last-admin protection permits removing an already disabled administrator and rejects closing the last active one',async()=>{
      const response=await api('POST',`/outlets/${ids.outlet}/accounts`,{username:prefix+'.disabledadmin',displayName:'Disabled QA admin',password,role:'ADMIN'},tokens[remainingAdmin]);
      assert.equal(response.status,201);const id=response.body.data.id;
      await pool.query('UPDATE ccs.accounts SET active=false WHERE id=$1',[id]);
      assert.equal((await api('PUT',`/outlets/${ids.outlet}/memberships/${id}`,{role:'EMPLOYEE',active:false},tokens[remainingAdmin])).status,200);
      assert.equal((await api('PUT',`/outlets/${ids.outlet}/memberships/${ids[remainingAdmin]}`,{role:'ADMIN',active:false},tokens[remainingAdmin])).status,409);
      await pool.query('UPDATE ccs.outlets SET active=false WHERE id=$1',[ids.outlet]);
      assert.equal((await api('GET',`/outlets/${ids.outlet}`,undefined,tokens[remainingAdmin])).status,403);
      assert.equal((await api('GET','/me',undefined,tokens[remainingAdmin])).body.data.outlets.length,0);
      await pool.query('UPDATE ccs.outlets SET active=true WHERE id=$1',[ids.outlet]);
    });
    assert.deepEqual(internal,[]);
  }finally{
    if(server?.listening)await new Promise(resolve=>server.close(resolve));await pool.end();
  }
});
