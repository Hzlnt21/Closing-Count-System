import {exactObject,invalid,isUuid} from './errors.mjs';

export function operationStatusInput(body){
  exactObject(body,['operationIds']);
  if(!Array.isArray(body.operationIds)||body.operationIds.length<1||body.operationIds.length>100||body.operationIds.some(id=>!isUuid(id)))throw invalid();
  const ids=body.operationIds.map(id=>id.toLowerCase());
  if(new Set(ids).size!==ids.length)throw invalid();
  return ids;
}

/** A bounded read query, not a command/replay API. Never returns receipt payloads. */
export function createOperationStatusRoutes({authenticate,member,readJson,transaction,pool}){
  return async(req,url)=>{
    const match=/^\/api\/v1\/outlets\/([^/]+)\/operation-status$/.exec(url.pathname);
    if(!match||req.method!=='POST')return;
    if(!isUuid(match[1])||url.search)throw invalid();
    const outletId=match[1].toLowerCase(),ids=operationStatusInput(await readJson(req,8192));
    const data=await transaction(pool,async db=>{
      // Recheck inside the consistent read snapshot; the outer router actor can be stale.
      const actor=await authenticate(req,db);await member(db,actor.id,outletId);
      const {rows}=await db.query(`SELECT operation_id FROM ccs.operation_receipts
        WHERE outlet_id=$1 AND actor_id=$2 AND operation_id=ANY($3::uuid[])`,[outletId,actor.id,ids]);
      const recorded=new Set(rows.map(row=>row.operation_id));
      return {outletId,items:ids.map(operationId=>({operationId,recorded:recorded.has(operationId)}))};
    },{readOnly:true});
    return {status:200,data};
  };
}
