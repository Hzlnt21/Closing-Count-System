import fs from 'node:fs';
import path from 'node:path';
import {loadState,atomicJson} from '../../../Worktrees/Closing-Count-Portfolio/demo/private-state.mjs';
import {appPool,assertContainer} from '../../../Worktrees/Closing-Count-Portfolio/service/demo/database.mjs';
import {assertOwnedDatabase} from '../../../Worktrees/Closing-Count-Portfolio/service/src/config.mjs';
import {privateGeneration,currentGeneration,identityFor} from '../../../Worktrees/Closing-Count-Portfolio/service/demo/generation.mjs';
const [action,directory,output]=process.argv.slice(2);
const state=loadState(directory);assertContainer(state);const pool=appPool(state);
try {
 await assertOwnedDatabase(pool,state.config);
 const current=await currentGeneration(pool);if(current?.status!=='READY')throw Error('Fixture not ready');
 const g=privateGeneration(state,current.number);
 const closings=(await pool.query('SELECT id,business_date::text AS date,state,revision,generation FROM ccs.closings WHERE outlet_id=$1 ORDER BY business_date',[g.outlet])).rows;
 if(action==='fixture') {
  if(!path.resolve(output).startsWith(path.resolve(directory)+path.sep))throw Error('Private handover must remain inside private runtime');
  atomicJson(output,{...g,closing:closings.find(c=>c.date===g.anchor).id});
  console.log(JSON.stringify({fixturePrepared:true,generation:g.number,credentialsPrinted:false}));
 }else if(action==='snapshot') {
  const generations=(await pool.query('SELECT number,status,outlet_id,anchor_date::text FROM ccs_demo.generations ORDER BY number')).rows;
  const result={generations,identities:[],closings:[],entries:[],reports:[],audits:[],receipts:[],catalogs:[]};
  for(const row of generations) {
   const old=privateGeneration(state,row.number);result.identities.push(identityFor(old));
   for(const [key,sql] of [
    ['closings','SELECT id,outlet_id,business_date::text AS date,state,revision,generation,catalog_version_id,snapshot FROM ccs.closings WHERE outlet_id=$1 ORDER BY business_date'],
    ['entries','SELECT e.* FROM ccs.closing_entries e JOIN ccs.closings c ON c.id=e.closing_id WHERE c.outlet_id=$1 ORDER BY c.business_date,e.menu_id'],
    ['reports','SELECT * FROM ccs.closing_reports WHERE outlet_id=$1 ORDER BY closing_id,generation'],
    ['audits','SELECT * FROM ccs.closing_audit WHERE outlet_id=$1 ORDER BY id'],
    ['receipts','SELECT outlet_id,actor_id,operation_id,response FROM ccs.operation_receipts WHERE outlet_id=$1 ORDER BY created_at'],
    ['catalogs','SELECT * FROM ccs.catalog_versions WHERE outlet_id=$1 ORDER BY revision']
   ])result[key].push(...(await pool.query(sql,[old.outlet])).rows);
  }
  fs.writeFileSync(output,JSON.stringify(result,null,2)+'\n',{flag:'wx'});
  console.log(JSON.stringify({snapshotWritten:true,generations:generations.length,credentialsPrinted:false}));
 }else throw Error('Unknown action');
}finally{await pool.end();}
