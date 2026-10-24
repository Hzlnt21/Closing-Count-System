import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawn} from 'node:child_process';
import {randomUUID} from 'node:crypto';
import {loadState,stateDirectory,controllerLock,readJson,atomicJson} from '../../demo/private-state.mjs';
import {initializeDatabase,stopDatabase,assertContainer,appPool,portOpen} from './database.mjs';
import {seedGeneration,currentGeneration,privateGeneration} from './generation.mjs';
import {assertOwnedDatabase} from '../src/config.mjs';
import {serviceVersion} from '../src/app.mjs';
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
const alive=pid=>{if(!Number.isInteger(pid)||pid<1)return false;try{process.kill(pid,0);return true;}catch(e){if(e.code==='ESRCH')return false;throw Error('Demo process ownership state is unavailable');}};
async function health(run) {
 try {
  const response=await fetch('http://127.0.0.1:4372/api/health',{signal:AbortSignal.timeout(1500)});
  const body=await response.json();return response.ok&&response.headers.get('X-Closing-Demo-Run')===run&&body.data?.environment==='DEMO'&&body.data?.service==='closing-count'?body.data:null;
 }catch{return null;}
}
export async function start(state,{beforeSpawn,onSpawned}={}) {
 // Optional local-library callbacks are used only by explicit fault tests;
 // ordinary CLI start never supplies callbacks or reads fault environment flags.
 if([beforeSpawn,onSpawned].some(fn=>fn!==undefined&&typeof fn!=='function'))throw Error('Invalid local startup callback');
 const file=path.join(state.dir,'active-run.json'),previous=fs.existsSync(file)?readJson(file):null;
 const probe=previous?.status==='ready'&&alive(previous.pid)?await health(previous.run):null;
 if(probe&&probe.version!==serviceVersion)throw Error('Stop the previous demo service version before starting the new version');
 const alreadyRunning=!!probe;
 if(!alreadyRunning&&await portOpen(4372))throw Error('Demo API port is occupied; refusing another process');
 await initializeDatabase(state.dir);
 if(!alreadyRunning&&previous&&previous.status!=='stopped'&&alive(previous.pid))throw Error('Recorded demo process is still starting or unresponsive; use status/stop');
 const pool=appPool(state);let generation;
 try{await assertOwnedDatabase(pool,state.config);generation=await currentGeneration(pool);if(generation?.status!=='READY')throw Error('A complete demo seed is required before start');}finally{await pool.end();}
 if(alreadyRunning)return{running:true,alreadyRunning:true,port:4372,generation:generation.number};
 const run=randomUUID(),record={run,status:'starting',pid:null,generation:generation.number,createdAt:new Date().toISOString()};
 atomicJson(file,record);let child,log,spawnError=false;
 try {
  log=fs.openSync(path.join(state.dir,'server.log'),'a',0o600);
  await beforeSpawn?.();
  child=spawn(process.execPath,[fileURLToPath(new URL('./server.mjs',import.meta.url)),state.dir,run],{detached:true,windowsHide:true,stdio:['ignore',log,log]});
  child.once('error',()=>{spawnError=true;});
  if(!Number.isInteger(child.pid))throw Error('Demo server child did not start');
  child.unref();atomicJson(file,{...record,pid:child.pid});await onSpawned?.();
  const deadline=Date.now()+45000;
  while(Date.now()<deadline) {
   const ready=await health(run);
   if(ready&&ready.version===serviceVersion)return{running:true,alreadyRunning:false,port:4372,generation:generation.number};
   if(spawnError||!alive(child.pid)||readJson(file).status==='stopped')throw Error('Demo server startup failed; inspect redacted server.log');
   await sleep(200);
  }
  throw Error('Demo startup timed out');
 }catch(error) {
  if(Number.isInteger(child?.pid)) {
   atomicJson(path.join(state.dir,'stop-'+run+'.json'),{run});
   const until=Date.now()+12000;
   while(alive(child.pid)&&readJson(file).status!=='stopped'&&Date.now()<until)await sleep(200);
   if(alive(child.pid)&&readJson(file).status!=='stopped')throw Error('Owned startup child is still recorded; use stop before retrying');
  }
  const current=readJson(file);
  if(current.run===run&&(!child||!alive(child.pid)))atomicJson(file,{...current,status:'stopped',stoppedAt:new Date().toISOString()});
  throw error;
 }finally{if(log!==undefined)fs.closeSync(log);}
}
export async function stop(state) {
 assertContainer(state);
 const file=path.join(state.dir,'active-run.json');let alreadyStopped=true;
 if(fs.existsSync(file)) {
  const record=readJson(file);if(!/^[a-f0-9-]{36}$/.test(record.run))throw Error('Unknown demo run record');
  if(record.status!=='stopped'&&alive(record.pid)) {
   alreadyStopped=false;atomicJson(path.join(state.dir,'stop-'+record.run+'.json'),{run:record.run});
   for(let i=0;i<100;i++){if(readJson(file).status==='stopped'||!alive(record.pid))break;await sleep(200);}
   if(readJson(file).status!=='stopped'&&alive(record.pid))throw Error('Owned demo child has not stopped; database left available');
  }
 }
 // No PID kill or adoption of a listener. Only the matched private control file
 // is used, and the separately labelled database is validated before shutdown.
 stopDatabase(state);return{running:false,alreadyStopped,historyAndVolumeRetained:true};
}
export async function main(args) {
 const action=args[0],rest=args.slice(1);let directory;
 try {
  if(rest.length){if(rest.length!==2||rest[0]!=='--state-dir')throw Error('Use --state-dir followed by an absolute private directory');directory=rest[1];}
  directory=stateDirectory(directory);
  if(!['init','seed','start','status','stop','reset','access'].includes(action))throw Error('Use init, seed, start, status, stop, reset or access');
  const result=await controllerLock(directory,async()=>{
   if(action==='init') {
    const state=await initializeDatabase(directory);return{initialized:true,...await seedGeneration(state),credentialsPrinted:false};
   }
   const state=loadState(directory);
   if(action==='start')return start(state);
   if(action==='stop')return stop(state);
   if(action==='status') {
    const file=path.join(directory,'active-run.json'),record=fs.existsSync(file)?readJson(file):null;
    const running=record?.status==='ready'&&alive(record.pid)?await health(record.run):null;
    return{running:!!running,recordedStatus:record?.status||'not-started',version:running?.version||null,expectedVersion:serviceVersion,versionMatches:!!running&&running.version===serviceVersion,port:4372,credentialsPrinted:false};
   }
   if(['seed','reset'].includes(action))await initializeDatabase(directory);
   assertContainer(state);const pool=appPool(state);let current;
   try{await assertOwnedDatabase(pool,state.config);current=await currentGeneration(pool);}finally{await pool.end();}
   if(action==='access') {
    if(current?.status!=='READY')throw Error('Demo seed is not ready');const g=privateGeneration(state,current.number);
    // Explicit local operator command only. Never run this during evidence capture.
    return{localDemoCredentials:true,outlet:g.outlet,businessDate:g.anchor,accounts:Object.values(g.actors).map(({username,password,role,displayName})=>({username,password,role,displayName}))};
   }
   return seedGeneration(state,{reset:action==='reset'});
  });
  console.log(JSON.stringify(result));
 }catch(error) {
  const code=/^[A-Z0-9_]{1,40}$/.test(error.code||'')?error.code:'REFUSED';
  console.error('Local demo command failed ('+code+'); no credentials logged. Check the command, private state, Docker ownership and ports.');process.exitCode=1;
 }
}
