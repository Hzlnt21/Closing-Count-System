import {readFileSync,writeFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
import path from 'node:path';
import assert from 'node:assert/strict';
import pg from 'pg';
import {configFromEnvironment,assertOwnedDatabase} from '../../../../Worktrees/Closing-Count-Portfolio/service/src/config.mjs';
import {normalizeCatalog,closingSnapshot} from '../../../../Worktrees/Closing-Count-Portfolio/service/src/catalog.mjs';
const out='D:/Code/Documents/Career-Portfolio-2026/evidence/closing-count-layout-r1';
const json=p=>JSON.parse(readFileSync(p,'utf8').replace(/^\uFEFF/,''));
const codes=['phone-large-dark','phone-small-light','phone-landscape-light','tablet-light','tablet-dark-large','tablet-landscape-dark'];
const targets=[[360,800,2,true],[360,800,1,false],[800,360,1.3,false],[800,1280,1,false],[800,1280,2,true],[1280,800,1.3,true]];
const scenes=['login','overview','input','unsaved','review','history','account','settings-review','team-form','catalog-form'];
const longOutlet='Kedai Kopi Nusantara Cabang Pusat Pelayanan Pelanggan dan Operasional';
const longMenu='Kopi Susu Arabika dengan Tambahan Krim dan Gula Aren untuk Menu Spesial Pelanggan';
const longIngredient='Biji Kopi Arabika Pilihan untuk Resep Minuman Spesial Nusantara';
const apks=['D:/Code/Worktrees/Closing-Count-Portfolio/app/build/outputs/apk/debug/app-debug.apk','D:/Code/Worktrees/Closing-Count-Portfolio/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'];
const hashes=apks.map(p=>createHash('sha256').update(readFileSync(p)).digest('hex').toUpperCase());
const secret=json(path.join(process.env.LOCALAPPDATA,'PortfolioQA/secrets/closing-count-local-r1.json'));
const config=configFromEnvironment({CCS_ENVIRONMENT:'QA',CCS_INSTANCE:secret.instance,CCS_DATABASE_URL:`postgresql://ccs_portfolio_app:${secret.appPassword}@127.0.0.1:55436/closing_count_portfolio_qa`});
const pool=new pg.Pool({connectionString:config.databaseUrl,max:1,connectionTimeoutMillis:5000,statement_timeout:10000});
const proofs=[];
try{
 await assertOwnedDatabase(pool,config);
 assert.deepEqual(json(out+'/validator-probe-apk-inputs.json').map(r=>r.Hash),hashes);
 const probeBytes=readFileSync(out+'/validator-probe.log');
 const probe=probeBytes[0]===255&&probeBytes[1]===254?probeBytes.toString('utf16le'):probeBytes.toString('utf8');
 assert.match(probe,/OK \(1 test\)/);assert.doesNotMatch(probe,/FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|AssumptionViolated/);
 for(const [i,code] of codes.entries()){
  const base=out+'/'+code,verified=json(base+'/verification.json'),fixture=json(base+'/fixture/fixture-identity.json'),layout=json(base+'/layout.json');
  const [width,height,font,dark]=targets[i];
  assert(verified.passed&&verified.deviceSettingsRestored);assert.equal(verified.outlet,fixture.outlet);assert.equal(verified.physicalPhoneAccessed,false);
  assert.equal(layout.case,code);assert(layout.passed);assert(Math.abs(layout.screenWidthDp-width)<=8);assert(Math.abs(layout.screenHeightDp-height)<=8);assert(Math.abs(layout.fontScale-font)<0.03);assert.equal(layout.dark,dark);
  assert.deepEqual(layout.checks.map(c=>c.scene),scenes);for(const c of layout.checks){assert(c.measuredStaticTexts>0);assert.deepEqual(c.overflow,[])}
  assert.deepEqual(json(base+'/apk-inputs.json').map(r=>r.Hash),hashes);
  const q=async sql=>(await pool.query(sql,[fixture.outlet])).rows;
  const versions=await q('SELECT id,revision::text,snapshot FROM ccs.catalog_versions WHERE outlet_id=$1 ORDER BY revision');assert.equal(versions.length,2);
  const expected=structuredClone(versions[0].snapshot);expected.menus[0].name=longMenu;expected.ingredients[0].name=longIngredient;assert.deepEqual(versions[1].snapshot,normalizeCatalog(expected));
  const settings=(await q('SELECT name,timezone,cutoff_minutes,settings_revision::text FROM ccs.outlets WHERE id=$1'))[0];assert.deepEqual(settings,{name:longOutlet,timezone:'Asia/Jakarta',cutoff_minutes:0,settings_revision:'1'});
  const settingsAudit=await q('SELECT actor_id,operation_id FROM ccs.outlet_settings_audit WHERE outlet_id=$1');assert.equal(settingsAudit.length,1);assert.equal(settingsAudit[0].actor_id,fixture.admin);
  const receipts=await q('SELECT operation_id,actor_id,response FROM ccs.operation_receipts WHERE outlet_id=$1');assert.equal(receipts.length,2);assert(receipts.every(r=>r.actor_id===fixture.admin));assert(receipts.some(r=>r.operation_id===settingsAudit[0].operation_id));
  const closings=await q('SELECT id,state,generation::text,revision::text,catalog_version_id,snapshot,cutoff_minutes FROM ccs.closings WHERE outlet_id=$1');assert.equal(closings.length,1);const c=closings[0];assert.equal(c.state,'DRAFT');assert.equal(c.generation,'1');assert.equal(c.revision,'0');assert.equal(c.catalog_version_id,versions[1].id);assert.equal(c.cutoff_minutes,0);assert.deepEqual(c.snapshot,closingSnapshot(versions[1].snapshot));
  const entries=await q('SELECT e.quantity,e.revision::text,e.updated_by FROM ccs.closing_entries e JOIN ccs.closings c ON c.id=e.closing_id WHERE c.outlet_id=$1');assert.equal(entries.length,2);for(const e of entries)assert.deepEqual(e,{quantity:0,revision:'0',updated_by:null});
  const audit=await q('SELECT action FROM ccs.closing_audit WHERE outlet_id=$1');assert.deepEqual(audit,[{action:'CREATE'}]);
  assert.equal((await q('SELECT account_id FROM ccs.memberships WHERE outlet_id=$1')).length,4);
  assert.equal((await q('SELECT id FROM ccs.membership_audit WHERE outlet_id=$1')).length,0);
  assert.equal((await q('SELECT r.id FROM ccs.closing_reports r JOIN ccs.closings c ON c.id=r.closing_id WHERE c.outlet_id=$1')).length,0);
  proofs.push({case:code,outlet:fixture.outlet,passed:true,runtimeConfiguration:targets[i],checks:layout.checks.length,settingsReviewScrolledToEnd:layout.settingsReviewScrolledToEnd,unsavedMessageScrolledToEnd:layout.unsavedMessageScrolledToEnd,receipts:receipts.length,catalogVersions:versions.length,closingState:c.state,quantitySaves:0,teamWrites:0,reports:0,deviceSettingsRestored:true});
 }
 writeFileSync(out+'/database-proof.json',JSON.stringify({passed:true,apkHashes:hashes,validatorProbePassed:true,cases:proofs,scope:'Selected native layouts and owned fixture isolation; not full accessibility or physical-device acceptance',fixturesRetained:true},null,2));
 console.log(JSON.stringify({passed:true,cases:proofs.length,nativeScenes:proofs.reduce((n,p)=>n+p.checks,0),deviceSettingsRestored:true,unexpectedUiWrites:0}));
}finally{await pool.end()}
