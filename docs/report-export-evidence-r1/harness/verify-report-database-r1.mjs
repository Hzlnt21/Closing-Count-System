import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {configFromEnvironment,assertOwnedDatabase} from 'file:///D:/Code/Worktrees/Closing-Count-Portfolio/service/src/config.mjs';
const require=createRequire('D:/Code/Worktrees/Closing-Count-Portfolio/service/package.json');
const {Pool}=require('pg');
const root='D:/Code/Documents/Career-Portfolio-2026/evidence/closing-count-report-export-r1';
const read=p=>JSON.parse(fs.readFileSync(p,'utf8').replace(/^\uFEFF/,''));
const secret=read(path.join(process.env.LOCALAPPDATA,'PortfolioQA/secrets/closing-count-local-r1.json'));
const config=configFromEnvironment({...process.env,CCS_ENVIRONMENT:'QA',CCS_INSTANCE:secret.instance,
 CCS_DATABASE_URL:`postgresql://ccs_portfolio_app:${secret.appPassword}@127.0.0.1:55436/closing_count_portfolio_qa`});
const pool=new Pool({connectionString:config.databaseUrl,max:1,connectionTimeoutMillis:5000,statement_timeout:10000});
try{
 await assertOwnedDatabase(pool,config);
 const db=await pool.connect();
 try{
  await db.query('BEGIN READ ONLY');
  const groups=[];
  for(const run of ['http-final','light-stable-final','dark-stable-final']){
   const folder=path.join(root,run),fixture=read(path.join(folder,'fixture/fixture-identity.json'));
   assert.equal(fixture.ownedDatabase,true);
   const source=read(path.join(folder,run==='http-final'?'report-export-http-source.json':`report-export-${run}-source.json`));
   assert.equal(source.outletId,fixture.outlet);
   const {rows:reports}=await db.query('SELECT snapshot FROM ccs.closing_reports WHERE closing_id=$1 AND outlet_id=$2 ORDER BY generation',[source.closingId,fixture.outlet]);
   assert.equal(reports.length,1);assert.deepEqual(reports[0].snapshot,source);
   const {rows:closings}=await db.query('SELECT id,generation::text,revision::text,state FROM ccs.closings WHERE outlet_id=$1 ORDER BY business_date',[fixture.outlet]);
   assert.equal(closings.length,run==='http-final'?5:1);
   for(const closing of closings){
    assert.equal(closing.generation,'2');assert.equal(closing.revision,'5');assert.equal(closing.state,'DRAFT');
    const {rows:entries}=await db.query('SELECT quantity FROM ccs.closing_entries WHERE closing_id=$1 ORDER BY quantity',[closing.id]);
    assert.deepEqual(entries.map(e=>e.quantity),[0,6]);
    const {rows:[receipts]}=await db.query("SELECT count(*)::int AS count FROM ccs.operation_receipts WHERE outlet_id=$1 AND response->>'closingId'=$2",[fixture.outlet,closing.id]);
    assert.equal(receipts.count,5);
    const {rows:[reportCount]}=await db.query('SELECT count(*)::int AS count FROM ccs.closing_reports WHERE closing_id=$1',[closing.id]);
    assert.equal(reportCount.count,1);
   }
   assert.deepEqual(source.closing.entries.map(e=>e.quantity).sort((a,b)=>a-b),[0,4]);
   assert.equal(source.generation,1);assert.equal(source.closingRevision,3);
   const {rows:[catalog]}=await db.query('SELECT count(*)::int AS count FROM ccs.catalog_versions WHERE outlet_id=$1',[fixture.outlet]);assert.equal(catalog.count,1);
   const {rows:[member]}=await db.query('SELECT active FROM ccs.memberships WHERE outlet_id=$1 AND account_id=$2',[fixture.outlet,fixture.employee]);assert.equal(member.active,true);
   const stopped=read(path.join(folder,'fixture/database-after.json'));assert.equal(stopped.internalErrorCount,0);
   assert.equal(stopped.receipts.length,closings.length*5);
   assert.equal(stopped.settingsReceipts.length,run==='http-final'?1:0);
   assert.equal(stopped.teamReceipts.length,run==='http-final'?2:0);
   groups.push({run,outlet:fixture.outlet,closingsCompared:closings.length,storedReportEqualsActualExportSource:true,selectedGeneration:1,frozenQuantity:4,currentGeneration:2,currentQuantity:6,
    receiptsPerClosing:5,reportCountPerClosing:1,catalogCount:1,employeeMembershipRestored:true,internalErrors:0});
  }
  await db.query('ROLLBACK');
  const result={passed:true,ownedDatabase:true,transactionReadOnly:true,groups,physicalPhoneAccessed:false,publicAcceptance:false};
  fs.writeFileSync(path.join(root,'database-proof.json'),JSON.stringify(result,null,2)+'\n');
  console.log(JSON.stringify({passed:true,groups:groups.length,closingsCompared:groups.reduce((n,g)=>n+g.closingsCompared,0),readOnly:true}));
 }finally{db.release();}
}finally{await pool.end();}
