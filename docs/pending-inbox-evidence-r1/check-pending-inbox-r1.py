from pathlib import Path
import subprocess,json,hashlib,os,sys,collections,xml.etree.ElementTree as ET
root=Path('D:/Code/Worktrees/Closing-Count-Portfolio');program=Path('D:/Code/Documents/Career-Portfolio-2026')
out=program/'evidence/closing-count-pending-inbox-r1';baseline='0af5146ba1b69b6254c2eb1bc7d447cb696b7a2c'
def git(where,*args):return subprocess.check_output(['git','-C',str(where),*args])
def sha(b):return hashlib.sha256(b).hexdigest()
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def log(p):
 b=p.read_bytes();return b.decode('utf-16' if b[:2] in [b'\xff\xfe',b'\xfe\xff'] else 'utf-8-sig')
v=read(out/'verification.json');assert v['passed'] and v['tests']==45 and not v['physicalPhoneAccessed'] and not v['nativeBackupUiAcceptance']
assert read(out/'device-baseline.json')==read(out/'device-after.json')
passed=[];row={};text=log(out/'instrumentation.log');assert 'OK (45 tests)' in text
for line in text.splitlines():
 if line.startswith('INSTRUMENTATION_STATUS: '):
  value=line.removeprefix('INSTRUMENTATION_STATUS: ')
  if '=' in value:k,val=value.split('=',1);row[k]=val
 elif line.startswith('INSTRUMENTATION_STATUS_CODE: '):
  if line.endswith(': 0'):passed.append((row['class'],row['test']))
  row={}
counts=collections.Counter(c.rsplit('.',1)[1] for c,t in passed)
assert len(passed)==len(set(passed))==45
assert counts=={'OnlinePendingBackupInstrumentedTest':8,'OnlinePendingCaptureInstrumentedTest':4,'OnlinePendingExportInstrumentedTest':9,'OnlineSyncInstrumentedTest':15,'OnlinePendingInboxInstrumentedTest':9}
for name in ['instrumentation.log','process-stage.log','process-verify.log']:
 t=log(out/name)
 for bad in ['FAILURES!!!','INSTRUMENTATION_FAILED','shortMsg=','Process crashed','AssumptionViolated','STATUS_CODE: -3']:assert bad not in t
 if name.startswith('process'):assert 'OK (1 test)' in t
