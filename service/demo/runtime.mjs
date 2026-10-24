import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawn} from 'node:child_process';
import {randomUUID} from 'node:crypto';
import {loadState,stateDirectory,controllerLock,readJson,atomicJson} from '../../demo/private-state.mjs';
import {initializeDatabase,stopDatabase,assertContainer,appPool,portOpen} from './database.mjs';
import {seedGeneration,currentGeneration,privateGeneration} from './generation.mjs';
import {assertOwnedDatabase} from '../src/config.mjs';
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
const alive=pid=>{if(!Number.isInteger(pid)||pid<1)return false;try{process.kill(pid,0);return true;}catch(e){if(e.code==='ESRCH')return false;throw Error('Demo process ownership state is unavailable');}};
async function health(run) {
 try {
  const response=await fetch('http://127.0.0.1:4372/api/health',{signal:AbortSignal.timeout(1500)});
  const body=await response.json();return response.ok&&response.headers.get('X-Closing-Demo-Run')===run&&body.data?.environment==='DEMO'&&body.data?.service==='closing-count';
 }catch{return false;}
}
export async function start(state) {
 const file=path.join(state.dir,'active-run.json'),previous=fs.existsSync(file)?readJson(file):null;
 const alreadyRunning=!!previous&&await health(previous.run);
 if(!alreadyRunning&&await portOpen(4372))throw Error('Demo API port is occupied; refusing another process');
 await initializeDatabase(state.dir);
 if(!alreadyRunning&&previous&&previous.status!=='stopped'&&alive(previous.pid))throw Error('Recorded demo process is still starting or unresponsive; use status/stop');
 const pool=appPool(state);let generation;
 try{await assertOwnedDatabase(pool,state.config);generation=await currentGeneration(pool);if(generation?.status!=='READY')throw Error('A complete demo seed is required before start');}finally{await pool.end();}
 if(alreadyRunning)return{running:true,alreadyRunning:true,port:4372,generation:generation.number};
 const run=randomUUID(),record={run,status:'starting',pid:null,generation:generation.number,createdAt:new Date().toISOString()};
 atomicJson(file,record);const log=fs.openSync(path.join(state.dir,'server.log'),'a',0o600);
 let child;
 try{child=spawn(process.execPath,[fileURLToPath(new URL('./server.mjs',import.meta.url)),state.dir,run],{detached:true,windowsHide:true,stdio:['ignore',log,log]});}
 finally{fs.closeSync(log);}
 if(!Number.isInteger(child.pid))throw Error('Demo server child did not start');
 let spawnError=false;child.once('error',()=>{spawnError=true;});child.unref();atomicJson(file,{...record,pid:child.pid});
 for(let i=0;i<100;i++) {
  if(await health(run))return{running:true,alreadyRunning:false,port:4372,generation:generation.number};
  if(spawnError||!alive(child.pid)||readJson(file).status==='stopped')throw Error('Demo server startup failed; inspect redacted server.log');
  await sleep(300);
 }
 atomicJson(path.join(state.dir,'stop-'+run+'.json'),{run});throw Error('Demo startup timed out; owned child received a stop request');
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
    return{running:!!record&&await health(record.run),recordedStatus:record?.status||'not-started',port:4372,credentialsPrinted:false};
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
