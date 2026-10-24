import fs from 'node:fs';
import path from 'node:path';
import {loadState,readJson,atomicJson} from '../../demo/private-state.mjs';
import {appPool,assertContainer} from './database.mjs';
import {assertOwnedDatabase} from '../src/config.mjs';
import {createApp,serviceVersion} from '../src/app.mjs';
const [directory,run]=process.argv.slice(2);
let pool,server,poll,stopping=false,state;
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
async function stop() {
 if(stopping)return;stopping=true;clearInterval(poll);
 if(server)await new Promise(resolve=>server.close(resolve));if(pool)await pool.end();
 if(state) {
  const file=path.join(state.dir,'active-run.json');const record=readJson(file);
  if(record.run===run&&record.pid===process.pid)atomicJson(file,{...record,status:'stopped',stoppedAt:new Date().toISOString()});
 }
}
try {
 if(!/^[a-f0-9-]{36}$/.test(run||''))throw Error('Invalid demo run');
 state=loadState(directory);assertContainer(state);
 const recordFile=path.join(state.dir,'active-run.json');let record;
 for(let i=0;i<20;i++){record=readJson(recordFile);if(record.run===run&&record.pid===process.pid)break;await sleep(100);}
 if(record.run!==run||record.pid!==process.pid)throw Error('Demo run owner mismatch');
 const stopFile=path.join(state.dir,'stop-'+run+'.json');
 poll=setInterval(()=>{if(fs.existsSync(stopFile)){const command=readJson(stopFile);if(command.run===run)stop().catch(()=>process.exitCode=1);}},300);
 pool=appPool(state);await assertOwnedDatabase(pool,state.config);
 server=await createApp({pool,environment:'DEMO',onInternalError:({requestId})=>{
  fs.appendFileSync(path.join(state.dir,'server-events.jsonl'),JSON.stringify({type:'internal-error',requestId,run,at:new Date().toISOString()})+'\n');
 }});
 server.prependListener('request',(_req,res)=>res.setHeader('X-Closing-Demo-Run',run));
 if(stopping)throw Error('Demo startup stopped');
 await new Promise((resolve,reject)=>{server.once('error',reject);server.listen(state.config.port,state.config.host,resolve);});
 atomicJson(recordFile,{...record,status:'ready',readyAt:new Date().toISOString(),version:serviceVersion});
 console.log('Closing Count local DEMO ready; fictional data, loopback only.');
}catch {
 console.error('Closing Count local DEMO startup failed; no credentials logged.');process.exitCode=1;await stop();
}
process.on('SIGINT',()=>stop());process.on('SIGTERM',()=>stop());
