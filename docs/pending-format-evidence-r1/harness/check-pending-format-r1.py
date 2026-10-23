from pathlib import Path
import subprocess,json,hashlib,os,sys,collections,xml.etree.ElementTree as ET
root=Path('D:/Code/Worktrees/Closing-Count-Portfolio')
program=Path('D:/Code/Documents/Career-Portfolio-2026')
out=program/'evidence/closing-count-pending-format-r1'
baseline='77bfb6c999471301d7768c57a237ff46ebae6c50'
def git(where,*args):return subprocess.check_output(['git','-C',str(where),*args])
def sha(b):return hashlib.sha256(b).hexdigest()
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def log(p):
 b=p.read_bytes();return b.decode('utf-16' if b[:2] in [b'\xff\xfe',b'\xfe\xff'] else 'utf-8-sig')
assert git(root,'rev-parse','HEAD').decode().strip()==baseline
v=read(out/'verification.json');assert v['passed'] and v['tests']==27 and not v['physicalPhoneAccessed'] and not v['realHttpAcceptance'] and not v['nativeBackupUiAcceptance']
assert read(out/'device-baseline.json')==read(out/'device-after.json')
text=log(out/'instrumentation.log');assert 'OK (27 tests)' in text
for bad in ['FAILURES!!!','INSTRUMENTATION_FAILED','shortMsg=','Process crashed','AssumptionViolated','STATUS_CODE: -3']:assert bad not in text
row={};passed=[]
for line in text.splitlines():
 if line.startswith('INSTRUMENTATION_STATUS: '):
  value=line.removeprefix('INSTRUMENTATION_STATUS: ')
  if '=' in value:k,val=value.split('=',1);row[k]=val
 elif line.startswith('INSTRUMENTATION_STATUS_CODE: '):
  if line.endswith(': 0'):passed.append((row['class'],row['test']))
  row={}
assert len(passed)==27 and len(set(passed))==27
counts=collections.Counter(c.rsplit('.',1)[1] for c,t in passed)
assert counts=={'OnlinePendingBackupInstrumentedTest':8,'OnlinePendingCaptureInstrumentedTest':4,'OnlineSyncInstrumentedTest':15}
inputs=read(out/'apk-inputs.json')
for p in inputs:assert sha(Path(p['Path']).read_bytes()).upper()==p['Hash']
jvmRows=[ET.parse(p).getroot().attrib for p in (root/'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')]
jvm={k:sum(int(r.get(k,0)) for r in jvmRows) for k in ['tests','failures','errors','skipped']}
assert jvm==dict(tests=25,failures=0,errors=0,skipped=0)
lint=ET.parse(root/'app/build/reports/lint-results-debug.xml').getroot()
assert not any(i.get('severity') in ['Error','Fatal'] for i in lint)
warnings=sum(i.get('severity')=='Warning' for i in lint)
assert 'BUILD SUCCESSFUL' in log(out/'build.log') and 'BUILD SUCCESSFUL' in log(out/'unicode-test-build.log')
proof=dict(passed=True,androidTests=27,testGroups=dict(counts),testCases=[dict(suite=c,test=t) for c,t in passed],jvm=jvm,lintErrors=0,lintWarnings=warnings,
 apkSha256=[p['Hash'] for p in inputs],deviceSettingsUnchanged=True,
 scope='Internal codec and Room capture foundation; no backup/import UI, recovery inbox, HTTP recovery or physical-phone acceptance.')
(out/'runtime-proof.json').write_text(json.dumps(proof,indent=2)+'\n',encoding='utf8')
(out/'jvm-tests.json').write_text(json.dumps(jvm,indent=2)+'\n',encoding='utf8')
(out/'lint.txt').write_text(f'0 errors, {warnings} warnings\n',encoding='utf8')
if '--staged' in sys.argv:
 assert git(root,'branch','--show-current').decode().strip()=='portfolio/closing-online'
 assert not git(root,'diff','--name-only').strip()
 assert not git(root,'diff',baseline,'--','service','app/schemas','app/src/main/java/com/closingcount/app/ui','app/src/main/java/com/closingcount/app/MainActivity.kt','app/src/main/java/com/closingcount/app/data/online','app/src/main/java/com/closingcount/app/data/transfer/BackupJsonCodec.kt','app/src/main/java/com/closingcount/app/data/transfer/LocalBackupTransfer.kt','app/src/main/java/com/closingcount/app/data/transfer/OnlineReportExcelExporter.kt','app/build.gradle.kts','gradle','build.gradle.kts','settings.gradle.kts','gradle.properties').strip()
 files=git(root,'diff','--cached','--name-only').decode().splitlines()
 allowed={'app/src/main/java/com/closingcount/app/'+p for p in ['data/local/OnlineSyncDao.kt','data/local/OnlinePendingClosingCapture.kt','data/transfer/OnlinePendingBackup.kt','data/transfer/OnlinePendingBackupCodec.kt']}
 assert {p for p in files if p.startswith('app/src/main/')}==allowed
 for p in files:
  assert p in allowed or p in ['README.md','docs/ONLINE_PENDING_TRANSFER_PLAN_R1.md','docs/PENDING_TRANSFER_FOUNDATION_R1.md','app/src/androidTest/java/com/closingcount/app/OnlinePendingBackupInstrumentedTest.kt','app/src/androidTest/java/com/closingcount/app/OnlinePendingCaptureInstrumentedTest.kt'] or p.startswith('docs/pending-format-evidence-r1/'),p
 canonical=[]
 for repo in read(program/'evidence/portfolio-run-r1/git-setup.json')['repos']:
  folder=Path('D:/Code')/repo['path'];head=subprocess.run(['git','-C',str(folder),'rev-parse','--verify','HEAD'],capture_output=True)
  assert (head.stdout.decode().strip() if head.returncode==0 else None)==repo['head']
  assert sha(git(folder,'status','--porcelain=v1','-uall'))==repo['sourceStatusSha256']
  canonical.append(dict(code=repo['code'],headAndStatusUnchanged=True))
 secret=read(Path(os.environ['LOCALAPPDATA'])/'PortfolioQA/secrets/closing-count-local-r1.json');records=[]
 for name in files:
  assert not name.endswith(('.apk','.jks','.keystore')) and 'hs_err_pid' not in name and 'replay_pid' not in name
  if name=='docs/pending-format-evidence-r1/source-preflight.json':continue
  b=git(root,'show',':'+name)
  for key in ['adminPassword','appPassword','instance']:assert secret[key].encode() not in b,name
  records.append(dict(file=name,indexSha256=sha(b)))
 result=dict(passed=True,baseline=baseline,files=records,canonical=canonical,knownPrivateValuesAbsent=True,serviceSchemasDependenciesExistingUiOnlineTransportLegacyUnchanged=True,
  androidTests=27,jvmTests=25,lintErrors=0,lintWarnings=warnings,scope=proof['scope'])
 (out/'source-preflight.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
 print(json.dumps(dict(passed=True,stagedFilesExcludingPreflight=len(records),canonicalUnchanged=8,androidTests=27)))
else:print(json.dumps(dict(passed=True,androidTests=27,codecTests=8,captureTests=4,syncRegressions=15,jvmTests=25,lintWarnings=warnings)))
