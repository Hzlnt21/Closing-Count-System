import pg from 'pg';
import {assertOwnedDatabase,configFromEnvironment} from './config.mjs';
import {migrate} from './migrate.mjs';
import {createApp} from './app.mjs';
const config=configFromEnvironment();
const pool=new pg.Pool({connectionString:config.databaseUrl,max:8,connectionTimeoutMillis:5000,statement_timeout:10000});
pool.on('error',()=>console.error('Closing Count database connection interrupted.'));
let server;
try {
  await assertOwnedDatabase(pool,config);await migrate(pool,config);
  server=await createApp({pool,onInternalError:({requestId})=>console.error('Closing Count request failed: '+requestId)});
  await new Promise((resolve,reject)=>{server.once('error',reject);server.listen(config.port,config.host,resolve);});
  console.log(`Closing Count QA ready on http://${config.host}:${config.port}`);
} catch {
  console.error('Closing Count startup refused or failed; check owned QA configuration.');
  await pool.end();process.exitCode=1;
}
let stopping=false;
async function stop(){if(stopping)return;stopping=true;if(server)await new Promise(resolve=>server.close(resolve));await pool.end();}
process.on('SIGINT',()=>stop());process.on('SIGTERM',()=>stop());
