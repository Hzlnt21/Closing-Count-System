// One extra fault case includes the ordinary CLI controller lock, not just seed IO.
import fs from 'node:fs';import path from 'node:path';import {pathToFileURL} from 'node:url';import {spawnSync} from 'node:child_process';import assert from 'node:assert/strict';
const root='D:/Code/ToolCache/PortfolioQA/Relocated Closing Demo R1',out='D:/Code/Documents/Career-Portfolio-2026/evidence/closing-count-demo-portability-r1';
const profile='D:/Code/ToolCache/PortfolioQA/Locked Crash Demo Profile R1',dir=path.join(profile,'ClosingCountDemo'),env={...process.env,LOCALAPPDATA:profile};
const priv=await import(pathToFileURL(path.join(root,'demo/private-state.mjs'))),db=await import(pathToFileURL(path.join(root,'service/demo/database.mjs'))),gen=await import(pathToFileURL(path.join(root,'service/demo/generation.mjs')));
let state,pool,passed=false;
function cli(action){const r=spawnSync(process.execPath,[path.join(root,'demo/runtime.mjs'),action],{cwd:root,env,encoding:'utf8',timeout:90000,windowsHide:true});assert.equal(r.status,0);return JSON.parse(r.stdout);}
try {
 assert(!fs.existsSync(profile)&&!fs.existsSync(path.join(out,'locked-seed-proof.json')),'Use a fresh controlled fault namespace');
 const driver=path.join(out,'locked-crash-seed-driver.mjs');
 fs.writeFileSync(driver,`import fs from 'node:fs';import path from 'node:path';import {pathToFileURL} from 'node:url';const root=process.argv[2];const priv=await import(pathToFileURL(path.join(root,'demo/private-state.mjs')).href),db=await import(pathToFileURL(path.join(root,'service/demo/database.mjs')).href),gen=await import(pathToFileURL(path.join(root,'service/demo/generation.mjs')).href);try{const dir=priv.stateDirectory();await priv.controllerLock(dir,async()=>{const state=await db.initializeDatabase(dir);await gen.seedGeneration(state,{onProgress:async e=>{if(e.stage==='quantity-committed'&&e.kind==='history'&&e.menu==='cappuccino'){fs.writeFileSync(path.join(state.dir,'deliberate-exit.json'),JSON.stringify({purpose:'owned-local-seed-and-lock-recovery-test',stage:e.stage}));process.exit(86);}}});});process.exitCode=1;}catch{console.error('Owned locked seed fault driver failed');process.exitCode=1;}`);
 const result=spawnSync(process.execPath,[driver,root],{cwd:root,env,encoding:'utf8',timeout:90000,windowsHide:true});assert.equal(result.status,86);
 state=priv.loadState(dir);pool=db.appPool(state);const lock=priv.readJson(path.join(dir,'controller.lock'));
 assert.equal(lock.format,'closing-count-demo-controller-lock');let dead=false;try{process.kill(lock.pid,0);}catch(e){if(e.code==='ESRCH')dead=true;else throw e;}assert(dead);
 const row=await gen.currentGeneration(pool);assert.equal(row.status,'SEEDING');assert.equal(row.number,1);
 const g=gen.privateGeneration(state,1),before=(await pool.query("SELECT to_jsonb(r)-'fingerprint' AS row FROM ccs.operation_receipts r WHERE outlet_id=$1",[g.outlet])).rows;assert.equal(before.length,1);
 const privateBytes=fs.readFileSync(path.join(dir,'generation-00001.private.json'));
 assert.equal(cli('seed').generation,1);assert.equal(fs.existsSync(path.join(dir,'controller.lock')),false);
 const after=await gen.currentGeneration(pool);assert.equal(after.status,'READY');assert.equal(after.number,1);
 const receipts=(await pool.query("SELECT to_jsonb(r)-'fingerprint' AS row FROM ccs.operation_receipts r WHERE outlet_id=$1",[g.outlet])).rows.map(r=>r.row);
 assert.equal(receipts.length,6);assert.deepEqual(receipts.find(r=>r.operation_id===before[0].row.operation_id),before[0].row);
 assert.deepEqual(fs.readFileSync(path.join(dir,'generation-00001.private.json')),privateBytes);
 const count=(await pool.query('SELECT count(*)::int AS n FROM ccs.closing_audit WHERE outlet_id=$1',[g.outlet])).rows[0].n;assert.equal(count,8);
 assert(cli('start').running);assert(cli('status').versionMatches);cli('stop');assert.equal(db.assertContainer(state).State.Running,false);
 const proof={passed:true,deliberateExitCode:86,normalTestPassClaimForAbortedDriver:false,controllerLockPersistedAcrossActualProcessExit:true,controllerPidVerifiedExited:true,ordinaryCliSeedRecoveredStaleLock:true,originalReceiptAndGenerationCredentialsPreserved:true,finalReceiptCount:6,finalAuditCount:8,finalGeneration:1,finalState:'READY',normalStartStatusStopPassed:true,physicalPhoneAccessed:false,nativeDemoJourneyComplete:false,ccs08Complete:false};
 fs.writeFileSync(path.join(out,'locked-seed-proof.json'),JSON.stringify(proof,null,2)+'\n');passed=true;console.log(JSON.stringify(proof));
}catch(e){console.error('Owned locked-seed recovery failed ('+e.name+')');process.exitCode=1;}
finally{
 if(pool)await pool.end();
 if(!state&&fs.existsSync(path.join(dir,'instance.private.json')))state=priv.loadState(dir);
 if(state){try{cli('stop');}catch{console.error('Owned locked profile cleanup needs inspection');process.exitCode=1;}}
 fs.writeFileSync(path.join(out,'locked-seed-completion.json'),JSON.stringify({passed:passed&&!(process.exitCode),exitCode:process.exitCode||0,cleanupAttempted:!!state,deliberateFailureWasExpected:true},null,2)+'\n');
}
