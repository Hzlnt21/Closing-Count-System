import pg from 'pg';
import {readFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {pathToFileURL} from 'node:url';
import {assertOwnedDatabase,configFromEnvironment} from './config.mjs';
export async function migrate(pool,config) {
  const scripts=await Promise.all(['001-foundation','002-collaborative-closing','003-closing-lifecycle','004-outlet-settings'].map(async id=>{
    const sql=(await readFile(new URL('../sql/'+id+'.sql',import.meta.url),'utf8')).replace(/\r\n/g,'\n');
    return {id,sql,checksum:createHash('sha256').update(sql).digest('hex')};
  }));
  const client=await pool.connect();
  try {
    await client.query('BEGIN');
    await assertOwnedDatabase(client,config);
    await client.query("SELECT pg_advisory_xact_lock(hashtextextended('closing-count-migrations',0))");
    const tables=await client.query("SELECT schemaname,tablename FROM pg_tables WHERE schemaname NOT IN ('pg_catalog','information_schema')");
    const installed=tables.rows.some(r=>r.schemaname==='ccs' && r.tablename==='migrations');
    if(!installed && tables.rows.some(r=>r.schemaname!=='public'||r.tablename!=='ccs_instance')) throw new Error('Refusing non-empty unmarked schema');
    const schemas=await client.query("SELECT n.nspname,r.rolname FROM pg_namespace n JOIN pg_roles r ON r.oid=n.nspowner WHERE n.nspname='ccs'");
    if(schemas.rowCount && schemas.rows[0].rolname!=='ccs_portfolio_app') throw new Error('Refusing foreign schema owner');
    await client.query('CREATE SCHEMA IF NOT EXISTS ccs AUTHORIZATION ccs_portfolio_app');
    await client.query('CREATE TABLE IF NOT EXISTS ccs.migrations (id text PRIMARY KEY, checksum text NOT NULL, applied_at timestamptz NOT NULL DEFAULT clock_timestamp())');
    const migrations=[];
    for(const {id,sql,checksum} of scripts){
      const prior=await client.query('SELECT checksum FROM ccs.migrations WHERE id=$1',[id]);
      if(prior.rowCount) {
        if(prior.rows[0].checksum!==checksum) throw new Error('Applied migration checksum changed');
      }else{
        await client.query(sql);await client.query('INSERT INTO ccs.migrations(id,checksum) VALUES ($1,$2)',[id,checksum]);
      }
      migrations.push({id,checksum,alreadyApplied:prior.rowCount===1});
    }
    await client.query('COMMIT');
    return {migrations,alreadyApplied:migrations.every(row=>row.alreadyApplied)};
  } catch(error) { await client.query('ROLLBACK').catch(()=>{});throw error; }
  finally { client.release(); }
}
if(process.argv[1] && pathToFileURL(process.argv[1]).href===import.meta.url) {
  const config=configFromEnvironment();
  const pool=new pg.Pool({connectionString:config.databaseUrl,max:2,connectionTimeoutMillis:5000,statement_timeout:10000});
  try { console.log(JSON.stringify(await migrate(pool,config))); }
  catch { console.error('Closing Count migration refused or failed. Check owned QA configuration.');process.exitCode=1; }
  finally { await pool.end(); }
}
