import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {randomBytes,randomUUID} from 'node:crypto';
import {configFromEnvironment} from '../service/src/config.mjs';
export const packageRoot=fs.realpathSync(fileURLToPath(new URL('..',import.meta.url)));
export function stateDirectory(value) {
  const base=value||path.join(process.env.LOCALAPPDATA||process.env.XDG_STATE_HOME||path.join(process.env.HOME||'.','.local','state'),'ClosingCountDemo');
  if(!path.isAbsolute(base))throw Error('Explicit demo state must be an absolute private directory');
  const result=path.resolve(base),relative=path.relative(packageRoot,result);
  if(!relative||(!relative.startsWith('..'+path.sep)&&relative!=='..'&&!path.isAbsolute(relative)))throw Error('Runtime secrets must stay outside the source/package');
  let ancestor=result;
  while(!fs.existsSync(ancestor)) {const parent=path.dirname(ancestor);if(parent===ancestor)throw Error('Demo state parent cannot be resolved');ancestor=parent;}
  const resolved=path.resolve(fs.realpathSync(ancestor),path.relative(ancestor,result)),physicalRelative=path.relative(packageRoot,resolved);
  if(!physicalRelative||(!physicalRelative.startsWith('..'+path.sep)&&physicalRelative!=='..'&&!path.isAbsolute(physicalRelative)))throw Error('Runtime state resolves inside the source/package');
  // Refuse a linked directory that could redirect writes into the source.
  if(fs.existsSync(result)) {
    if(!fs.lstatSync(result).isDirectory()||fs.lstatSync(result).isSymbolicLink())throw Error('Refusing linked or non-directory demo state');
    const real=fs.realpathSync(result),rel=path.relative(packageRoot,real);
    if(!rel||(!rel.startsWith('..'+path.sep)&&rel!=='..'&&!path.isAbsolute(rel)))throw Error('Runtime state resolves inside the source/package');
  }
  return result;
}
export function readJson(file){return JSON.parse(fs.readFileSync(file,'utf8'));}
export function atomicJson(file,value) {
  const temp=file+'.'+randomUUID()+'.tmp';fs.writeFileSync(temp,JSON.stringify(value,null,2)+'\n',{flag:'wx',mode:0o600});
  fs.renameSync(temp,file);
}
export function loadState(directory,{create=false}={}) {
  const dir=stateDirectory(directory),file=path.join(dir,'instance.private.json'),passwordFile=path.join(dir,'postgres-password.private.txt');
  if(!fs.existsSync(file)) {
    if(!create)throw Error('Run init with this private state directory first');
    if(fs.existsSync(dir)&&fs.readdirSync(dir).some(n=>n!=='controller.lock'))throw Error('Refusing nonempty unowned demo state');
    fs.mkdirSync(dir,{recursive:true,mode:0o700});
    const value={format:'closing-count-local-demo-private',version:1,instance:randomBytes(24).toString('hex'),ownerPassword:randomBytes(32).toString('hex'),appPassword:randomBytes(32).toString('hex')};
    fs.writeFileSync(passwordFile,value.ownerPassword,{flag:'wx',mode:0o600});atomicJson(file,value);
  }
  if(fs.lstatSync(file).isSymbolicLink()||!fs.lstatSync(file).isFile()||fs.lstatSync(passwordFile).isSymbolicLink())throw Error('Refusing linked private configuration');
  const value=readJson(file);
  if(value.format!=='closing-count-local-demo-private'||value.version!==1||!/^[a-f0-9]{48}$/.test(value.instance)||!['ownerPassword','appPassword'].every(k=>/^[a-f0-9]{64}$/.test(value[k]))||fs.readFileSync(passwordFile,'utf8')!==value.ownerPassword)throw Error('Invalid private demo configuration');
  const config=configFromEnvironment({CCS_ENVIRONMENT:'DEMO',CCS_INSTANCE:value.instance,CCS_DATABASE_URL:`postgresql://ccs_portfolio_app:${value.appPassword}@127.0.0.1:55437/closing_count_portfolio_demo`});
  return {dir,file,passwordFile,value,config};
}
export async function controllerLock(directory,action) {
  const dir=stateDirectory(directory);fs.mkdirSync(dir,{recursive:true,mode:0o700});
  const file=path.join(dir,'controller.lock'),token=randomUUID();
  if(fs.existsSync(file)) {
    if(fs.lstatSync(file).isSymbolicLink())throw Error('Refusing linked controller lock');
    const old=readJson(file);if(old.format!=='closing-count-demo-controller-lock'||old.version!==1||!/^[a-f0-9-]{36}$/.test(old.token||'')||!Number.isInteger(old.pid)||old.pid<1)throw Error('Unknown controller lock');
    let alive=true;try{process.kill(old.pid,0);}catch(e){if(e.code==='ESRCH')alive=false;else throw Error('Controller lock owner cannot be checked');}
    if(alive)throw Error('Another local demo command is still running');
    fs.unlinkSync(file);
  }
  fs.writeFileSync(file,JSON.stringify({format:'closing-count-demo-controller-lock',version:1,pid:process.pid,token}),{flag:'wx',mode:0o600});
  try{return await action();}finally{if(readJson(file).token!==token)throw Error('Controller lock changed');fs.unlinkSync(file);}
}
