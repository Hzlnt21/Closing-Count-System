import test from 'node:test';
import assert from 'node:assert/strict';
import {configFromEnvironment,assertOwnedDatabase} from '../src/config.mjs';
import {createApp} from '../src/app.mjs';
const qa={CCS_ENVIRONMENT:'QA',CCS_INSTANCE:'a'.repeat(48),CCS_DATABASE_URL:`postgresql://ccs_portfolio_app:${'b'.repeat(64)}@127.0.0.1:55436/closing_count_portfolio_qa`};
const demo={...qa,CCS_ENVIRONMENT:'DEMO',CCS_DATABASE_URL:qa.CCS_DATABASE_URL.replace('55436','55437').replace('portfolio_qa','portfolio_demo')};
test('QA and DEMO cannot adopt one another or an operational target',()=>{
 assert.equal(configFromEnvironment(demo).port,4372);assert.equal(configFromEnvironment(qa).port,4370);
 for(const value of [{...demo,CCS_DATABASE_URL:qa.CCS_DATABASE_URL},{...qa,CCS_DATABASE_URL:demo.CCS_DATABASE_URL},{...demo,CCS_ENVIRONMENT:'PRODUCTION'},
  {...demo,CCS_DATABASE_URL:demo.CCS_DATABASE_URL.replace('127.0.0.1','localhost')},{...demo,CCS_DATABASE_URL:demo.CCS_DATABASE_URL+'?options=x'},
  {...demo,CCS_DATABASE_URL:demo.CCS_DATABASE_URL.replace('55437','5432')},{...demo,CCS_DATABASE_URL:demo.CCS_DATABASE_URL.replace('portfolio_demo','operational')}])assert.throws(()=>configFromEnvironment(value));
});
test('forged ownership configuration is refused before any database query',async()=>{
 let queries=0;const client={query:async()=>{queries++;throw Error('must not query');}};
 await assert.rejects(assertOwnedDatabase(client,{environment:'DEMO',instance:qa.CCS_INSTANCE,databaseUrl:qa.CCS_DATABASE_URL,port:4372}));assert.equal(queries,0);
});
test('ownership still rejects unrestricted roles, mismatched markers and wrong databases',async()=>{
 const config=configFromEnvironment(demo),base={database:'closing_count_portfolio_demo',role:'ccs_portfolio_app',version:160000,rolsuper:false,rolcreatedb:false,rolcreaterole:false,owns_database:true};
 for(const changed of [{database:'closing_count_portfolio_qa'},{rolsuper:true},{rolcreatedb:true},{rolcreaterole:true},{owns_database:false},{version:170000}]) {
  let queries=0;await assert.rejects(assertOwnedDatabase({query:async()=>{queries++;return{rows:[{...base,...changed}]};}},config));assert.equal(queries,1);
 }
 await assert.rejects(assertOwnedDatabase({query:async sql=>sql.includes('current_database')?{rows:[base]}:{rowCount:1,rows:[{instance:'c'.repeat(48)}]}},config));
});
test('health identifies the explicit DEMO profile while existing default remains QA',async()=>{
 for(const environment of ['QA','DEMO']) {
  const server=await createApp({pool:{query:async()=>({rows:[]})},environment});await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  try{const response=await fetch(`http://127.0.0.1:${server.address().port}/api/health`);assert.equal((await response.json()).data.environment,environment);}finally{await new Promise(resolve=>server.close(resolve));}
 }
});
