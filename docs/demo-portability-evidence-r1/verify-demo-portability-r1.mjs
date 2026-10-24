// Read/write only explicitly owned local demo profiles. No physical Android access.
import fs from 'node:fs';import path from 'node:path';import {pathToFileURL} from 'node:url';
import {spawnSync} from 'node:child_process';import {createHash} from 'node:crypto';import {createRequire} from 'node:module';import assert from 'node:assert/strict';
const original='D:/Code/Worktrees/Closing-Count-Portfolio',relocated='D:/Code/ToolCache/PortfolioQA/Relocated Closing Demo R1';
const out='D:/Code/Documents/Career-Portfolio-2026/evidence/closing-count-demo-portability-r1';
const legacyDir='D:/Code/ToolCache/PortfolioQA/closing-count-demo-r1';
const profile='D:/Code/ToolCache/PortfolioQA/Portable Demo Profile R1',crashProfile='D:/Code/ToolCache/PortfolioQA/Crash Demo Profile R1';
const moduleAt=(root,name)=>import(pathToFileURL(path.join(root,name)).href);
const originalPrivate=await moduleAt(original,'demo/private-state.mjs'),originalDb=await moduleAt(original,'service/demo/database.mjs');
const portablePrivate=await moduleAt(relocated,'demo/private-state.mjs'),portableDb=await moduleAt(relocated,'service/demo/database.mjs');
const portableRuntime=await moduleAt(relocated,'service/demo/runtime.mjs'),portableGeneration=await moduleAt(relocated,'service/demo/generation.mjs');
const sha=data=>createHash('sha256').update(data).digest('hex'),hash=value=>sha(Buffer.from(JSON.stringify(value)));
const sleep=ms=>new Promise(r=>setTimeout(r,ms));const steps=[],owned=[];let phase='setup',pool,legacyState;
function cli(root,action,env={},ok=true) {
 const r=spawnSync(process.execPath,[path.join(root,'demo/runtime.mjs'),action,...(root===original?['--state-dir',legacyDir]:[])],{cwd:root,env:{...process.env,...env},encoding:'utf8',timeout:120000,windowsHide:true});
 if(!ok){assert.equal(r.status,1);return;}
 assert.equal(r.status,0,'CLI action failed: '+action);return JSON.parse(r.stdout);
}
const envFor=base=>({LOCALAPPDATA:base});const dirFor=base=>path.join(base,'ClosingCountDemo');
async function note(name,action){phase=name;await action();steps.push({name,passed:true});console.log(JSON.stringify({step:name,passed:true}));}
async function frozen(db,outlet) {
 const value={};for(const table of ['catalog_versions','operation_receipts','closing_audit','closing_reports'])value[table]=(await db.query(`SELECT to_jsonb(t)-'fingerprint' AS row FROM ccs.${table} t WHERE outlet_id=$1 ORDER BY to_jsonb(t)::text`,[outlet])).rows.map(r=>r.row);
 return{sha256:hash(value),counts:Object.fromEntries(Object.entries(value).map(([k,v])=>[k,v.length]))};
}
async function baseline(db,state) {
 const row=await portableGeneration.currentGeneration(db);assert.equal(row.status,'READY');const g=portableGeneration.privateGeneration(state,row.number);
 const closings=(await db.query('SELECT id,business_date::text,state,revision::int,generation::int FROM ccs.closings WHERE outlet_id=$1 ORDER BY business_date',[g.outlet])).rows;assert.equal(closings.length,2);
 for(const [index,kind] of ['history','current'].entries()) {
  const c=closings[index],expected=portableGeneration.manifest.baseline[kind];assert.equal(c.state,expected.state);assert.equal(c.revision,index===0?4:2);assert.equal(c.generation,1);
  const entries=(await db.query('SELECT menu_id,quantity,updated_by FROM ccs.closing_entries WHERE closing_id=$1',[c.id])).rows;
  assert.equal(entries.length,2);assert.equal(entries.reduce((s,e)=>s+e.quantity,0),expected.totalPortions);
  for(const [menu,actor] of [['cappuccino','rani'],['latte','dimas']]){const e=entries.find(e=>e.menu_id===g.ids[menu]);assert.equal(e.quantity,expected.quantities[menu]);assert.equal(e.updated_by,g.actors[actor].id);}
 }
 const snapshot=await frozen(db,g.outlet);assert.deepEqual(snapshot.counts,{catalog_versions:1,operation_receipts:6,closing_audit:8,closing_reports:1});return{g,snapshot};
}
function assertStopped(state,dbModule) {
 assert.equal(dbModule.assertContainer(state).State.Running,false);
 const record=portablePrivate.readJson(path.join(state.dir,'active-run.json'));assert.equal(record.status,'stopped');
 assert.equal(fs.existsSync(path.join(state.dir,'controller.lock')),false);
}
async function stoppedChild(state) {
 const record=portablePrivate.readJson(path.join(state.dir,'active-run.json'));assert.equal(record.status,'stopped');
 for(let i=0;i<30;i++){try{process.kill(record.pid,0);}catch(e){if(e.code==='ESRCH')return;throw e;}await sleep(100);}
 throw Error('Owned startup child remained alive');
}
let fresh,crash,initial,legacyPrivateBytes,legacyGenerationBytes;
try {
 assert(!fs.existsSync(path.join(out,'runtime-proof.json')),'Evidence already contains a completed run');
 assert(!fs.existsSync(profile)&&!fs.existsSync(crashProfile),'Fresh isolated profile names already exist');
 await note('extracted-payload-and-local-dependencies-verified',async()=>{
  const info=JSON.parse(fs.readFileSync(path.join(relocated,'package-info.json')));
  for(const file of info.files)assert.equal(sha(fs.readFileSync(path.join(relocated,file.file))),file.sha256);
  assert.equal(portablePrivate.packageRoot,fs.realpathSync(relocated));
  const pg=createRequire(path.join(relocated,'service/package.json')).resolve('pg/package.json');assert(pg.startsWith(path.join(relocated,'service','node_modules')));
 });
 await note('version1-profile-and-archived-history-preserved',async()=>{
  legacyState=originalPrivate.loadState(legacyDir);assert.equal(legacyState.value.version,1);assert.equal(originalDb.assertContainer(legacyState).State.Running,false);
  legacyPrivateBytes=fs.readFileSync(legacyState.file);legacyGenerationBytes=fs.readFileSync(path.join(legacyDir,'generation-00002.private.json'));owned.push({root:original,env:{},state:legacyState,db:originalDb});
  assert(cli(original,'start').running);assert(cli(original,'seed').alreadySeeded);pool=originalDb.appPool(legacyState);
  const g=originalPrivate.readJson(path.join(legacyDir,'generation-00001.private.json'));
  const old=JSON.parse(fs.readFileSync('D:/Code/Documents/Career-Portfolio-2026/evidence/closing-count-demo-foundation-r1/runtime-proof.json'));
  assert.deepEqual(await frozen(pool,g.outlet),old.archivedImmutableData);
  assert.equal((await originalDb.assertContainer(legacyState)).Name,'/ccs-portfolio-demo-pg16');
  await pool.end();pool=null;cli(original,'stop');assertStopped(legacyState,originalDb);
  assert.deepEqual(fs.readFileSync(legacyState.file),legacyPrivateBytes);assert.deepEqual(fs.readFileSync(path.join(legacyDir,'generation-00002.private.json')),legacyGenerationBytes);
 });
 await note('fresh-default-profile-in-relocated-directory-initializes-independently',async()=>{
  const result=cli(relocated,'init',envFor(profile));assert.equal(result.generation,1);assert.equal(result.alreadySeeded,false);
  fresh=portablePrivate.loadState(dirFor(profile));owned.push({root:relocated,env:envFor(profile),state:fresh,db:portableDb});
  assert.equal(fresh.value.version,2);assert.notEqual(portableDb.resourcesFor(fresh).container,originalDb.resourcesFor(legacyState).container);
  assert.notEqual(fresh.value.instance,legacyState.value.instance);assert.notEqual(fresh.value.appPassword,legacyState.value.appPassword);
  assert.equal(originalDb.assertContainer(legacyState).State.Running,false);pool=portableDb.appPool(fresh);initial=await baseline(pool,fresh);
 });
 await note('relocated-start-status-and-repeat-init-seed-are-consistent',async()=>{
  assert(cli(relocated,'start',envFor(profile)).running);const first=portablePrivate.readJson(path.join(fresh.dir,'active-run.json'));
  assert(cli(relocated,'start',envFor(profile)).alreadyRunning);assert.equal(portablePrivate.readJson(path.join(fresh.dir,'active-run.json')).run,first.run);
  const status=cli(relocated,'status',envFor(profile));assert(status.running&&status.versionMatches);assert.equal(status.version,'0.2.0-dev.8');
  assert(cli(relocated,'init',envFor(profile)).alreadySeeded);assert(cli(relocated,'seed',envFor(profile)).alreadySeeded);assert.deepEqual((await baseline(pool,fresh)).snapshot,initial.snapshot);
 });
 await note('another-retained-instance-cannot-adopt-active-ports',async()=>{
  const before=fs.readFileSync(path.join(legacyDir,'active-run.json'));cli(original,'start',{},false);
  assert.deepEqual(fs.readFileSync(path.join(legacyDir,'active-run.json')),before);assert.equal(originalDb.assertContainer(legacyState).State.Running,false);assert(cli(relocated,'status',envFor(profile)).running);
 });
 await pool.end();pool=null;cli(relocated,'stop',envFor(profile));
 await note('child-authentication-failure-is-bounded-and-cleaned-up',async()=>{
  const bytes=fs.readFileSync(fresh.file),began=Date.now();
  try{await assert.rejects(portablePrivate.controllerLock(fresh.dir,()=>portableRuntime.start(fresh,{beforeSpawn:()=>{
   portablePrivate.atomicJson(fresh.file,{...fresh.value,appPassword:'c'.repeat(64)});
  }})));}
  finally{portablePrivate.atomicJson(fresh.file,JSON.parse(bytes));}
  assert(Date.now()-began<75000);await stoppedChild(fresh);
  assert(cli(relocated,'start',envFor(profile)).running);pool=portableDb.appPool(fresh);assert.deepEqual((await baseline(pool,fresh)).snapshot,initial.snapshot);await pool.end();pool=null;cli(relocated,'stop',envFor(profile));
 });
 await note('controller-failure-after-spawn-stops-matched-production-child',async()=>{
  const began=Date.now();await assert.rejects(portablePrivate.controllerLock(fresh.dir,()=>portableRuntime.start(fresh,{onSpawned:()=>{throw Error('Explicit local fault after child registration');}})));
  assert(Date.now()-began<75000);await stoppedChild(fresh);
  assert(cli(relocated,'start',envFor(profile)).running);pool=portableDb.appPool(fresh);assert.deepEqual((await baseline(pool,fresh)).snapshot,initial.snapshot);await pool.end();pool=null;cli(relocated,'stop',envFor(profile));assertStopped(fresh,portableDb);
 });
 await note('real-process-exit-leaves-durable-incomplete-seed',async()=>{
  const driver=path.join(out,'crash-seed-driver.mjs');
  fs.writeFileSync(driver,`import fs from 'node:fs';import path from 'node:path';import {pathToFileURL} from 'node:url';const root=process.argv[2];const priv=await import(pathToFileURL(path.join(root,'demo/private-state.mjs')).href);const db=await import(pathToFileURL(path.join(root,'service/demo/database.mjs')).href);const gen=await import(pathToFileURL(path.join(root,'service/demo/generation.mjs')).href);try{const state=await db.initializeDatabase(priv.stateDirectory());await gen.seedGeneration(state,{onProgress:async e=>{if(e.stage==='quantity-committed'&&e.kind==='history'&&e.menu==='cappuccino'){fs.writeFileSync(path.join(state.dir,'deliberate-exit.json'),JSON.stringify({purpose:'owned-local-seed-recovery-test',stage:e.stage}));process.exit(86);}}});process.exitCode=1;}catch{console.error('Owned seed fault driver failed');process.exitCode=1;}`);
  const result=spawnSync(process.execPath,[driver,relocated],{cwd:relocated,env:{...process.env,...envFor(crashProfile)},encoding:'utf8',timeout:90000,windowsHide:true});assert.equal(result.status,86);
  crash=portablePrivate.loadState(dirFor(crashProfile));owned.push({root:relocated,env:envFor(crashProfile),state:crash,db:portableDb});pool=portableDb.appPool(crash);
  const current=await portableGeneration.currentGeneration(pool);assert.equal(current.status,'SEEDING');assert.equal(current.number,1);
  const receipts=(await pool.query("SELECT to_jsonb(r)-'fingerprint' AS row FROM ccs.operation_receipts r WHERE outlet_id=$1",[current.outlet_id])).rows;assert.equal(receipts.length,1);
  fs.writeFileSync(path.join(out,'partial-seed-proof.json'),JSON.stringify({deliberateProcessExit:86,normalTestPassClaim:false,status:'SEEDING',generation:1,committedReceipts:1,receipt:receipts[0].row},null,2));
  assert.equal(fs.existsSync(path.join(crash.dir,'active-run.json')),false);cli(relocated,'start',envFor(crashProfile),false);assert.equal(fs.existsSync(path.join(crash.dir,'active-run.json')),false);
 });
 await note('ordinary-seed-resumes-same-generation-and-preserves-original-receipt',async()=>{
  const before=portablePrivate.readJson(path.join(out,'partial-seed-proof.json')),privateBytes=fs.readFileSync(path.join(crash.dir,'generation-00001.private.json'));
  const result=cli(relocated,'seed',envFor(crashProfile));assert.equal(result.generation,1);assert.equal(result.alreadySeeded,false);
  const complete=await baseline(pool,crash);const recovered=(await pool.query("SELECT to_jsonb(r)-'fingerprint' AS row FROM ccs.operation_receipts r WHERE outlet_id=$1 AND operation_id=$2",[complete.g.outlet,before.receipt.operation_id])).rows[0].row;
  assert.deepEqual(recovered,before.receipt);assert.deepEqual(fs.readFileSync(path.join(crash.dir,'generation-00001.private.json')),privateBytes);
  assert(cli(relocated,'start',envFor(crashProfile)).running);assert(cli(relocated,'status',envFor(crashProfile)).versionMatches);
  await pool.end();pool=null;cli(relocated,'stop',envFor(crashProfile));assertStopped(crash,portableDb);
 });
 assert.deepEqual(fs.readFileSync(legacyState.file),legacyPrivateBytes);assert.deepEqual(fs.readFileSync(path.join(legacyDir,'generation-00002.private.json')),legacyGenerationBytes);
 const proof={passed:true,steps,operatorPayloadRelocationPassed:true,freshPrivateIdentityVersion:2,legacyVersion1Preserved:true,sourceAndDeveloperSecretsNotNeededForRelocatedCommands:true,dependenciesInstalledInsideRelocatedPackage:true,pathsWithSpacesExercised:true,distinctRetainedDockerResources:true,fixedPortsRefuseOtherActiveInstances:true,actualChildAuthenticationFailureRecovered:true,actualControllerFaultAfterSpawnRecovered:true,deliberateSeedExitCode:86,ordinarySeedResumePreservedReceiptAndIdentity:true,rateClockSimulated:false,physicalPhoneAccessed:false,nativeDemoJourneyComplete:false,completeDemoDistribution:false,ccs08Complete:false,fullGoalComplete:false};
 fs.writeFileSync(path.join(out,'runtime-proof.json'),JSON.stringify(proof,null,2)+'\n');console.log(JSON.stringify({passed:true,scenarios:steps.length,ccs08Complete:false}));
}catch(error){fs.writeFileSync(path.join(out,'failure.json'),JSON.stringify({phase,errorClass:error.name,code:error.code||null,acceptanceClaim:false},null,2));console.error('Portability acceptance failed at '+phase+' ('+error.name+')');process.exitCode=1;}
finally {
 if(pool)await pool.end();
 for(const base of [profile,crashProfile]) {
  const dir=dirFor(base);
  if(!owned.some(r=>r.state.dir===dir)&&fs.existsSync(path.join(dir,'instance.private.json'))) {
   try {const state=portablePrivate.loadState(dir),resource=portableDb.resourcesFor(state);
    if(portableDb.inspect('container',resource.container)){portableDb.assertContainer(state);owned.push({root:relocated,env:envFor(base),state,db:portableDb});}
   }catch{console.error('Partial owned profile cleanup needs inspection');process.exitCode=1;}
  }
 }
 for(const item of [...owned].reverse())try{cli(item.root,'stop',item.env);}catch{console.error('Owned demo cleanup needs inspection');process.exitCode=1;}
}
