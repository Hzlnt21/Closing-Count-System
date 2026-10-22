import {createHash,randomUUID} from 'node:crypto';
import {ApiError,exactObject,forbidden,invalid} from './errors.mjs';
import {uuid,revision,normalizeCatalog,closingSnapshot,ingredientTotals,businessDateInput,emptyCatalog} from './catalog.mjs';
import {createLifecycleRoutes} from './lifecycle-routes.mjs';

const conflict=(code,message,details)=>new ApiError(409,code,message,details);
const hash=value=>createHash('sha256').update(JSON.stringify(value)).digest('hex');
const bump=value=>{const n=Number(value);if(!Number.isSafeInteger(n)||n>=Number.MAX_SAFE_INTEGER)throw conflict('REVISION_LIMIT','Batas revisi tercapai. Hubungi pengelola.');return n+1;};
const count=value=>{if(!Number.isInteger(value)||value<0||value>9999)throw invalid('Jumlah harus bilangan bulat 0 sampai 9999.');return value;};
function entryDto(row){return {menuId:row.menu_id,quantity:row.quantity,entryRevision:Number(row.revision),
  updatedBy:row.updated_by?{id:row.updated_by,username:row.username,displayName:row.display_name}:null,updatedAt:row.updated_at};}

export function createClosingRoutes({pool,authenticate,member,readJson,transaction}){
  async function access(db,req,outletId,exclusive=false){
    // All mutations lock outlet -> actor -> closing -> entry. This serializes
    // membership changes and catalog publication against their permission checks.
    await db.query('SELECT id FROM ccs.outlets WHERE id=$1 FOR '+(exclusive?'UPDATE':'SHARE'),[outletId]);
    let actor=await authenticate(req,db);
    await db.query('SELECT id FROM ccs.accounts WHERE id=$1 FOR SHARE',[actor.id]);
    actor=await authenticate(req,db);const membership=await member(db,actor.id,outletId);
    return {actor,membership};
  }
  async function lockOperation(db,outletId,actorId,operationId){
    await db.query("SELECT pg_advisory_xact_lock(hashtextextended($1,0))",['ccs-operation:'+outletId+':'+actorId+':'+operationId]);
  }
  async function receipt(db,outletId,actorId,operationId,fingerprint){
    const {rows:[saved]}=await db.query('SELECT fingerprint,response FROM ccs.operation_receipts WHERE outlet_id=$1 AND actor_id=$2 AND operation_id=$3',[outletId,actorId,operationId]);
    if(saved&&saved.fingerprint!==fingerprint)throw conflict('OPERATION_ID_REUSED','ID perubahan telah dipakai untuk data yang berbeda.');
    return saved?.response;
  }
  async function saveReceipt(db,outletId,actorId,operationId,fingerprint,response){
    await db.query('INSERT INTO ccs.operation_receipts(outlet_id,actor_id,operation_id,fingerprint,response) VALUES ($1,$2,$3,$4,$5::jsonb)',[outletId,actorId,operationId,fingerprint,JSON.stringify(response)]);
  }
  async function audit(db,closing,actor,action,operationId=null,change={}){
    await db.query(`INSERT INTO ccs.closing_audit(closing_id,outlet_id,actor_id,actor_username,actor_name,operation_id,action,
      menu_id,before_quantity,after_quantity,entry_revision,closing_revision,generation,reason)
      VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14)`,[closing.id,closing.outlet_id,actor.id,actor.username,actor.display_name,
      operationId,action,change.menuId??null,change.before??null,change.after??null,change.entryRevision??null,closing.revision,closing.generation,change.reason??null]);
  }
  async function header(db,id,lock=false){
    const {rows:[row]}=await db.query(`SELECT id,outlet_id,business_date::text,state,revision,generation,catalog_version_id,snapshot,
      timezone,cutoff_minutes,created_at,updated_at,submitted_by,submitted_at,submitted_actor,locked_by,locked_at,locked_actor FROM ccs.closings WHERE id=$1${lock?' FOR UPDATE':''}`,[id]);
    if(!row)throw forbidden();return row;
  }
  async function closingView(db,row){
    const entries=(await db.query(`SELECT e.*,u.username,u.display_name FROM ccs.closing_entries e
      LEFT JOIN ccs.accounts u ON u.id=e.updated_by WHERE e.closing_id=$1 ORDER BY e.menu_id`,[row.id])).rows.map(entryDto);
    return {id:row.id,outletId:row.outlet_id,businessDate:row.business_date,state:row.state,closingRevision:Number(row.revision),
      generation:Number(row.generation),catalogVersionId:row.catalog_version_id,catalog:row.snapshot,timezone:row.timezone,
      cutoffMinutes:row.cutoff_minutes,submittedBy:row.submitted_actor,submittedAt:row.submitted_at,lockedBy:row.locked_actor,lockedAt:row.locked_at,
      entries,ingredientTotals:ingredientTotals(row.snapshot,entries),createdAt:row.created_at,updatedAt:row.updated_at};
  }
  async function permittedClosing(req,actor,id){const row=await header(pool,id);await member(pool,actor.id,row.outlet_id);return row;}
  const lifecycleRoutes=createLifecycleRoutes({pool,authenticate,member,readJson,transaction,access,lockOperation,receipt,saveReceipt,audit,header,closingView});
  return async(req,url,outerActor)=>{
    let match=/^\/api\/v1\/outlets\/([^/]+)\/(catalog|closings)$/.exec(url.pathname);
    if(match){
      const outletId=uuid(match[1]),kind=match[2];await member(pool,outerActor.id,outletId);
      if(kind==='catalog'&&req.method==='GET'){
        const {rows:[row]}=await pool.query('SELECT id,revision,snapshot,created_at FROM ccs.catalog_versions WHERE outlet_id=$1 ORDER BY revision DESC LIMIT 1',[outletId]);
        return {status:200,data:row?{versionId:row.id,revision:Number(row.revision),catalog:row.snapshot,createdAt:row.created_at}:{versionId:null,revision:0,catalog:emptyCatalog()}};
      }
      if(kind==='catalog'&&req.method==='POST'){
        await member(pool,outerActor.id,outletId,true);
        const body=await readJson(req,524288);exactObject(body,['operationId','expectedRevision','catalog']);
        const operationId=uuid(body.operationId),expectedRevision=revision(body.expectedRevision),catalog=normalizeCatalog(body.catalog);
        const fingerprint=hash({kind:'CATALOG',outletId,expectedRevision,catalog});
        const data=await transaction(pool,async db=>{
          // Receipt lock first across all resource kinds, then resource locks.
          await lockOperation(db,outletId,outerActor.id,operationId);
          const {actor,membership}=await access(db,req,outletId,true);if(membership.role!=='ADMIN')throw forbidden();
          const replay=await receipt(db,outletId,actor.id,operationId,fingerprint);
          if(replay)return replay;
          const {rows:[latest]}=await db.query('SELECT revision FROM ccs.catalog_versions WHERE outlet_id=$1 ORDER BY revision DESC LIMIT 1',[outletId]);
          if(Number(latest?.revision??0)!==expectedRevision)throw conflict('CATALOG_CONFLICT','Katalog sudah berubah. Muat versi terbaru.',{currentRevision:Number(latest?.revision??0)});
          const versionId=randomUUID(),next=bump(expectedRevision);
          const {rows:[saved]}=await db.query('INSERT INTO ccs.catalog_versions(id,outlet_id,revision,snapshot,created_by) VALUES ($1,$2,$3,$4::jsonb,$5) RETURNING created_at',[versionId,outletId,next,JSON.stringify(catalog),actor.id]);
          const response={versionId,revision:next,catalog,createdAt:saved.created_at};
          await saveReceipt(db,outletId,actor.id,operationId,fingerprint,response);return response;
        });
        return {status:200,data};
      }
      if(kind==='closings'&&req.method==='POST'){
        const body=await readJson(req);exactObject(body,['businessDate']);const date=businessDateInput(body.businessDate);
        return transaction(pool,async db=>{
          const {actor,membership}=await access(db,req,outletId,true);
          const {rows:[existing]}=await db.query('SELECT id FROM ccs.closings WHERE outlet_id=$1 AND business_date=$2',[outletId,date]);
          if(existing)return {status:200,data:await closingView(db,await header(db,existing.id))};
          const {rows:[catalog]}=await db.query('SELECT id,snapshot FROM ccs.catalog_versions WHERE outlet_id=$1 ORDER BY revision DESC LIMIT 1',[outletId]);
          if(!catalog)throw conflict('CATALOG_REQUIRED','Pengelola perlu menyiapkan katalog terlebih dahulu.');
          const snapshot=closingSnapshot(catalog.snapshot);if(!snapshot.menus.length)throw conflict('CATALOG_EMPTY','Belum ada menu aktif untuk closing.');
          const id=randomUUID();
          await db.query(`INSERT INTO ccs.closings(id,outlet_id,business_date,catalog_version_id,snapshot,timezone,cutoff_minutes,created_by)
            VALUES ($1,$2,$3,$4,$5::jsonb,$6,$7,$8)`,[id,outletId,date,catalog.id,JSON.stringify(snapshot),membership.timezone,membership.cutoff_minutes,actor.id]);
          await db.query('INSERT INTO ccs.closing_entries(closing_id,menu_id) SELECT $1,unnest($2::uuid[])',[id,snapshot.menus.map(row=>row.id)]);
          const row=await header(db,id);await audit(db,row,actor,'CREATE');return {status:201,data:await closingView(db,row)};
        });
      }
      if(kind==='closings'&&req.method==='GET'){
        const limit=url.searchParams.has('limit')?Number(url.searchParams.get('limit')):20;
        if(!Number.isInteger(limit)||limit<1||limit>100)throw invalid();
        const before=url.searchParams.has('before')?businessDateInput(url.searchParams.get('before')):null;
        const result=await pool.query(`SELECT id,business_date::text,state,revision,generation,created_at,updated_at FROM ccs.closings
          WHERE outlet_id=$1 AND ($2::date IS NULL OR business_date<$2::date) ORDER BY business_date DESC LIMIT $3`,[outletId,before,limit+1]);
        const items=result.rows.slice(0,limit).map(row=>({id:row.id,businessDate:row.business_date,state:row.state,
          closingRevision:Number(row.revision),generation:Number(row.generation),createdAt:row.created_at,updatedAt:row.updated_at}));
        return {status:200,data:{items,nextBefore:result.rowCount>limit?items.at(-1).businessDate:null}};
      }
    }
    const lifecycleResponse=await lifecycleRoutes(req,url,outerActor);if(lifecycleResponse)return lifecycleResponse;
    match=/^\/api\/v1\/closings\/([^/]+)(?:\/(audit|entries)(?:\/([^/]+)\/operations)?)?$/.exec(url.pathname);
    if(!match)return;
    const id=uuid(match[1]),kind=match[2],menuId=match[3]?uuid(match[3]):null;
    const initial=await permittedClosing(req,outerActor,id);
    if(req.method==='GET'&&!kind){
      const data=await transaction(pool,async db=>{const actor=await authenticate(req,db);const row=await header(db,id);await member(db,actor.id,row.outlet_id);return closingView(db,row);},{readOnly:true});
      return {status:200,data};
    }
    if(req.method==='GET'&&kind==='audit'&&!menuId){
      const after=url.searchParams.get('after')??'0',limit=url.searchParams.has('limit')?Number(url.searchParams.get('limit')):50;
      if(!/^\d{1,19}$/.test(after)||BigInt(after)>9223372036854775807n||!Number.isInteger(limit)||limit<1||limit>100)throw invalid();
      const result=await pool.query(`SELECT id::text,actor_id,actor_username,actor_name,operation_id,action,menu_id,before_quantity,
        after_quantity,entry_revision::text,closing_revision::text,generation::text,reason,created_at FROM ccs.closing_audit
        WHERE closing_id=$1 AND id>$2::bigint ORDER BY ccs.closing_audit.id LIMIT $3`,[id,after,limit+1]);
      const items=result.rows.slice(0,limit);return {status:200,data:{items,nextAfter:result.rowCount>limit?items.at(-1).id:null}};
    }
    if(req.method==='POST'&&kind==='entries'&&menuId){
      const body=await readJson(req);exactObject(body,['operationId','generation','expectedEntryRevision','quantity']);
      const operationId=uuid(body.operationId),generation=revision(body.generation,1),expectedEntryRevision=revision(body.expectedEntryRevision),quantity=count(body.quantity);
      const fingerprint=hash({kind:'ENTRY',closingId:id,menuId,generation,expectedEntryRevision,quantity});
      const data=await transaction(pool,async db=>{
        await lockOperation(db,initial.outlet_id,outerActor.id,operationId);
        const {actor}=await access(db,req,initial.outlet_id);const row=await header(db,id,true);
        const replay=await receipt(db,initial.outlet_id,actor.id,operationId,fingerprint);
        if(replay)return replay;
        if(Number(row.generation)!==generation)throw conflict('GENERATION_CONFLICT','Closing telah dibuka untuk revisi baru.',{currentGeneration:Number(row.generation)});
        if(row.state!=='DRAFT')throw conflict('CLOSING_STATE_CONFLICT','Closing sudah diajukan atau dikunci.',{state:row.state});
        const {rows:[entry]}=await db.query(`SELECT e.*,u.username,u.display_name FROM ccs.closing_entries e LEFT JOIN ccs.accounts u ON u.id=e.updated_by
          WHERE e.closing_id=$1 AND e.menu_id=$2 FOR UPDATE OF e`,[id,menuId]);
        if(!entry)throw new ApiError(404,'MENU_NOT_FOUND','Menu tidak terdaftar pada closing ini.');
        if(Number(entry.revision)!==expectedEntryRevision)throw conflict('ENTRY_CONFLICT','Jumlah menu sudah diubah karyawan lain.',{current:entryDto(entry),generation:Number(row.generation),closingRevision:Number(row.revision)});
        let result=entryDto(entry);
        if(entry.quantity!==quantity){
          const entryRevision=bump(entry.revision),closingRevision=bump(row.revision);
          const {rows:[changed]}=await db.query(`UPDATE ccs.closing_entries SET quantity=$3,revision=$4,updated_by=$5,updated_at=clock_timestamp()
            WHERE closing_id=$1 AND menu_id=$2 RETURNING *`,[id,menuId,quantity,entryRevision,actor.id]);
          await db.query('UPDATE ccs.closings SET revision=$2,updated_at=clock_timestamp() WHERE id=$1',[id,closingRevision]);
          await audit(db,{...row,revision:closingRevision},actor,'ENTRY_UPDATED',operationId,{menuId,before:entry.quantity,after:quantity,entryRevision});
          result=entryDto({...changed,username:actor.username,display_name:actor.display_name});row.revision=closingRevision;
        }
        const response={closingId:id,generation:Number(row.generation),closingRevision:Number(row.revision),entry:result};
        await saveReceipt(db,row.outlet_id,actor.id,operationId,fingerprint,response);return response;
      });
      return {status:200,data};
    }
  };
}
