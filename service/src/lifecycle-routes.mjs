import {createHash,randomUUID} from 'node:crypto';
import {ApiError,invalid} from './errors.mjs';
import {uuid,revision} from './catalog.mjs';
import {lifecycleInput,lifecycleRole,lifecycleTransition} from './lifecycle.mjs';
const actorDto=actor=>({id:actor.id,username:actor.username,displayName:actor.display_name});
const ack=row=>({closingId:row.id,state:row.state,generation:Number(row.generation),closingRevision:Number(row.revision),
  submittedBy:row.submitted_actor,submittedAt:row.submitted_at,lockedBy:row.locked_actor,lockedAt:row.locked_at});
export function createLifecycleRoutes({pool,authenticate,member,readJson,transaction,access,lockOperation,receipt,saveReceipt,audit,header,closingView}){
  return async(req,url,outerActor)=>{
    const match=/^\/api\/v1\/closings\/([^/]+)\/(submit|return|lock|reopen|reports)(?:\/([^/]+))?$/.exec(url.pathname);
    if(!match)return;
    const id=uuid(match[1]),action=match[2],reportGeneration=match[3];
    const initial=await header(pool,id),outerMembership=await member(pool,outerActor.id,initial.outlet_id);
    if(action==='reports'&&req.method==='GET'){
      if(reportGeneration){
        if(!/^[1-9]\d{0,15}$/.test(reportGeneration))throw invalid();const generation=revision(Number(reportGeneration),1);
        const {rows:[row]}=await pool.query('SELECT snapshot FROM ccs.closing_reports WHERE closing_id=$1 AND generation=$2',[id,generation]);
        if(!row)throw new ApiError(404,'REPORT_NOT_FOUND','Laporan generasi ini belum tersedia.');
        return {status:200,data:row.snapshot};
      }
      const before=url.searchParams.has('beforeGeneration')?revision(Number(url.searchParams.get('beforeGeneration')),1):null;
      const limit=url.searchParams.has('limit')?Number(url.searchParams.get('limit')):20;
      if(!Number.isInteger(limit)||limit<1||limit>100)throw invalid();
      const result=await pool.query(`SELECT id,generation,closing_revision,locked_at,snapshot->'reviewedBy' AS reviewer FROM ccs.closing_reports
        WHERE closing_id=$1 AND ($2::bigint IS NULL OR generation<$2::bigint) ORDER BY generation DESC LIMIT $3`,[id,before,limit+1]);
      const items=result.rows.slice(0,limit).map(row=>({id:row.id,generation:Number(row.generation),closingRevision:Number(row.closing_revision),reviewedAt:row.locked_at,reviewedBy:row.reviewer}));
      return {status:200,data:{items,nextBeforeGeneration:result.rowCount>limit?items.at(-1).generation:null}};
    }
    if(req.method!=='POST'||reportGeneration||action==='reports')return;
    lifecycleRole(action,outerMembership.role);
    const body=lifecycleInput(action,await readJson(req));
    const fingerprint=createHash('sha256').update(JSON.stringify({kind:'LIFECYCLE',action,closingId:id,...body,operationId:undefined})).digest('hex');
    const data=await transaction(pool,async db=>{
      await lockOperation(db,initial.outlet_id,outerActor.id,body.operationId);
      const {actor,membership}=await access(db,req,initial.outlet_id);lifecycleRole(action,membership.role);
      const row=await header(db,id,true),replay=await receipt(db,initial.outlet_id,actor.id,body.operationId,fingerprint);
      if(replay)return replay;
      const next=lifecycleTransition(action,row,body,membership.role),actorSnapshot=JSON.stringify(actorDto(actor));
      if(action==='submit'){
        await db.query(`UPDATE ccs.closings SET state=$2,revision=$3,generation=$4,submitted_by=$5,submitted_at=clock_timestamp(),
          submitted_actor=$6::jsonb,updated_at=clock_timestamp() WHERE id=$1`,[id,next.state,next.revision,next.generation,actor.id,actorSnapshot]);
      }else if(action==='lock'){
        await db.query(`UPDATE ccs.closings SET state=$2,revision=$3,generation=$4,locked_by=$5,locked_at=clock_timestamp(),
          locked_actor=$6::jsonb,updated_at=clock_timestamp() WHERE id=$1`,[id,next.state,next.revision,next.generation,actor.id,actorSnapshot]);
      }else{
        await db.query(`UPDATE ccs.closings SET state=$2,revision=$3,generation=$4,submitted_by=NULL,submitted_at=NULL,
          submitted_actor=NULL,locked_by=NULL,locked_at=NULL,locked_actor=NULL,updated_at=clock_timestamp() WHERE id=$1`,[id,next.state,next.revision,next.generation]);
      }
      const changed=await header(db,id);
      let report=null;
      if(action==='lock'){
        const snapshot=await closingView(db,changed),reportId=randomUUID();
        const payload={format:'closing-count-report',schemaVersion:1,id:reportId,closingId:id,outletId:row.outlet_id,
          generation:next.generation,closingRevision:next.revision,reviewedBy:actorDto(actor),reviewedAt:changed.locked_at,closing:snapshot};
        await db.query(`INSERT INTO ccs.closing_reports(id,closing_id,outlet_id,generation,closing_revision,locked_by,locked_at,snapshot)
          VALUES ($1,$2,$3,$4,$5,$6,$7,$8::jsonb)`,[reportId,id,row.outlet_id,next.generation,next.revision,actor.id,changed.locked_at,JSON.stringify(payload)]);
        report={id:reportId,generation:next.generation,closingRevision:next.revision,reviewedBy:actorDto(actor),reviewedAt:changed.locked_at};
      }
      await audit(db,changed,actor,action.toUpperCase(),body.operationId,{reason:body.reason});
      const response={...ack(changed),report};
      await saveReceipt(db,row.outlet_id,actor.id,body.operationId,fingerprint,response);return response;
    });
    return {status:200,data};
  };
}
