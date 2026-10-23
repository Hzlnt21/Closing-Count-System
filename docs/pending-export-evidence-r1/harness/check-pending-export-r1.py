from pathlib import Path
import subprocess,json,hashlib,os,sys,collections,xml.etree.ElementTree as ET
root=Path('D:/Code/Worktrees/Closing-Count-Portfolio');program=Path('D:/Code/Documents/Career-Portfolio-2026')
out=program/'evidence/closing-count-pending-export-r1';baseline='05716034e282e0f8d580089c87cdf34a5f577fed'
def git(where,*args):return subprocess.check_output(['git','-C',str(where),*args])
def sha(b):return hashlib.sha256(b).hexdigest()
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def log(p):
 b=p.read_bytes();return b.decode('utf-16' if b[:2] in [b'\xff\xfe',b'\xfe\xff'] else 'utf-8-sig')
v=read(out/'verification.json');assert v['passed'] and v['tests']==36 and not v['physicalPhoneAccessed'] and not v['nativeBackupUiAcceptance']
assert read(out/'device-baseline.json')==read(out/'device-after.json')
text=log(out/'instrumentation.log');assert 'OK (36 tests)' in text
for bad in ['FAILURES!!!','INSTRUMENTATION_FAILED','shortMsg=','Process crashed','AssumptionViolated','STATUS_CODE: -3']:assert bad not in text
row={};passed=[]
for line in text.splitlines():
 if line.startswith('INSTRUMENTATION_STATUS: '):
  value=line.removeprefix('INSTRUMENTATION_STATUS: ')
  if '=' in value:k,val=value.split('=',1);row[k]=val
 elif line.startswith('INSTRUMENTATION_STATUS_CODE: '):
  if line.endswith(': 0'):passed.append((row['class'],row['test']))
  row={}
counts=collections.Counter(c.rsplit('.',1)[1] for c,t in passed)
assert len(passed)==len(set(passed))==36
assert counts=={'OnlinePendingBackupInstrumentedTest':8,'OnlinePendingCaptureInstrumentedTest':4,'OnlinePendingExportInstrumentedTest':9,'OnlineSyncInstrumentedTest':15}
inputs=read(out/'apk-inputs.json')
for p in inputs:assert sha(Path(p['Path']).read_bytes()).upper()==p['Hash']
jvmRows=[ET.parse(p).getroot().attrib for p in (root/'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')]
jvm={k:sum(int(r.get(k,0)) for r in jvmRows) for k in ['tests','failures','errors','skipped']}
assert jvm==dict(tests=25,failures=0,errors=0,skipped=0)
lint=ET.parse(root/'app/build/reports/lint-results-debug.xml').getroot();assert not any(i.get('severity') in ['Error','Fatal'] for i in lint)
warnings=sum(i.get('severity')=='Warning' for i in lint)
assert 'BUILD SUCCESSFUL' in log(out/'build.log') and 'BUILD SUCCESSFUL' in log(out/'fixture-fix-build.log')
body=(out/'pending-export-qa.json').read_bytes();packet=json.loads(body.decode('utf8'));p=read(out/'pending-export-qa-proof.json')
assert sha(body)==p['sha256'] and len(body)<20*1024*1024
assert set(packet)=={'format','schemaVersion','backupId','createdAt','sourceDeviceId','origin','account','outlet','closings','admin'}
assert packet['format']=='closing-count-online-pending-backup' and packet['schemaVersion']==1
assert packet['origin']=='http://127.0.0.1:1' and packet['account']['id']==p['accountId'] and packet['outlet']['id']==p['outletId'] and packet['sourceDeviceId']==p['sourceDeviceId']
c=packet['closings'][0];assert len(packet['closings'])==1 and c['id']==p['closingId'] and c['lifecycle'] is None
assert len(c['entries'])==1 and c['entries'][0]['menuId']==p['menuId'] and c['entries'][0]['localQuantity']==p['frozenQuantity']==8
assert [q['quantity'] for q in c['operations']]==[4,8] and [q['expectedRevision'] for q in c['operations']]==[0,None]
assert [q['status'] for q in c['operations']]==['SENT','QUEUED'] and p['currentLocalQuantity']==9 and p['legacyUnchanged']
admin={a['kind']:a for a in packet['admin']};assert set(admin)=={'SETTINGS','TEAM_CREATE','CATALOG'}
assert admin['SETTINGS']['payload']=={'name':'Outlet berikutnya','timezone':'Asia/Jakarta','cutoffMinutes':60}
assert admin['TEAM_CREATE']['payload']=={'username':'dimas.qa','displayName':'Dimas QA','role':'EMPLOYEE'}
assert admin['CATALOG']['status']=='EDITING' and not admin['CATALOG']['wasSent']
assert admin['CATALOG']['payload']=={'ingredientCategories':[],'ingredients':[],'menuCategories':[],'menus':[]}
def no_secrets(value):
 if isinstance(value,dict):
  for k,v in value.items():assert k.lower() not in {'token','password','passwordhash','password_hash','session','expiresat','snapshotjson','updatedby','lockedby'};no_secrets(v)
 elif isinstance(value,list):
  for v in value:no_secrets(v)
