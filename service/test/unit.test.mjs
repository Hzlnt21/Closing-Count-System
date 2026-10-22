import test from 'node:test';
import assert from 'node:assert/strict';
import {configFromEnvironment} from '../src/config.mjs';
import {hashPassword,verifyPassword,normalizeUsername,validatePassword} from '../src/passwords.mjs';
import {LoginLimiter} from '../src/limiter.mjs';
import {businessDate,validateBusinessSettings} from '../src/business-date.mjs';
import {exactObject} from '../src/errors.mjs';

const valid={CCS_ENVIRONMENT:'QA',CCS_INSTANCE:'a'.repeat(48),CCS_DATABASE_URL:`postgresql://ccs_portfolio_app:${'b'.repeat(64)}@127.0.0.1:55436/closing_count_portfolio_qa`};
test('QA configuration refuses implicit, operational or foreign targets without reflecting credentials',()=>{
  assert.equal(configFromEnvironment(valid).host,'127.0.0.1');
  const refused=[{}, {...valid,CCS_ENVIRONMENT:'PRODUCTION'}, {...valid,CCS_INSTANCE:'wrong'},
    {...valid,CCS_DATABASE_URL:valid.CCS_DATABASE_URL.replace('55436','5432')},
    {...valid,CCS_DATABASE_URL:valid.CCS_DATABASE_URL.replace('127.0.0.1','example.com')},
    {...valid,CCS_DATABASE_URL:valid.CCS_DATABASE_URL.replace('ccs_portfolio_app','postgres')},
    {...valid,CCS_DATABASE_URL:valid.CCS_DATABASE_URL.replace('closing_count_portfolio_qa','operational')},
    {...valid,CCS_DATABASE_URL:valid.CCS_DATABASE_URL+'?options=-csearch_path=public'},
    {...valid,CCS_DATABASE_URL:'not a URL'}, {...valid,CCS_PORT:'0'}];
  for(const env of refused) assert.throws(()=>configFromEnvironment(env),{message:'Closing Count requires the explicit owned QA target'});
});
test('password salt is unique, verification works and malformed parameters fail bounded',async()=>{
  const password='QA Closing!2026';const a=await hashPassword(password),b=await hashPassword(password);
  assert.notEqual(a,b);assert.equal(await verifyPassword(password,a),true);
  assert.equal(await verifyPassword('wrong',a),false);assert.equal(await verifyPassword(password,'scrypt$huge$bad'),false);
  assert.equal(await verifyPassword('x'.repeat(257),a),false);
});
test('password and username validation preserve case-sensitive password semantics',()=>{
  for(const value of [null,12,'short','é'.repeat(129)])assert.throws(()=>validatePassword(value));
  assert.equal(normalizeUsername('  Maya.Demo  '),'maya.demo');
  for(const value of [null,12,'a','with space',"x' OR true--",'x'.repeat(65)])assert.throws(()=>normalizeUsername(value));
});
test('payload rejects arrays, null, missing fields and injected actor/role',()=>{
  for(const value of [null,[],{username:'abc'},{username:'abc',password:'test',actorId:'spoof'}]) assert.throws(()=>exactObject(value,['username','password']));
  exactObject({username:'abc',password:'test'},['username','password']);
});
test('rate limit enforces current window, expires and fails closed at bounded capacity',()=>{
  let now=0;const limiter=new LoginLimiter({now:()=>now});
  for(let i=0;i<5;i++)limiter.consume('user:maya',5);
  assert.throws(()=>limiter.consume('user:maya',5),{code:'LOGIN_RATE_LIMITED'});
  now=60000;limiter.consume('user:maya',5);
  for(let i=0;i<4999;i++)limiter.consume('user:'+i,5);
  assert.throws(()=>limiter.consume('user:overflow',5),{code:'LOGIN_RATE_LIMITED'});
});
test('business date uses outlet calendar and cutoff across year rollover',()=>{
  assert.equal(businessDate(new Date('2025-12-31T16:59:59Z'),'Asia/Jakarta',0),'2025-12-31');
  assert.equal(businessDate(new Date('2025-12-31T17:00:00Z'),'Asia/Jakarta',0),'2026-01-01');
  assert.equal(businessDate(new Date('2025-12-31T20:59:59Z'),'Asia/Jakarta',240),'2025-12-31');
  assert.equal(businessDate(new Date('2025-12-31T21:00:00Z'),'Asia/Jakarta',240),'2026-01-01');
});
test('business date handles a DST outlet calendar and rejects invalid settings',()=>{
  assert.equal(businessDate(new Date('2026-03-08T07:30:00Z'),'America/New_York',240),'2026-03-07');
  assert.equal(businessDate(new Date('2026-03-08T08:00:00Z'),'America/New_York',240),'2026-03-08');
  for(const [zone,cutoff] of [['bad/zone',0],['UTC',-1],['UTC',1440],['UTC',0.5],['UTC','0']])assert.throws(()=>validateBusinessSettings(zone,cutoff));
});
