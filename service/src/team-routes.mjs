import {createHash,randomUUID} from 'node:crypto';
import {ApiError,exactObject,forbidden,invalid} from './errors.mjs';
import {uuid,revision} from './catalog.mjs';
import {normalizeUsername,validatePassword,hashPasswordWithSalt} from './passwords.mjs';
const roles=new Set(['ADMIN','SUPERVISOR','EMPLOYEE']);
const conflict=code=>new ApiError(409,code,'Data atau izin sudah berubah. Periksa kembali.');
const dto=row=>({id:row.account_id,username:row.username,displayName:row.display_name,role:row.role,active:row.active,accountActive:row.account_active,revision:Number(row.revision)});
const select=`SELECT m.account_id,u.username,u.display_name,m.role,m.active,u.active AS account_active,m.revision
 FROM ccs.memberships m JOIN ccs.accounts u ON u.id=m.account_id`;

export function createTeamRoutes({pool,authenticate,member,readJson,transaction}){
 return async(req,url,outerActor)=>{
  const match=/^\/api\/v1\/outlets\/([^/]+)\/(team|team-operations)(?:\/([^/]+))?$/.exec(url.pathname);
  if(!match)return;const outletId=uuid(match[1]),kind=match[2],subject=match[3]?uuid(match[3]):null;
  if(req.method==='GET'&&kind==='team'){
   const limit=url.searchParams.has('limit')?Number(url.searchParams.get('limit')):30;
   const after=url.searchParams.get('after');if(!Number.isInteger(limit)||limit<1||limit>100)throw invalid();
   if(after!==null&&normalizeUsername(after)!==after)throw invalid();
   const data=await transaction(pool,async db=>{
    await db.query('SELECT id FROM ccs.outlets WHERE id=$1 FOR SHARE',[outletId]);
    let actor=await authenticate(req,db);await db.query('SELECT id FROM ccs.accounts WHERE id=$1 FOR SHARE',[actor.id]);actor=await authenticate(req,db);
    await member(db,actor.id,outletId,true);
    if(subject){const {rows:[row]}=await db.query(select+' WHERE m.outlet_id=$1 AND m.account_id=$2',[outletId,subject]);if(!row)throw new ApiError(404,'MEMBER_NOT_FOUND','Anggota tidak ditemukan.');return dto(row);}
    const result=await db.query(select+' WHERE m.outlet_id=$1 AND ($2::text IS NULL OR u.username COLLATE "C">$2::text COLLATE "C") ORDER BY u.username COLLATE "C" LIMIT $3',[outletId,after,limit+1]);
    const items=result.rows.slice(0,limit).map(dto);return{items,nextAfter:result.rowCount>limit?items.at(-1).username:null};
   });return{status:200,data};
  }
  async function lock(db,operationId){
   await db.query('SELECT pg_advisory_xact_lock(hashtextextended($1,0))',['ccs-operation:'+outletId+':'+outerActor.id+':'+operationId]);
   await db.query('SELECT id FROM ccs.outlets WHERE id=$1 FOR UPDATE',[outletId]);
   let actor=await authenticate(req,db);await db.query('SELECT id FROM ccs.accounts WHERE id=$1 FOR SHARE',[actor.id]);return authenticate(req,db);
  }
  if(req.method==='GET'&&kind==='team-operations'&&subject){
   const data=await transaction(pool,async db=>{
    const actor=await lock(db,subject);
    const {rows:[receipt]}=await db.query('SELECT response FROM ccs.operation_receipts WHERE outlet_id=$1 AND actor_id=$2 AND operation_id=$3',[outletId,actor.id,subject]);
    // A former administrator may reconcile ONLY their own access-loss action.
    // No outlet name, other members or general outlet access is granted by this exception.
    const ownLoss=receipt?.response.kind==='UPDATE_MEMBERSHIP'&&receipt.response.member.id===actor.id&&
      (!receipt.response.member.active||receipt.response.member.role!=='ADMIN');
    if(!ownLoss)await member(db,actor.id,outletId,true);
    if(!receipt)throw new ApiError(404,'TEAM_OPERATION_NOT_FOUND','Tindakan belum terkonfirmasi.');
    if(!['CREATE_ACCOUNT','UPDATE_MEMBERSHIP'].includes(receipt.response.kind))throw conflict('OPERATION_ID_REUSED');
    return receipt.response;
   });return{status:200,data};
  }
  if(req.method!=='POST'||kind!=='team-operations'||subject)return;
  await member(pool,outerActor.id,outletId,true);
  const body=await readJson(req);exactObject(body,['operationId','kind','payload']);
  const operationId=uuid(body.operationId);let payload,passwordHash;
  if(body.kind==='CREATE_ACCOUNT'){
   exactObject(body.payload,['username','displayName','role','password']);const p=body.payload;
   if(typeof p.displayName!=='string'||p.displayName.trim().length<1||p.displayName.trim().length>120||!roles.has(p.role))throw invalid();
   validatePassword(p.password);
   // Password-derived receipt fingerprint has scrypt cost, never a fast password digest.
   const salt=createHash('sha256').update(`ccs-team-password:${outletId}:${outerActor.id}:${operationId}`).digest('hex').slice(0,32);
   passwordHash=await hashPasswordWithSalt(p.password,salt);
   payload={username:normalizeUsername(p.username),displayName:p.displayName.trim(),role:p.role,passwordHash};
  }else if(body.kind==='UPDATE_MEMBERSHIP'){
   exactObject(body.payload,['accountId','expectedRevision','role','active']);const p=body.payload;
   if(!roles.has(p.role)||typeof p.active!=='boolean')throw invalid();
   payload={accountId:uuid(p.accountId),expectedRevision:revision(p.expectedRevision),role:p.role,active:p.active};
  }else throw invalid();
  const fingerprint=createHash('sha256').update(JSON.stringify({kind:body.kind,outletId,...payload})).digest('hex');
  const data=await transaction(pool,async db=>{
   const actor=await lock(db,operationId);await member(db,actor.id,outletId,true);
   const {rows:[old]}=await db.query('SELECT fingerprint,response FROM ccs.operation_receipts WHERE outlet_id=$1 AND actor_id=$2 AND operation_id=$3',[outletId,actor.id,operationId]);
   if(old){if(old.fingerprint!==fingerprint)throw conflict('OPERATION_ID_REUSED');return old.response;}
   let id,changed=true;
   if(body.kind==='CREATE_ACCOUNT'){
    id=randomUUID();await db.query('INSERT INTO ccs.accounts(id,username,display_name,password_hash) VALUES ($1,$2,$3,$4)',[id,payload.username,payload.displayName,passwordHash]);
    await db.query('INSERT INTO ccs.memberships(outlet_id,account_id,role) VALUES ($1,$2,$3)',[outletId,id,payload.role]);
    await db.query("INSERT INTO ccs.membership_audit(outlet_id,actor_id,subject_id,action,after_value) VALUES ($1,$2,$3,'CREATE_ACCOUNT',$4::jsonb)",[outletId,actor.id,id,JSON.stringify({username:payload.username,role:payload.role,active:true})]);
   }else{
    id=payload.accountId;const {rows:[before]}=await db.query(select+' WHERE m.outlet_id=$1 AND m.account_id=$2 FOR UPDATE OF m',[outletId,id]);
    if(!before)throw new ApiError(404,'MEMBER_NOT_FOUND','Anggota tidak ditemukan.');
    if(Number(before.revision)!==payload.expectedRevision)throw conflict('MEMBERSHIP_CONFLICT');
    changed=before.role!==payload.role||before.active!==payload.active;
    if(changed&&before.role==='ADMIN'&&before.active&&before.account_active&&(!payload.active||payload.role!=='ADMIN')){
     const {rows:[count]}=await db.query("SELECT count(*)::int AS n FROM ccs.memberships m JOIN ccs.accounts u ON u.id=m.account_id WHERE m.outlet_id=$1 AND m.role='ADMIN' AND m.active AND u.active",[outletId]);
     if(count.n<=1)throw new ApiError(409,'LAST_ADMIN_REQUIRED','Minimal satu administrator aktif harus dipertahankan.');
    }
    if(changed){
     if(payload.expectedRevision>=Number.MAX_SAFE_INTEGER)throw conflict('REVISION_LIMIT');
     await db.query('UPDATE ccs.memberships SET role=$3,active=$4,revision=revision+1,updated_at=clock_timestamp() WHERE outlet_id=$1 AND account_id=$2',[outletId,id,payload.role,payload.active]);
     await db.query("INSERT INTO ccs.membership_audit(outlet_id,actor_id,subject_id,action,before_value,after_value) VALUES ($1,$2,$3,'UPDATE_MEMBERSHIP',$4::jsonb,$5::jsonb)",[outletId,actor.id,id,JSON.stringify({role:before.role,active:before.active}),JSON.stringify({role:payload.role,active:payload.active})]);
    }
   }
   const {rows:[row]}=await db.query(select+' WHERE m.outlet_id=$1 AND m.account_id=$2',[outletId,id]);
   const response={outletId,operationId,kind:body.kind,changed,member:dto(row)};
   await db.query('INSERT INTO ccs.operation_receipts(outlet_id,actor_id,operation_id,fingerprint,response) VALUES ($1,$2,$3,$4,$5::jsonb)',[outletId,actor.id,operationId,fingerprint,JSON.stringify(response)]);
   return response;
  });return{status:200,data};
 };
}