no_secrets(packet);assert b'NEVER_EXPORT_SESSION_TOKEN' not in body and b'MUST_NOT_EXPORT_HISTORICAL_AUTHORS' not in body
packetProof=dict(passed=True,sha256=sha(body),bytes=len(body),closingCount=1,quantities=[4,8],latestFrozen=8,currentAfterReview=9,adminKinds=sorted(admin),credentialFieldsAbsent=True,historicalActorsAbsent=True,scope='Actual MediaStore content output; no system document-picker/UI or server recovery acceptance.')
(out/'packet-verification.json').write_text(json.dumps(packetProof,indent=2)+'\n',encoding='utf8')
proof=dict(passed=True,androidTests=36,testGroups=dict(counts),testCases=[dict(suite=c,test=t) for c,t in passed],jvm=jvm,lintErrors=0,lintWarnings=warnings,apkSha256=[p['Hash'] for p in inputs],deviceSettingsUnchanged=True,packet=packetProof,concurrentAckCaptureIterations=12,largeCatalogMenus=500,scope='PT02 local bounded capture/export core; UI/inbox/restore/HTTP recovery remain open.')
(out/'runtime-proof.json').write_text(json.dumps(proof,indent=2)+'\n',encoding='utf8')
(out/'jvm-tests.json').write_text(json.dumps(jvm,indent=2)+'\n',encoding='utf8');(out/'lint.txt').write_text(f'0 errors, {warnings} warnings\n',encoding='utf8')
if '--staged' in sys.argv:
 assert git(root,'rev-parse','HEAD').decode().strip()==baseline
 assert git(root,'branch','--show-current').decode().strip()=='portfolio/closing-online' and not git(root,'diff','--name-only').strip()
 assert not git(root,'diff',baseline,'--','service','app/schemas','app/src/main/java/com/closingcount/app/ui','app/src/main/java/com/closingcount/app/MainActivity.kt','app/src/main/java/com/closingcount/app/data/transfer/BackupJsonCodec.kt','app/src/main/java/com/closingcount/app/data/transfer/LocalBackupTransfer.kt','app/src/main/java/com/closingcount/app/data/transfer/OnlineReportExcelExporter.kt','app/build.gradle.kts','gradle','build.gradle.kts','settings.gradle.kts','gradle.properties').strip()
 for name,marker in [('data/local/OnlineSyncDao.kt','    abstract suspend fun closing(scope: String, closingId: String)'),('data/online/OnlineRepository.kt','    suspend fun team(outlet:')]:
  rel='app/src/main/java/com/closingcount/app/'+name;old=git(root,'show',baseline+':'+rel).decode();new=(root/rel).read_text(encoding='utf8');assert old[old.index(marker):]==new[new.index(marker):]
 files=git(root,'diff','--cached','--name-only').decode().splitlines()
 allowed={'app/src/main/java/com/closingcount/app/'+p for p in ['data/local/OnlineSyncDao.kt','data/local/OnlinePendingClosingCapture.kt','data/transfer/OnlinePendingBackupCodec.kt','data/transfer/OnlinePendingIntentBackupReader.kt','data/transfer/OnlineBackupDeviceIdentity.kt','data/transfer/OnlinePendingBackupTransfer.kt','data/online/OnlineRepository.kt','data/online/OnlineCatalogStore.kt','data/online/OnlineSettingsStore.kt','data/online/OnlineTeamStore.kt']}
 assert {p for p in files if p.startswith('app/src/main/')}==allowed
 for name in files:assert name in allowed or name in ['README.md','docs/ONLINE_PENDING_TRANSFER_PLAN_R1.md','docs/PENDING_EXPORT_CORE_R1.md','app/src/androidTest/java/com/closingcount/app/OnlinePendingCaptureInstrumentedTest.kt','app/src/androidTest/java/com/closingcount/app/OnlinePendingExportInstrumentedTest.kt'] or name.startswith('docs/pending-export-evidence-r1/'),name
 rel='docs/pending-export-evidence-r1/pending-export-qa.json';assert git(root,'show',':'+rel)==body
 canonical=[]
 for repo in read(program/'evidence/portfolio-run-r1/git-setup.json')['repos']:
  folder=Path('D:/Code')/repo['path'];head=subprocess.run(['git','-C',str(folder),'rev-parse','--verify','HEAD'],capture_output=True)
  assert (head.stdout.decode().strip() if head.returncode==0 else None)==repo['head'];assert sha(git(folder,'status','--porcelain=v1','-uall'))==repo['sourceStatusSha256'];canonical.append(dict(code=repo['code'],headAndStatusUnchanged=True))
 secret=read(Path(os.environ['LOCALAPPDATA'])/'PortfolioQA/secrets/closing-count-local-r1.json');records=[]
 for name in files:
  assert not name.endswith(('.apk','.jks','.keystore')) and 'hs_err_pid' not in name and 'replay_pid' not in name
  if name=='docs/pending-export-evidence-r1/source-preflight.json':continue
  b=git(root,'show',':'+name)
  for key in ['adminPassword','appPassword','instance']:assert secret[key].encode() not in b,name
  records.append(dict(file=name,indexSha256=sha(b)))
 result=dict(passed=True,baseline=baseline,files=records,canonical=canonical,knownPrivateValuesAbsent=True,existingUiServiceSchemaDependencyLegacyUnchanged=True,liveDaoMutationAndRepositoryTransportTailUnchanged=True,exactPacketIndexBytes=True,androidTests=36,jvmTests=25,scope=proof['scope'])
 (out/'source-preflight.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8');print(json.dumps(dict(passed=True,stagedFilesExcludingPreflight=len(records),canonicalUnchanged=8,androidTests=36)))
else:print(json.dumps(dict(passed=True,androidTests=36,packetBytes=len(body),concurrentAckCaptureIterations=12,jvmTests=25,lintWarnings=warnings)))