inputs=read(out/'apk-inputs.json')
for p in inputs:assert sha(Path(p['Path']).read_bytes()).upper()==p['Hash']
jvmRows=[ET.parse(p).getroot().attrib for p in (root/'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')]
assert sum(int(r['tests']) for r in jvmRows)==25 and all(int(r['failures'])==int(r['errors'])==int(r['skipped'])==0 for r in jvmRows)
lint=ET.parse(root/'app/build/reports/lint-results-debug.xml').getroot();warnings=sum(e.attrib['severity']=='Warning' for e in lint);assert not any(e.attrib['severity'] in ['Error','Fatal'] for e in lint)
body=(out/'pending-inbox-process-qa.json').read_bytes();p=read(out/'pending-inbox-process-proof.json');packet=json.loads(body)
assert p['stagePid']!=p['verifyPid'] and p['duplicate'] and p['uncommittedPartialIgnored'] and p['sha256']==sha(body) and p['bytes']==len(body)
def id(n):return '00000000-0000-4000-8000-'+str(n).zfill(12)
expected=dict(format='closing-count-online-pending-backup',schemaVersion=1,backupId=p['run'],createdAt='2026-10-23T00:00:00Z',sourceDeviceId=id(2),origin='https://closing.example.test',account=dict(id=id(3),username='rani.qa',name='Rani QA'),outlet=dict(id=id(7),name='Outlet QA'),closings=[dict(id=id(8),date='2026-10-23',generation=2,revision=5,state='DRAFT',entries=[dict(menuId=id(4),name='Kopi susu',baseQuantity=6,baseRevision=2,localQuantity=8,status='PENDING')],operations=[dict(operationId=id(5),menuId=id(4),generation=1,expectedRevision=0,quantity=4,status='SENT'),dict(operationId=id(6),menuId=id(4),generation=2,expectedRevision=None,quantity=8,status='QUEUED')],lifecycle=None),dict(id=id(9),date='2026-10-22',generation=2,revision=4,state='DRAFT',entries=[],operations=[],lifecycle=dict(operationId=id(10),action='reopen',generation=1,expectedRevision=3,reason='Koreksi angka',status='AUTH_REQUIRED',wasSent=True))],admin=[dict(kind='SETTINGS',operationId=id(11),expectedRevision=2,status='QUEUED',wasSent=False,payload=dict(name='Outlet berikutnya',timezone='Asia/Jakarta',cutoffMinutes=0)),dict(kind='TEAM_CREATE',operationId=id(12),expectedRevision=None,status='SENT',wasSent=True,payload=dict(username='dimas.qa',displayName='Dimas QA',role='EMPLOYEE')),dict(kind='CATALOG',operationId=id(13),expectedRevision=4,status='EDITING',wasSent=False,payload=dict(ingredientCategories=[],ingredients=[],menuCategories=[],menus=[]))])
assert packet==expected
proof=dict(passed=True,androidTests=45,testGroups=dict(counts),processTests=2,separateProcessIds=[p['stagePid'],p['verifyPid']],actualForceStop=True,packetSha256=sha(body),packetBytes=len(body),wholePacketCompared=True,jvmTests=25,lintErrors=0,lintWarnings=warnings,deviceSettingsUnchanged=True,scope='PT04 inbox core; no recovery application, HTTP or native picker acceptance.')
(out/'runtime-proof.json').write_text(json.dumps(proof,indent=2)+'\n',encoding='utf8')
if '--staged' in sys.argv:
 assert git(root,'rev-parse','HEAD').decode().strip()==baseline
 assert git(root,'branch','--show-current').decode().strip()=='portfolio/closing-online' and not git(root,'diff','--name-only').strip()
 assert not git(root,'diff',baseline,'--','service','app/schemas','app/build.gradle.kts','gradle','build.gradle.kts','settings.gradle.kts','gradle.properties').strip()
 files=git(root,'diff','--cached','--name-only').decode().splitlines()
 allowed={'app/src/'+p for p in ['main/java/com/closingcount/app/data/transfer/OnlinePendingRecoveryInbox.kt','main/java/com/closingcount/app/data/transfer/OnlinePendingRecoveryTransfer.kt','androidTest/java/com/closingcount/app/OnlinePendingInboxInstrumentedTest.kt','androidTest/java/com/closingcount/app/OnlinePendingInboxProcessInstrumentedTest.kt']}
 assert set(git(root,'diff',baseline,'--name-only','--','app').decode().splitlines())==allowed
 canonical=[]
 for repo in read(program/'evidence/portfolio-run-r1/git-setup.json')['repos']:
  folder=Path('D:/Code')/repo['path'];head=subprocess.run(['git','-C',str(folder),'rev-parse','--verify','HEAD'],capture_output=True)
  assert (head.stdout.decode().strip() if head.returncode==0 else None)==repo['head'];assert sha(git(folder,'status','--porcelain=v1','-uall'))==repo['sourceStatusSha256'];canonical.append(dict(code=repo['code'],headAndStatusUnchanged=True))
 secret=read(Path(os.environ['LOCALAPPDATA'])/'PortfolioQA/secrets/closing-count-local-r1.json');records=[]
 for name in files:
  assert name in allowed or name in ['README.md','docs/ONLINE_PENDING_TRANSFER_PLAN_R1.md','docs/PENDING_RECOVERY_INBOX_R1.md'] or name.startswith('docs/pending-inbox-evidence-r1/'),name
  if name=='docs/pending-inbox-evidence-r1/source-preflight.json':continue
  b=git(root,'show',':'+name)
  for key in ['adminPassword','appPassword','instance']:assert secret[key].encode() not in b,name
  records.append(dict(file=name,indexSha256=sha(b)))
 assert git(root,'show',':docs/pending-inbox-evidence-r1/pending-inbox-process-qa.json')==body
 result=dict(passed=True,baseline=baseline,files=records,canonical=canonical,knownPrivateValuesAbsent=True,existingAppAndServiceSchemaDependencyUnchanged=True,exactPacketIndexBytes=True,androidTests=45,processTests=2,jvmTests=25,scope=proof['scope'])
 (out/'source-preflight.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8');print(json.dumps(dict(passed=True,stagedFilesExcludingPreflight=len(records),canonicalUnchanged=8,androidTests=45,processTests=2)))
else:print(json.dumps(proof))
