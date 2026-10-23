// Dedicated owned PostgreSQL fixture, retained after tests. No operational configuration.
import {readFileSync,writeFileSync,mkdirSync,existsSync} from 'node:fs';
import path from 'node:path';
import {randomUUID} from 'node:crypto';
import pg from 'pg';
import {configFromEnvironment,assertOwnedDatabase} from '../../../../Worktrees/Closing-Count-Portfolio/service/src/config.mjs';
import {migrate} from '../../../../Worktrees/Closing-Count-Portfolio/service/src/migrate.mjs';
import {createApp} from '../../../../Worktrees/Closing-Count-Portfolio/service/src/app.mjs';
import {hashPassword} from '../../../../Worktrees/Closing-Count-Portfolio/service/src/passwords.mjs';
import {LoginLimiter} from '../../../../Worktrees/Closing-Count-Portfolio/service/src/limiter.mjs';
import {catalogFixture} from '../../../../Worktrees/Closing-Count-Portfolio/service/test/catalog-fixture.mjs';
import {normalizeCatalog} from '../../../../Worktrees/Closing-Count-Portfolio/service/src/catalog.mjs';
const secret=JSON.parse(readFileSync(path.join(process.env.LOCALAPPDATA,'PortfolioQA/secrets/closing-count-local-r1.json'),'utf8'));
const config=configFromEnvironment({CCS_ENVIRONMENT:'QA',CCS_INSTANCE:secret.instance,CCS_DATABASE_URL:`postgresql://ccs_portfolio_app:${secret.appPassword}@127.0.0.1:55436/closing_count_portfolio_qa`});
const pool=new pg.Pool({connectionString:config.databaseUrl,max:4,connectionTimeoutMillis:5000,statement_timeout:10000});
const out='D:/Code/Documents/Career-Portfolio-2026/evidence/closing-count-native-restart-r5';mkdirSync(out,{recursive:true});
let server,stopping=false,stopPoll;const internal=[];
const outlet=randomUUID(),adminOutlet=randomUUID(),backupAdmin=randomUUID(),admin=randomUUID(),a=randomUUID(),b=randomUUID(),prefix='qah'+Date.now().toString(36),password='QA Http Closing!2026';
try{
 await assertOwnedDatabase(pool,config);await migrate(pool,config);
 const hash=await hashPassword(password),catalog=normalizeCatalog(catalogFixture().catalog);
 const db=await pool.connect();try{
  await db.query('BEGIN');await db.query('INSERT INTO ccs.outlets(id,name) VALUES ($1,$2)',[outlet,'HTTP QA '+prefix]);
  for(const [id,suffix,role] of [[admin,'admin','ADMIN'],[a,'rani','EMPLOYEE'],[b,'dimas','EMPLOYEE']]){
   await db.query('INSERT INTO ccs.accounts(id,username,display_name,password_hash) VALUES ($1,$2,$3,$4)',[id,prefix+'.'+suffix,'Dummy '+suffix,hash]);
   await db.query('INSERT INTO ccs.memberships(outlet_id,account_id,role) VALUES ($1,$2,$3)',[outlet,id,role]);
  }
  await db.query('INSERT INTO ccs.catalog_versions(id,outlet_id,revision,snapshot,created_by) VALUES ($1,$2,1,$3::jsonb,$4)',[randomUUID(),outlet,JSON.stringify(catalog),admin]);
  await db.query('INSERT INTO ccs.outlets(id,name) VALUES ($1,$2)',[adminOutlet,'Admin recovery QA '+prefix]);
  await db.query('INSERT INTO ccs.accounts(id,username,display_name,password_hash) VALUES ($1,$2,$3,$4)',[backupAdmin,prefix+'.backupadmin','Dummy backupadmin',hash]);
  for(const [account,role]of [[a,'EMPLOYEE'],[b,'EMPLOYEE'],[admin,'ADMIN'],[backupAdmin,'ADMIN']])await db.query('INSERT INTO ccs.memberships(outlet_id,account_id,role) VALUES ($1,$2,$3)',[adminOutlet,account,role]);
  await db.query('INSERT INTO ccs.catalog_versions(id,outlet_id,revision,snapshot,created_by) VALUES ($1,$2,1,$3::jsonb,$4)',[randomUUID(),adminOutlet,JSON.stringify(catalog),admin]);
  await db.query('COMMIT');
 }catch(e){await db.query('ROLLBACK');throw e;}finally{db.release()}
 // Injected advancing limiter clock avoids test order/wall-clock waits; no rate-limit acceptance claim.
 let tick=0;
 server=await createApp({pool,limiter:new LoginLimiter({now:()=>Date.now()+(tick++)*61000}),onInternalError:e=>internal.push(e.requestId)});
 await new Promise((resolve,reject)=>{server.once('error',reject);server.listen(4371,'127.0.0.1',resolve)});
 const fixture={origin:'http://10.0.2.2:4371',outlet,employee:prefix+'.rani',colleague:prefix+'.dimas',admin:prefix+'.admin',adminOutlet,backupAdmin:prefix+'.backupadmin',password};
 writeFileSync('D:/Code/ToolCache/PortfolioQA/ccs-native-restart-r5-fixture.json',JSON.stringify(fixture,null,2));
 writeFileSync(out+'/fixture-identity.json',JSON.stringify({outlet,adminOutlet,backupAdmin,employee:a,colleague:b,admin,employeeUsername:prefix+'.rani',outletName:'HTTP QA '+prefix,catalog,ownedDatabase:true,retained:true,limiterClockInjected:true},null,2));
 stopPoll=setInterval(()=>{if(existsSync(out+'/fixture.stop'))stop().catch(()=>process.exitCode=1)},300);
 console.log(JSON.stringify({ready:true,port:4371,outlet,ownedDatabase:true,fixtureCredentialsPrinted:false}));
 process.stdin.resume();process.stdin.on('data',data=>{if(data.toString().trim()==='stop')stop().catch(()=>process.exitCode=1)});
 process.on('SIGINT',()=>stop());process.on('SIGTERM',()=>stop());
}catch(e){console.error('Owned HTTP fixture failed: '+(e.code&&/^[A-Z0-9_]+$/.test(e.code)?e.code:'CONTROLLED_FAILURE'));await pool.end();process.exitCode=1;}
async function stop(){
 if(stopping)return;stopping=true;clearInterval(stopPoll);
 if(server)await new Promise(resolve=>server.close(resolve));
 const entries=await pool.query(`SELECT c.business_date::text,c.generation::text,c.revision::text,e.menu_id,e.quantity,e.revision::text AS entry_revision,e.updated_by
   FROM ccs.closings c JOIN ccs.closing_entries e ON e.closing_id=c.id WHERE c.outlet_id=$1 ORDER BY c.business_date,e.menu_id`,[outlet]);
 const receipts=await pool.query(`SELECT c.business_date::text,r.actor_id,r.operation_id,r.response FROM ccs.operation_receipts r
   JOIN ccs.closings c ON c.id=(r.response->>'closingId')::uuid WHERE r.outlet_id=$1 ORDER BY c.business_date`,[outlet]);
 const audit=await pool.query(`SELECT c.business_date::text,a.actor_id,a.operation_id,a.before_quantity,a.after_quantity FROM ccs.closing_audit a
   JOIN ccs.closings c ON c.id=a.closing_id WHERE a.outlet_id=$1 AND action='ENTRY_UPDATED' ORDER BY a.id`,[outlet]);
 writeFileSync(out+'/database-after.json',JSON.stringify({entries:entries.rows,receipts:receipts.rows,audit:audit.rows,internalErrorCount:internal.length,fixturesRetained:true},null,2));
 const adminData={outletId:adminOutlet};
 for(const table of ['outlets','memberships','catalog_versions','operation_receipts','outlet_settings_audit','membership_audit','closings','closing_audit']){
  const key=table==='outlets'?'id':'outlet_id';
  adminData[table]=(await pool.query(`SELECT to_jsonb(x)-'fingerprint' AS value FROM ccs.${table} x WHERE ${key}=$1 ORDER BY to_jsonb(x)::text`,[adminOutlet])).rows.map(r=>r.value);
 }
 adminData.members=(await pool.query('SELECT a.id,a.username,a.display_name,a.active,m.role,m.active AS member_active,m.revision::text FROM ccs.accounts a JOIN ccs.memberships m ON m.account_id=a.id WHERE m.outlet_id=$1 ORDER BY a.username',[adminOutlet])).rows;
 // Audit and public receipts contain no password/session fields. Never select account password hashes.
 writeFileSync(out+'/admin-database-after.json',JSON.stringify(adminData,null,2));

 await pool.end();process.stdin.pause();console.log(JSON.stringify({stopped:true,internalErrors:internal.length,entries:entries.rowCount,receipts:receipts.rowCount,auditChanges:audit.rowCount}));
}
