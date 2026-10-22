import {createHash} from 'node:crypto';
import {ApiError,exactObject,invalid} from './errors.mjs';
import {uuid,revision} from './catalog.mjs';
import {validateBusinessSettings} from './business-date.mjs';

export function createSettingsRoutes({pool,authenticate,member,readJson,transaction}){
 return async(req,url,outerActor)=>{
  const match=/^\/api\/v1\/outlets\/([^/]+)\/settings$/.exec(url.pathname);
  if(!match||req.method!=='POST')return;
  const outletId=uuid(match[1]);await member(pool,outerActor.id,outletId,true);
  const body=await readJson(req);exactObject(body,['operationId','expectedRevision','name','timezone','cutoffMinutes']);
  const operationId=uuid(body.operationId),expectedRevision=revision(body.expectedRevision);
  if(typeof body.name!=='string'||!body.name.trim()||body.name.trim().length>120)throw invalid();
  const name=body.name.trim();validateBusinessSettings(body.timezone,body.cutoffMinutes);
  const value={name,timezone:body.timezone,cutoffMinutes:body.cutoffMinutes};
  const fingerprint=createHash('sha256').update(JSON.stringify({kind:'OUTLET_SETTINGS',outletId,expectedRevision,...value})).digest('hex');
  const data=await transaction(pool,async db=>{
   // Shared operation namespace and lock order with catalog/closing mutations.
   await db.query('SELECT pg_advisory_xact_lock(hashtextextended($1,0))',['ccs-operation:'+outletId+':'+outerActor.id+':'+operationId]);
   await db.query('SELECT id FROM ccs.outlets WHERE id=$1 FOR UPDATE',[outletId]);
   let actor=await authenticate(req,db);await db.query('SELECT id FROM ccs.accounts WHERE id=$1 FOR SHARE',[actor.id]);
   actor=await authenticate(req,db);await member(db,actor.id,outletId,true);
   const {rows:[saved]}=await db.query('SELECT fingerprint,response FROM ccs.operation_receipts WHERE outlet_id=$1 AND actor_id=$2 AND operation_id=$3',[outletId,actor.id,operationId]);
   if(saved){if(saved.fingerprint!==fingerprint)throw new ApiError(409,'OPERATION_ID_REUSED','ID perubahan sudah dipakai.');return saved.response;}
   const {rows:[before]}=await db.query('SELECT name,timezone,cutoff_minutes,settings_revision FROM ccs.outlets WHERE id=$1',[outletId]);
   if(Number(before.settings_revision)!==expectedRevision)throw new ApiError(409,'SETTINGS_CONFLICT','Pengaturan sudah berubah. Periksa versi terbaru.');
   if(expectedRevision>=Number.MAX_SAFE_INTEGER)throw new ApiError(409,'REVISION_LIMIT','Batas revisi tercapai.');
   const next=expectedRevision+1;
   await db.query('UPDATE ccs.outlets SET name=$2,timezone=$3,cutoff_minutes=$4,settings_revision=$5 WHERE id=$1',[outletId,name,value.timezone,value.cutoffMinutes,next]);
   await db.query('INSERT INTO ccs.outlet_settings_audit(outlet_id,actor_id,operation_id,revision,before_value,after_value) VALUES ($1,$2,$3,$4,$5::jsonb,$6::jsonb)',
    [outletId,actor.id,operationId,next,JSON.stringify({name:before.name,timezone:before.timezone,cutoffMinutes:before.cutoff_minutes}),JSON.stringify(value)]);
   const response={outletId,operationId,settingsRevision:next,...value};
   await db.query('INSERT INTO ccs.operation_receipts(outlet_id,actor_id,operation_id,fingerprint,response) VALUES ($1,$2,$3,$4,$5::jsonb)',[outletId,actor.id,operationId,fingerprint,JSON.stringify(response)]);
   return response;
  });
  return {status:200,data};
 };
}
