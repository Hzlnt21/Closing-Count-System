// Recover only persisted database evidence after the fixture handle disappeared.
// No migration, seed, HTTP server, mutation, password hash or session query.
import {readFileSync,writeFileSync,existsSync} from 'node:fs';
import path from 'node:path';
import net from 'node:net';
import pg from 'pg';
import {configFromEnvironment,assertOwnedDatabase} from '../../../../Worktrees/Closing-Count-Portfolio/service/src/config.mjs';
const out='D:/Code/Documents/Career-Portfolio-2026/evidence/closing-count-pending-copy-r5';
const host=JSON.parse(readFileSync(out+'/verification.json','utf8').replace(/^\uFEFF/,''));
if(!host.passed||host.normalVerificationTests!==4||!host.primaryRoomFilesUnchanged||host.cleanupErrors.length)throw Error('Host acceptance/cleanup incomplete');
if(existsSync(out+'/database-after.json')||existsSync(out+'/admin-database-after.json'))throw Error('Do not overwrite a collected snapshot');
const noListener=await new Promise((resolve,reject)=>{const socket=net.connect({host:'127.0.0.1',port:4371});socket.setTimeout(3000);socket.on('connect',()=>{socket.destroy();reject(Error('Fixture listener still active'));});socket.on('timeout',()=>{socket.destroy();reject(Error('Listener state unknown'));});socket.on('error',e=>e.code==='ECONNREFUSED'?resolve(true):reject(Error('Listener state unknown')));});
const fixture=JSON.parse(readFileSync(out+'/fixture-identity.json','utf8'));const {outlet,adminOutlet}=fixture;
if(!fixture.ownedDatabase||fixture.fixturePurpose!=='pending-copy-r5'||![outlet,adminOutlet].every(v=>/^[a-f0-9-]{36}$/.test(v)))throw Error('Wrong fixture identity');
const secret=JSON.parse(readFileSync(path.join(process.env.LOCALAPPDATA,'PortfolioQA/secrets/closing-count-local-r1.json'),'utf8'));
const config=configFromEnvironment({CCS_ENVIRONMENT:'QA',CCS_INSTANCE:secret.instance,CCS_DATABASE_URL:`postgresql://ccs_portfolio_app:${secret.appPassword}@127.0.0.1:55436/closing_count_portfolio_qa`});
const pool=new pg.Pool({connectionString:config.databaseUrl,max:1,connectionTimeoutMillis:5000,statement_timeout:10000});
try {
 await assertOwnedDatabase(pool,config);const db=await pool.connect();
 try {
  await db.query('BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY');
  const entries=await db.query(`SELECT c.business_date::text,c.generation::text,c.revision::text,e.menu_id,e.quantity,e.revision::text AS entry_revision,e.updated_by FROM ccs.closings c JOIN ccs.closing_entries e ON e.closing_id=c.id WHERE c.outlet_id=$1 ORDER BY c.business_date,e.menu_id`,[outlet]);
  const receipts=await db.query(`SELECT c.business_date::text,r.actor_id,r.operation_id,r.response FROM ccs.operation_receipts r JOIN ccs.closings c ON c.id=(r.response->>'closingId')::uuid WHERE r.outlet_id=$1 ORDER BY c.business_date`,[outlet]);
  const audit=await db.query(`SELECT c.business_date::text,a.actor_id,a.operation_id,a.before_quantity,a.after_quantity FROM ccs.closing_audit a JOIN ccs.closings c ON c.id=a.closing_id WHERE a.outlet_id=$1 AND action='ENTRY_UPDATED' ORDER BY a.id`,[outlet]);
  const adminData={outletId:adminOutlet};
  for(const table of ['outlets','memberships','catalog_versions','operation_receipts','outlet_settings_audit','membership_audit','closings','closing_audit','closing_reports']) {
   const key=table==='outlets'?'id':'outlet_id';
   adminData[table]=(await db.query(`SELECT to_jsonb(x)-'fingerprint' AS value FROM ccs.${table} x WHERE ${key}=$1 ORDER BY to_jsonb(x)::text`,[adminOutlet])).rows.map(r=>r.value);
  }
  adminData.closing_entries=(await db.query('SELECT e.closing_id,e.menu_id,e.quantity,e.revision::text,e.updated_by FROM ccs.closing_entries e JOIN ccs.closings c ON c.id=e.closing_id WHERE c.outlet_id=$1 ORDER BY e.closing_id,e.menu_id',[adminOutlet])).rows;
  adminData.members=(await db.query('SELECT a.id,a.username,a.display_name,a.active,m.role,m.active AS member_active,m.revision::text FROM ccs.accounts a JOIN ccs.memberships m ON m.account_id=a.id WHERE m.outlet_id=$1 ORDER BY a.username',[adminOutlet])).rows;
  await db.query('COMMIT');
  writeFileSync(out+'/database-after.json',JSON.stringify({entries:entries.rows,receipts:receipts.rows,audit:audit.rows,internalErrorCount:null,telemetryComplete:false,fixturesRetained:true,collectedAfterFixtureExit:true},null,2));
  writeFileSync(out+'/admin-database-after.json',JSON.stringify(adminData,null,2));
  writeFileSync(out+'/database-collection.json',JSON.stringify({readOnlyTransaction:true,ownershipGuardPassed:true,fixturePurpose:fixture.fixturePurpose,outlet,adminOutlet,collectedAt:new Date().toISOString(),fixtureListenerAbsent:noListener,fixtureHandleMissing:true,inMemoryInternalErrorTelemetryAvailable:false,nativeNormalPhases:4,appDataReplayed:false,seedRerun:false,physicalPhoneAccessed:false},null,2));
  console.log(JSON.stringify({snapshotCollected:true,readOnly:true,adminReceipts:adminData.operation_receipts.length,internalErrorCountKnown:false}));
 } finally {db.release();}
} catch(e) {console.error('Read-only owned snapshot unavailable: '+(e.code&&/^[A-Z0-9_]+$/.test(e.code)?e.code:'CONTROLLED_FAILURE'));process.exitCode=1;}
finally {await pool.end();}
