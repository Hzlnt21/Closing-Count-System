import {createServer} from 'node:http';
import {readFileSync} from 'node:fs';
import {createHash,randomBytes,randomUUID} from 'node:crypto';
import {ApiError,exactObject,forbidden,invalid,isUuid,sessionRequired} from './errors.mjs';
import {hashPassword,normalizeUsername,validatePassword,verifyPassword} from './passwords.mjs';
import {LoginLimiter} from './limiter.mjs';
import {businessDate} from './business-date.mjs';
import {createClosingRoutes} from './closing-routes.mjs';
import {createSettingsRoutes} from './settings-routes.mjs';
import {createTeamRoutes} from './team-routes.mjs';
import {createOperationStatusRoutes} from './operation-status-routes.mjs';

const digest=token=>createHash('sha256').update(token).digest('hex');
export const serviceVersion=JSON.parse(readFileSync(new URL('../package.json',import.meta.url),'utf8')).version;
const roles=new Set(['EMPLOYEE','SUPERVISOR','ADMIN']);
function validateRole(role) { if(!roles.has(role)) throw invalid(); }
function displayName(value) {
  if(typeof value!=='string'||value.trim().length<1||value.trim().length>120) throw invalid();
  return value.trim();
}
async function readJson(req,limit=16384) {
  if(!/^application\/json(?:\s*;\s*charset=utf-8)?$/i.test(req.headers['content-type']||'')) throw new ApiError(415,'JSON_REQUIRED','Gunakan application/json.');
  if(Number(req.headers['content-length'])>limit) throw new ApiError(413,'BODY_TOO_LARGE','Data terlalu besar.');
  const chunks=[];let bytes=0;
  for await(const chunk of req) {
    bytes+=chunk.length;
    if(bytes>limit) throw new ApiError(413,'BODY_TOO_LARGE','Data terlalu besar.');
    chunks.push(chunk);
  }
  try { return JSON.parse(Buffer.concat(chunks).toString('utf8')); } catch { throw invalid('JSON tidak valid.'); }
}
async function transaction(pool,fn,{readOnly=false}={}) {
  const client=await pool.connect();
  try {await client.query(readOnly?'BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY':'BEGIN');const result=await fn(client);await client.query('COMMIT');return result;}
  catch(error){await client.query('ROLLBACK').catch(()=>{});throw error;}
  finally{client.release();}
}
export async function createApp({pool,limiter=new LoginLimiter(),onInternalError=()=>{}}) {
  const dummyHash=await hashPassword(randomBytes(24).toString('hex'));
  async function authenticate(req,db=pool) {
    const token=/^Bearer ([a-f0-9]{64})$/.exec(req.headers.authorization||'')?.[1];
    if(!token) throw sessionRequired();
    const {rows:[row]}=await db.query(`SELECT s.id AS session_id,u.id,u.username,u.display_name,u.active
      FROM ccs.sessions s JOIN ccs.accounts u ON u.id=s.account_id
      WHERE s.token_hash=$1 AND s.revoked_at IS NULL AND s.expires_at>clock_timestamp()`,[digest(token)]);
    if(!row) throw sessionRequired();
    if(!row.active) throw new ApiError(403,'ACCOUNT_DISABLED','Akun tidak aktif.');
    return row;
  }
  async function member(db,accountId,outletId,admin=false) {
    const {rows:[membership]}=await db.query(`SELECT m.role,o.id,o.name,o.timezone,o.cutoff_minutes,o.settings_revision
      FROM ccs.memberships m JOIN ccs.outlets o ON o.id=m.outlet_id
      JOIN ccs.accounts u ON u.id=m.account_id
      WHERE m.outlet_id=$1 AND m.account_id=$2 AND m.active AND o.active AND u.active`,[outletId,accountId]);
    if(!membership || (admin&&membership.role!=='ADMIN')) throw forbidden();
    return membership;
  }
  const closingRoutes=createClosingRoutes({pool,authenticate,member,readJson,transaction});
  const settingsRoutes=createSettingsRoutes({pool,authenticate,member,readJson,transaction});
  const teamRoutes=createTeamRoutes({pool,authenticate,member,readJson,transaction});
  const operationStatusRoutes=createOperationStatusRoutes({pool,authenticate,member,readJson,transaction});
  return createServer({requestTimeout:10000,headersTimeout:10000,maxHeaderSize:8192},async(req,res)=>{
    const requestId=randomUUID();
    res.setHeader('Cache-Control','no-store');res.setHeader('X-Content-Type-Options','nosniff');
    const send=(status,data)=>{res.writeHead(status,{'Content-Type':'application/json; charset=utf-8'});res.end(JSON.stringify({data,requestId}));};
    try {
      if((req.url||'').length>4096) throw invalid();
      const url=new URL(req.url,'http://localhost');const path=url.pathname;const method=req.method;
      if(method==='GET'&&path==='/api/health') {
        await pool.query('SELECT 1');return send(200,{status:'ok',environment:'QA',service:'closing-count',version:serviceVersion});
      }
      if(method==='POST'&&path==='/api/v1/auth/login') {
        limiter.consume('ip:'+req.socket.remoteAddress,10);
        const body=await readJson(req);exactObject(body,['username','password']);
        const username=normalizeUsername(body.username);
        if(typeof body.password!=='string'||Buffer.byteLength(body.password)>256) throw invalid();
        limiter.consume('user:'+username,5);
        const {rows:[account]}=await pool.query('SELECT id,password_hash,active FROM ccs.accounts WHERE username=$1',[username]);
        const valid=await verifyPassword(body.password,account?.password_hash||dummyHash);
        if(!valid||!account?.active) throw new ApiError(401,'INVALID_CREDENTIALS','Username atau password tidak sesuai.');
        const token=randomBytes(32).toString('hex');
        const session=await transaction(pool,async db=>{
          const {rows:[current]}=await db.query('SELECT active,password_hash FROM ccs.accounts WHERE id=$1 FOR UPDATE',[account.id]);
          if(!current?.active||current.password_hash!==account.password_hash) throw new ApiError(401,'INVALID_CREDENTIALS','Username atau password tidak sesuai.');
          await db.query("DELETE FROM ccs.sessions WHERE account_id=$1 AND expires_at<clock_timestamp()-interval '7 days'",[account.id]);
          await db.query(`UPDATE ccs.sessions SET revoked_at=clock_timestamp() WHERE id IN
            (SELECT id FROM ccs.sessions WHERE account_id=$1 AND revoked_at IS NULL AND expires_at>clock_timestamp()
             ORDER BY created_at DESC,id DESC OFFSET 4)`,[account.id]);
          return (await db.query(`INSERT INTO ccs.sessions(id,account_id,token_hash,expires_at)
            VALUES ($1,$2,$3,clock_timestamp()+interval '8 hours') RETURNING expires_at`,[randomUUID(),account.id,digest(token)])).rows[0];
        });
        return send(200,{accessToken:token,expiresAt:session.expires_at,tokenType:'Bearer'});
      }
      const account=await authenticate(req);
      const operationStatusResponse=await operationStatusRoutes(req,url);
      if(operationStatusResponse)return send(operationStatusResponse.status,operationStatusResponse.data);
      const teamResponse=await teamRoutes(req,url,account);
      if(teamResponse)return send(teamResponse.status,teamResponse.data);
      const settingsResponse=await settingsRoutes(req,url,account);
      if(settingsResponse)return send(settingsResponse.status,settingsResponse.data);
      const closingResponse=await closingRoutes(req,url,account);
      if(closingResponse)return send(closingResponse.status,closingResponse.data);
      if(method==='POST'&&path==='/api/v1/auth/logout') {
        const body=await readJson(req);exactObject(body,[]);
        await pool.query('UPDATE ccs.sessions SET revoked_at=clock_timestamp() WHERE id=$1',[account.session_id]);
        return send(200,{loggedOut:true});
      }
      if(method==='GET'&&path==='/api/v1/me') {
        const memberships=await pool.query(`SELECT o.id AS outlet_id,o.name,m.role,o.timezone,o.cutoff_minutes
          FROM ccs.memberships m JOIN ccs.outlets o ON o.id=m.outlet_id
          WHERE m.account_id=$1 AND m.active AND o.active ORDER BY o.name,o.id`,[account.id]);
        return send(200,{id:account.id,username:account.username,displayName:account.display_name,outlets:memberships.rows});
      }
      const match=/^\/api\/v1\/outlets\/([^/]+)(?:\/(business-date|memberships|accounts)(?:\/([^/]+))?)?$/.exec(path);
      if(match) {
        const [,outletId,action,subjectId]=match;
        if(!isUuid(outletId)||(subjectId&&!isUuid(subjectId))) throw invalid();
        if(method==='GET'&&!subjectId&&(!action||action==='business-date')) {
          const outlet=await member(pool,account.id,outletId);
          if(!action) return send(200,{id:outlet.id,name:outlet.name,role:outlet.role,timezone:outlet.timezone,cutoffMinutes:outlet.cutoff_minutes,settingsRevision:Number(outlet.settings_revision)});
          const {rows:[clock]}=await pool.query('SELECT clock_timestamp() AS now');
          return send(200,{businessDate:businessDate(clock.now,outlet.timezone,outlet.cutoff_minutes),serverTime:clock.now,
            timezone:outlet.timezone,cutoffMinutes:outlet.cutoff_minutes});
        }
        if(method==='GET'&&action==='memberships'&&!subjectId) {
          await member(pool,account.id,outletId,true);
          const members=await pool.query(`SELECT m.account_id,u.username,u.display_name,m.role,m.active,u.active AS account_active
            FROM ccs.memberships m JOIN ccs.accounts u ON u.id=m.account_id WHERE m.outlet_id=$1 ORDER BY u.username`,[outletId]);
          return send(200,members.rows);
        }
        if(method==='POST'&&action==='accounts'&&!subjectId) {
          await member(pool,account.id,outletId,true);
          const body=await readJson(req);exactObject(body,['username','displayName','password','role']);
          const username=normalizeUsername(body.username),name=displayName(body.displayName);validateRole(body.role);validatePassword(body.password);
          const passwordHash=await hashPassword(body.password);
          const created=await transaction(pool,async db=>{
            await db.query('SELECT id FROM ccs.outlets WHERE id=$1 FOR UPDATE',[outletId]);
            const current=await authenticate(req,db);await member(db,current.id,outletId,true);
            const id=randomUUID();
            await db.query('INSERT INTO ccs.accounts(id,username,display_name,password_hash) VALUES ($1,$2,$3,$4)',[id,username,name,passwordHash]);
            await db.query('INSERT INTO ccs.memberships(outlet_id,account_id,role) VALUES ($1,$2,$3)',[outletId,id,body.role]);
            await db.query(`INSERT INTO ccs.membership_audit(outlet_id,actor_id,subject_id,action,after_value)
              VALUES ($1,$2,$3,'CREATE_ACCOUNT',$4::jsonb)`,[outletId,current.id,id,JSON.stringify({username,role:body.role,active:true})]);
            return {id,username,displayName:name,role:body.role,active:true};
          });
          return send(201,created);
        }
        if(method==='PUT'&&action==='memberships'&&subjectId) {
          await member(pool,account.id,outletId,true);
          const body=await readJson(req);exactObject(body,['role','active']);validateRole(body.role);if(typeof body.active!=='boolean') throw invalid();
          const updated=await transaction(pool,async db=>{
            await db.query('SELECT id FROM ccs.outlets WHERE id=$1 FOR UPDATE',[outletId]);
            const current=await authenticate(req,db);await member(db,current.id,outletId,true);
            const {rows:[before]}=await db.query(`SELECT m.role,m.active,u.active AS account_active FROM ccs.memberships m
              JOIN ccs.accounts u ON u.id=m.account_id WHERE m.outlet_id=$1 AND m.account_id=$2 FOR UPDATE OF m`,[outletId,subjectId]);
            if(!before) throw new ApiError(404,'MEMBER_NOT_FOUND','Anggota outlet tidak ditemukan.');
            if(before.role==='ADMIN'&&before.active&&before.account_active&&(!body.active||body.role!=='ADMIN')) {
              const {rows:[count]}=await db.query(`SELECT count(*)::int AS total FROM ccs.memberships m JOIN ccs.accounts u ON u.id=m.account_id
                WHERE m.outlet_id=$1 AND m.role='ADMIN' AND m.active AND u.active`,[outletId]);
              if(count.total<=1) throw new ApiError(409,'LAST_ADMIN_REQUIRED','Outlet memerlukan minimal satu administrator aktif.');
            }
            if(before.role!==body.role||before.active!==body.active) {
              await db.query('UPDATE ccs.memberships SET role=$3,active=$4,revision=revision+1,updated_at=clock_timestamp() WHERE outlet_id=$1 AND account_id=$2',[outletId,subjectId,body.role,body.active]);
              await db.query(`INSERT INTO ccs.membership_audit(outlet_id,actor_id,subject_id,action,before_value,after_value)
                VALUES ($1,$2,$3,'UPDATE_MEMBERSHIP',$4::jsonb,$5::jsonb)`,[outletId,current.id,subjectId,JSON.stringify({role:before.role,active:before.active}),JSON.stringify(body)]);
            }
            return {accountId:subjectId,...body};
          });
          return send(200,updated);
        }
      }
      throw new ApiError(404,'NOT_FOUND','Halaman API tidak ditemukan.');
    } catch(error) {
      req.resume();
      let safe=error;
      if(error.code==='23505'&&error.constraint==='accounts_username_key') safe=new ApiError(409,'USERNAME_UNAVAILABLE','Username tidak tersedia.');
      if(!(safe instanceof ApiError)) {onInternalError({requestId,code:'INTERNAL_ERROR'});safe=new ApiError(500,'INTERNAL_ERROR','Permintaan belum dapat diproses.');}
      if(!res.headersSent) {if(safe.status===429)res.setHeader('Retry-After','60');res.writeHead(safe.status,{'Content-Type':'application/json; charset=utf-8'});res.end(JSON.stringify({error:{code:safe.code,message:safe.message,...(safe.details?{details:safe.details}:{})},requestId}));}
      else res.destroy();
    }
  });
}
