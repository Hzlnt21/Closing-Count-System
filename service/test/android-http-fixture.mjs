// Dedicated owned PostgreSQL fixture, retained after tests. No operational configuration.
import {writeFileSync,mkdirSync,existsSync,readFileSync,unlinkSync} from 'node:fs';
import path from 'node:path';
import {randomUUID} from 'node:crypto';
import pg from 'pg';
import {configFromEnvironment,assertOwnedDatabase} from '../src/config.mjs';
import {migrate} from '../src/migrate.mjs';
import {createApp} from '../src/app.mjs';
import {hashPassword} from '../src/passwords.mjs';
import {LoginLimiter} from '../src/limiter.mjs';
import {catalogFixture} from './catalog-fixture.mjs';
import {normalizeCatalog} from '../src/catalog.mjs';
const config=configFromEnvironment();
const pool=new pg.Pool({connectionString:config.databaseUrl,max:4,connectionTimeoutMillis:5000,statement_timeout:10000});
const out=process.env.CCS_HTTP_EVIDENCE_DIR,fixtureFile=process.env.CCS_HTTP_FIXTURE_FILE;
if(!out||!fixtureFile||!path.isAbsolute(out)||!path.isAbsolute(fixtureFile))throw Error('Explicit absolute QA output paths required');
mkdirSync(out,{recursive:true});
let server,stopping=false,stopTimer;const internal=[],heldResponses=new Set();
const outlet=randomUUID(),admin=randomUUID(),a=randomUUID(),b=randomUUID(),supervisor=randomUUID(),prefix='qah'+Date.now().toString(36),password='QA Http Closing!2026';
try{
 await assertOwnedDatabase(pool,config);await migrate(pool,config);
 const hash=await hashPassword(password),catalog=normalizeCatalog(catalogFixture().catalog);
 const db=await pool.connect();try{
  await db.query('BEGIN');await db.query('INSERT INTO ccs.outlets(id,name) VALUES ($1,$2)',[outlet,'HTTP QA '+prefix]);
  for(const [id,suffix,role] of [[admin,'admin','ADMIN'],[a,'rani','EMPLOYEE'],[b,'dimas','EMPLOYEE'],[supervisor,'arif','SUPERVISOR']]){
   await db.query('INSERT INTO ccs.accounts(id,username,display_name,password_hash) VALUES ($1,$2,$3,$4)',[id,prefix+'.'+suffix,'Dummy '+suffix,hash]);
   await db.query('INSERT INTO ccs.memberships(outlet_id,account_id,role) VALUES ($1,$2,$3)',[outlet,id,role]);
  }
  if(process.env.CCS_TEAM_QA_EXTRA==='31')for(let i=0;i<31;i++){
   const id=randomUUID(),suffix='extra'+String(i).padStart(2,'0');
   await db.query('INSERT INTO ccs.accounts(id,username,display_name,password_hash) VALUES ($1,$2,$3,$4)',[id,prefix+'.'+suffix,'Dummy '+suffix,hash]);
   await db.query("INSERT INTO ccs.memberships(outlet_id,account_id,role) VALUES ($1,$2,'EMPLOYEE')",[outlet,id]);
  }
  await db.query('INSERT INTO ccs.catalog_versions(id,outlet_id,revision,snapshot,created_by) VALUES ($1,$2,1,$3::jsonb,$4)',[randomUUID(),outlet,JSON.stringify(catalog),admin]);
  await db.query('COMMIT');
 }catch(e){await db.query('ROLLBACK');throw e;}finally{db.release()}
 // Injected advancing limiter clock avoids test order/wall-clock waits; no rate-limit acceptance claim.
 let tick=0;
 server=await createApp({pool,limiter:new LoginLimiter({now:()=>Date.now()+(tick++)*61000}),onInternalError:e=>internal.push(e.requestId)});
 // Opt-in test boundary: hold one real committed operation's HTTP reply until the OS kills its client.
 // No production handler or database transaction is replaced; gate is absent in normal fixtures.
 if(process.env.CCS_HTTP_RECOVERY_GATE==='true'){
  const handlers=server.listeners('request');server.removeAllListeners('request');
  server.on('request',(req,res)=>{
   if(existsSync(fixtureFile+'.offline')){res.writeHead(503,{'content-type':'application/json'});res.end(JSON.stringify({error:{code:'QA_OFFLINE',message:'Owned QA transport unavailable'}}));return;}
   const hold=fixtureFile+'.hold-next';
   if(req.method==='POST'&&existsSync(hold)){
    const kind=readFileSync(hold,'utf8').trim();
    const matches=kind==='quantity'?/\/entries\/[^/]+\/operations$/.test(req.url):kind==='lifecycle'&&/\/(submit|return|lock|reopen)$/.test(req.url);
    if(matches){
     unlinkSync(hold);const end=res.end.bind(res);
     res.end=(...args)=>{
      if(res.statusCode<200||res.statusCode>=300)return end(...args);
      heldResponses.add(res);res.once('close',()=>heldResponses.delete(res));
      writeFileSync(fixtureFile+'.held',JSON.stringify({kind,url:req.url,status:res.statusCode,committedBeforeReply:true,at:new Date().toISOString()}));
      return res;
     };
    }
   }
   for(const handler of handlers)handler(req,res);
  });
 }
 await new Promise((resolve,reject)=>{server.once('error',reject);server.listen(4371,'127.0.0.1',resolve)});
 const fixture={origin:'http://10.0.2.2:4371',outlet,employee:prefix+'.rani',colleague:prefix+'.dimas',supervisor:prefix+'.arif',admin:prefix+'.admin',password};
 writeFileSync(fixtureFile,JSON.stringify(fixture,null,2));
 writeFileSync(out+'/fixture-identity.json',JSON.stringify({outlet,employee:a,colleague:b,supervisor,admin,extraTeamMembers:process.env.CCS_TEAM_QA_EXTRA==='31'?31:0,ownedDatabase:true,retained:true,limiterClockInjected:true},null,2));
 console.log(JSON.stringify({ready:true,port:4371,outlet,ownedDatabase:true,fixtureCredentialsPrinted:false}));
 process.stdin.resume();process.stdin.on('data',data=>{if(data.toString().trim()==='stop')stop().catch(()=>process.exitCode=1)});
 process.on('SIGINT',()=>stop());process.on('SIGTERM',()=>stop());
 stopTimer=setInterval(()=>{if(existsSync(fixtureFile+'.stop'))stop().catch(()=>process.exitCode=1)},1000);
 if(process.argv.includes('--smoke'))await stop();
}catch(e){console.error('Owned HTTP fixture failed: '+(e.code&&/^[A-Z0-9_]+$/.test(e.code)?e.code:'CONTROLLED_FAILURE'));await pool.end();process.exitCode=1;}
async function stop(){
 if(stopping)return;stopping=true;clearInterval(stopTimer);
 for(const response of heldResponses)response.destroy();
 if(server)await new Promise(resolve=>server.close(resolve));
 const entries=await pool.query(`SELECT c.business_date::text,c.generation::text,c.revision::text,e.menu_id,e.quantity,e.revision::text AS entry_revision,e.updated_by
   FROM ccs.closings c JOIN ccs.closing_entries e ON e.closing_id=c.id WHERE c.outlet_id=$1 ORDER BY c.business_date,e.menu_id`,[outlet]);
 const receipts=await pool.query(`SELECT c.business_date::text,r.actor_id,r.operation_id,r.response FROM ccs.operation_receipts r
   JOIN ccs.closings c ON c.id=(r.response->>'closingId')::uuid WHERE r.outlet_id=$1 ORDER BY c.business_date`,[outlet]);
 const audit=await pool.query(`SELECT c.business_date::text,a.actor_id,a.operation_id,a.before_quantity,a.after_quantity FROM ccs.closing_audit a
   JOIN ccs.closings c ON c.id=a.closing_id WHERE a.outlet_id=$1 AND action='ENTRY_UPDATED' ORDER BY a.id`,[outlet]);
 const settingsReceipts=await pool.query("SELECT operation_id,actor_id,response FROM ccs.operation_receipts WHERE outlet_id=$1 AND response ? 'settingsRevision'",[outlet]);
 const teamReceipts=await pool.query("SELECT operation_id,actor_id,response FROM ccs.operation_receipts WHERE outlet_id=$1 AND response->>'kind' IN ('CREATE_ACCOUNT','UPDATE_MEMBERSHIP')",[outlet]);
 writeFileSync(out+'/database-after.json',JSON.stringify({entries:entries.rows,receipts:receipts.rows,audit:audit.rows,settingsReceipts:settingsReceipts.rows,teamReceipts:teamReceipts.rows,internalErrorCount:internal.length,fixturesRetained:true},null,2));
 await pool.end();process.stdin.pause();console.log(JSON.stringify({stopped:true,internalErrors:internal.length,entries:entries.rowCount,receipts:receipts.rowCount,settingsReceipts:settingsReceipts.rowCount,teamReceipts:teamReceipts.rowCount,auditChanges:audit.rowCount}));
}
