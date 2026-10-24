import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import {randomUUID} from 'node:crypto';
import {validateContainer,label,volume} from '../demo/database.mjs';
import {stateDirectory,loadState,packageRoot,controllerLock} from '../../demo/private-state.mjs';
test('container identity, bindings, secret mount and volume must all match',()=>{
 const state={passwordFile:'D:/owned-demo/postgres-password.private.txt',value:{instance:'a'.repeat(48)}};
 const info={Config:{Image:'postgres:16',Labels:{[label]:state.value.instance}},HostConfig:{RestartPolicy:{Name:'no'},Privileged:false,Memory:384*1024*1024,NanoCpus:750000000,PortBindings:{'5432/tcp':[{HostIp:'127.0.0.1',HostPort:'55437'}]}},Mounts:[{Type:'volume',Name:volume,Destination:'/var/lib/postgresql/data'},{Type:'bind',Source:'/run/desktop/mnt/host/d/owned-demo/postgres-password.private.txt',Destination:'/run/secrets/pg-password',RW:false}]};
 const v={Labels:{[label]:state.value.instance}};
 assert.doesNotThrow(()=>validateContainer(info,v,state));
 const bad=[x=>x.Config.Labels[label]='foreign',x=>x.HostConfig.Privileged=true,x=>x.HostConfig.PortBindings['5432/tcp'][0].HostIp='0.0.0.0',x=>x.HostConfig.PortBindings['5432/tcp'][0].HostPort='55436',x=>x.Mounts[1].RW=true,x=>x.Mounts[1].Source='D:/other/password',x=>x.Mounts[0].Name='operational'];
 for(const mutate of bad){const copy=structuredClone(info);mutate(copy);assert.throws(()=>validateContainer(copy,v,state));}
 assert.throws(()=>validateContainer(info,{Labels:{}},state));
});
test('source paths and linked descendants are rejected before private-state writes',()=>{
 assert.throws(()=>stateDirectory(packageRoot));assert.throws(()=>stateDirectory(path.join(packageRoot,'demo','runtime-private')));
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'ccs-demo-path-')),link=path.join(dir,'source-link');
 try{fs.symlinkSync(packageRoot,link,process.platform==='win32'?'junction':'dir');assert.throws(()=>stateDirectory(path.join(link,'future-private-state')));assert.equal(fs.existsSync(path.join(packageRoot,'future-private-state')),false);}
 finally{if(fs.existsSync(link))fs.unlinkSync(link);fs.rmdirSync(dir);}
});
test('unknown existing files are never adopted as a private instance',()=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'ccs-demo-foreign-')),file=path.join(dir,'foreign.txt');fs.writeFileSync(file,'keep');
 try{assert.throws(()=>loadState(dir,{create:true}));assert.equal(fs.readFileSync(file,'utf8'),'keep');assert.equal(fs.existsSync(path.join(dir,'instance.private.json')),false);}
 finally{fs.unlinkSync(file);fs.rmdirSync(dir);}
});
test('a live controller lock is refused and the original lock remains intact',async()=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'ccs-demo-lock-')),file=path.join(dir,'controller.lock'),body=JSON.stringify({format:'closing-count-demo-controller-lock',version:1,pid:process.pid,token:randomUUID()});fs.writeFileSync(file,body);
 try{await assert.rejects(controllerLock(dir,async()=>assert.fail('must not execute')));assert.equal(fs.readFileSync(file,'utf8'),body);}
 finally{fs.unlinkSync(file);fs.rmdirSync(dir);}
});
test('foreign lock content is preserved and an owned temporary lock is removed normally',async()=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'ccs-demo-foreign-lock-')),file=path.join(dir,'controller.lock'),foreign=JSON.stringify({pid:2147483647,token:'not-owned'});fs.writeFileSync(file,foreign);
 try{
  await assert.rejects(controllerLock(dir,async()=>assert.fail('must not execute')));assert.equal(fs.readFileSync(file,'utf8'),foreign);fs.unlinkSync(file);
  await controllerLock(dir,async()=>{assert.equal(JSON.parse(fs.readFileSync(file,'utf8')).format,'closing-count-demo-controller-lock');});assert.equal(fs.existsSync(file),false);
 }finally{if(fs.existsSync(file))fs.unlinkSync(file);fs.rmdirSync(dir);}
});
