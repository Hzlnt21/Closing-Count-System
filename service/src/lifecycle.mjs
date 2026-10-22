import {ApiError,exactObject,forbidden,invalid} from './errors.mjs';
import {uuid,revision} from './catalog.mjs';
const states={submit:'DRAFT',return:'SUBMITTED',lock:'SUBMITTED',reopen:'LOCKED'};
const syncStates=new Set(['LOCAL_SAVED','PENDING','SYNCING','SYNCED','CONFLICT','FAILED','AUTH_REQUIRED']);
export function lifecycleInput(action,body){
  const reasonRequired=action==='return'||action==='reopen';
  exactObject(body,['operationId','generation','expectedRevision','deviceSyncState',...(reasonRequired?['reason']:[])]);
  const result={operationId:uuid(body.operationId),generation:revision(body.generation,1),expectedRevision:revision(body.expectedRevision)};
  if(!syncStates.has(body.deviceSyncState))throw invalid();
  if(body.deviceSyncState!=='SYNCED')throw new ApiError(409,'LOCAL_CHANGES_PENDING','Selesaikan sinkronisasi dan konflik di perangkat ini terlebih dahulu.');
  result.deviceSyncState='SYNCED';
  if(reasonRequired){
    if(typeof body.reason!=='string'||!body.reason.trim()||body.reason.trim().length>500)throw invalid('Alasan diperlukan, maksimal 500 karakter.');
    result.reason=body.reason.trim();
  }
  return result;
}
export function lifecycleRole(action,role){
  if(!Object.hasOwn(states,action)||!['EMPLOYEE','SUPERVISOR','ADMIN'].includes(role))throw forbidden();
  if(action!=='submit'&&role==='EMPLOYEE')throw forbidden();
}
export function lifecycleTransition(action,row,input,role){
  lifecycleRole(action,role);
  if(Number(row.generation)!==input.generation)throw new ApiError(409,'GENERATION_CONFLICT','Closing telah masuk revisi baru.',{currentGeneration:Number(row.generation)});
  if(Number(row.revision)!==input.expectedRevision)throw new ApiError(409,'REVISION_CONFLICT','Closing sudah berubah. Periksa data terbaru.',{closingRevision:Number(row.revision),state:row.state,generation:Number(row.generation)});
  if(row.state!==states[action])throw new ApiError(409,'CLOSING_STATE_CONFLICT','Tindakan tidak sesuai tahap closing saat ini.',{state:row.state});
  const increment=value=>{const next=Number(value)+1;if(!Number.isSafeInteger(next))throw new ApiError(409,'REVISION_LIMIT','Batas revisi tercapai. Hubungi pengelola.');return next;};
  return {state:action==='submit'?'SUBMITTED':action==='lock'?'LOCKED':'DRAFT',revision:increment(row.revision),
    generation:action==='return'||action==='reopen'?increment(row.generation):Number(row.generation)};
}
