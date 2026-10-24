import {execFileSync,spawnSync} from 'node:child_process';
import net from 'node:net';
import fs from 'node:fs';
import {createHash} from 'node:crypto';
import pg from 'pg';
import {assertOwnedDatabase} from '../src/config.mjs';
import {migrate} from '../src/migrate.mjs';
import {loadState} from '../../demo/private-state.mjs';
export const container='ccs-portfolio-demo-pg16',volume=container+'-data',label='com.exvelabs.closingcount.demo';
// Version1 keeps its existing names and data. Fresh private identities never
// adopt those resources; version2 derives a separate namespace from its marker.
export function resourcesFor(state) {
  if(!/^[a-f0-9]{48}$/.test(state.value.instance)||![1,2].includes(state.value.version))throw Error('Unknown demo resource identity');
  const name=state.value.version===1?container:'ccs-demo-'+createHash('sha256').update(state.value.instance).digest('hex').slice(0,24)+'-pg16';
  return{container:name,volume:name+'-data'};
}
const docker=(...args)=>execFileSync('docker',args,{encoding:'utf8',timeout:30000,stdio:['ignore','pipe','pipe']}).trim();
export function inspect(kind,name) {
  const r=spawnSync('docker',[kind,'inspect',name],{encoding:'utf8',timeout:15000});
  if(r.status===0)return JSON.parse(r.stdout)[0];
  if(/No such/i.test(r.stderr||''))return null;
  throw Error('Local Docker inspection failed');
}
export function requireLocalDocker() {
  if(process.env.DOCKER_HOST||process.env.DOCKER_CONTEXT)throw Error('Refusing overridden Docker target');
  const context=docker('context','show'),info=JSON.parse(docker('context','inspect',context))[0];
  if(!/^(npipe:|unix:)/.test(info.Endpoints.docker.Host))throw Error('Require a local Docker context');
  docker('info','--format','{{.ServerVersion}}');
}
const normalized=value=>value.replaceAll('\\','/').replace(/^\/run\/desktop\/mnt\/host\/([a-z])\//i,(_,drive)=>drive+':/').toLowerCase();
export function validateContainer(info,v,state) {
  const resources=resourcesFor(state);
  const ports=info?.HostConfig?.PortBindings,bindings=ports?.['5432/tcp'];
  if(info?.Name!=='/'+resources.container||info?.Config?.Labels?.[label]!==state.value.instance||info.Config.Image!=='postgres:16'||info.HostConfig.RestartPolicy.Name!=='no'
    ||info.HostConfig.Privileged||info.HostConfig.Memory!==384*1024*1024||info.HostConfig.NanoCpus!==750000000
    ||info.Mounts.length!==2||Object.keys(ports||{}).length!==1||bindings?.length!==1||bindings[0].HostIp!=='127.0.0.1'||bindings[0].HostPort!=='55437'
    ||!info.Mounts.some(m=>m.Type==='volume'&&m.Name===resources.volume&&m.Destination==='/var/lib/postgresql/data')
    ||!info.Mounts.some(m=>m.Type==='bind'&&normalized(m.Source)===normalized(state.passwordFile)&&m.Destination==='/run/secrets/pg-password'&&!m.RW)
    ||v?.Labels?.[label]!==state.value.instance)throw Error('Refusing unowned or unexpected local demo resources');
}
export function assertContainer(state) {
  requireLocalDocker();const resources=resourcesFor(state),info=inspect('container',resources.container),v=inspect('volume',resources.volume);
  validateContainer(info,v,state);return info;
}
export const appPool=state=>new pg.Pool({connectionString:state.config.databaseUrl,max:4,connectionTimeoutMillis:5000,statement_timeout:10000});
export const portOpen=port=>new Promise((resolve,reject)=>{const socket=net.connect({host:'127.0.0.1',port});socket.setTimeout(5000);socket.once('connect',()=>{socket.destroy();resolve(true);});socket.once('timeout',()=>{socket.destroy();reject(Error('Local port state unknown'));});socket.once('error',e=>e.code==='ECONNREFUSED'?resolve(false):reject(Error('Local port state unknown')));});
export async function initializeDatabase(directory) {
  requireLocalDocker();
  if(!inspect('image','postgres:16'))throw Error('Local postgres:16 image is required');
  if(!fs.existsSync(directory+'/instance.private.json')&&await portOpen(55437))throw Error('Demo database port is occupied; refusing another service');
  const state=loadState(directory,{create:true});
  const resources=resourcesFor(state);let info=inspect('container',resources.container);
  if(info)validateContainer(info,inspect('volume',resources.volume),state);
  if(!info?.State.Running&&await portOpen(55437))throw Error('Demo database port is occupied; refusing another service');
  if(info) {
    if(!info.State.Running)docker('start',resources.container);
  } else {
    const v=inspect('volume',resources.volume);if(v&&v.Labels?.[label]!==state.value.instance)throw Error('Refusing foreign demo volume');
    if(!v)docker('volume','create','--label',`${label}=${state.value.instance}`,resources.volume);
    docker('run','--detach','--name',resources.container,'--restart','no','--label',`${label}=${state.value.instance}`,'--memory','384m','--cpus','0.75',
      '--publish','127.0.0.1:55437:5432','--mount',`type=volume,source=${resources.volume},target=/var/lib/postgresql/data`,
      '--mount',`type=bind,source=${state.passwordFile},target=/run/secrets/pg-password,readonly`,
      '--env','POSTGRES_USER=ccs_demo_owner','--env','POSTGRES_DB=postgres','--env','POSTGRES_PASSWORD_FILE=/run/secrets/pg-password','postgres:16');
    validateContainer(inspect('container',resources.container),inspect('volume',resources.volume),state);
  }
  let admin;
  for(let attempt=0;attempt<30;attempt++) {
    admin=new pg.Client({host:'127.0.0.1',port:55437,database:'postgres',user:'ccs_demo_owner',password:state.value.ownerPassword,connectionTimeoutMillis:1000});
    try{await admin.connect();break;}catch{await admin.end().catch(()=>{});admin=null;await new Promise(r=>setTimeout(r,400));}
  }
  if(!admin)throw Error('Owned demo database did not become ready');
  try {
    const {rows:[identity]}=await admin.query('SELECT current_database() AS db,current_user AS role');
    if(identity.db!=='postgres'||identity.role!=='ccs_demo_owner')throw Error('Unexpected local demo owner');
    const role=await admin.query("SELECT rolsuper,rolcreatedb,rolcreaterole FROM pg_roles WHERE rolname='ccs_portfolio_app'");
    if(!role.rowCount) {
      const {rows:[sql]}=await admin.query("SELECT format('CREATE ROLE ccs_portfolio_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD %L',$1::text) AS command",[state.value.appPassword]);await admin.query(sql.command);
    }else if(role.rows[0].rolsuper||role.rows[0].rolcreatedb||role.rows[0].rolcreaterole)throw Error('Demo application role is not restricted');
    const db=await admin.query("SELECT r.rolname FROM pg_database d JOIN pg_roles r ON r.oid=d.datdba WHERE d.datname='closing_count_portfolio_demo'");
    if(!db.rowCount)await admin.query('CREATE DATABASE closing_count_portfolio_demo OWNER ccs_portfolio_app');
    else if(db.rows[0].rolname!=='ccs_portfolio_app')throw Error('Refusing foreign demo database owner');
  }finally{await admin.end();}
  const marker=new pg.Client({host:'127.0.0.1',port:55437,database:'closing_count_portfolio_demo',user:'ccs_demo_owner',password:state.value.ownerPassword});
  await marker.connect();try {
    const {rows:[table]}=await marker.query("SELECT to_regclass('public.ccs_instance')::text AS name");
    if(!table.name) {
      const tables=await marker.query("SELECT tablename FROM pg_tables WHERE schemaname NOT IN ('pg_catalog','information_schema')");
      if(tables.rowCount)throw Error('Refusing unmarked nonempty demo database');
      await marker.query('BEGIN');await marker.query('CREATE TABLE public.ccs_instance(instance text PRIMARY KEY CHECK(length(instance)=48))');
      await marker.query('INSERT INTO public.ccs_instance VALUES ($1)',[state.value.instance]);
      await marker.query('REVOKE ALL ON public.ccs_instance FROM PUBLIC');await marker.query('GRANT SELECT ON public.ccs_instance TO ccs_portfolio_app');await marker.query('COMMIT');
    }else{const rows=await marker.query('SELECT instance FROM public.ccs_instance');if(rows.rowCount!==1||rows.rows[0].instance!==state.value.instance)throw Error('Refusing foreign demo marker');}
  }finally{await marker.end();}
  const pool=appPool(state);try {await assertOwnedDatabase(pool,state.config);await migrate(pool,state.config);}finally{await pool.end();}
  return state;
}
export function stopDatabase(state) {const info=assertContainer(state);if(info.State.Running)docker('stop','--time','15',resourcesFor(state).container);}
