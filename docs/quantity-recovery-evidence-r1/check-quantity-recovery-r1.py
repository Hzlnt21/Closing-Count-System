from pathlib import Path
import subprocess,json,hashlib,os,sys,collections,xml.etree.ElementTree as ET,uuid
root=Path('D:/Code/Worktrees/Closing-Count-Portfolio');program=Path('D:/Code/Documents/Career-Portfolio-2026')
out=program/'evidence/closing-count-quantity-recovery-r1';baseline='55476d27a4b6f5451dfcc6e8ba1c52c80056ff67'
def git(where,*args):return subprocess.check_output(['git','-C',str(where),*args])
def sha(b):return hashlib.sha256(b).hexdigest()
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def log(p):
 b=p.read_bytes();return b.decode('utf-16' if b[:2] in [b'\xff\xfe',b'\xfe\xff'] else 'utf-8-sig')
v=read(out/'verification.json');assert v['passed'] and v['tests']==70 and not v['physicalPhoneAccessed'] and not v['nativePickerAcceptance']
assert read(out/'device-baseline.json')==read(out/'device-after.json')
row={};passed=[];text=log(out/'instrumentation.log');assert 'OK (70 tests)' in text
for line in text.splitlines():
 if line.startswith('INSTRUMENTATION_STATUS: '):
  value=line.removeprefix('INSTRUMENTATION_STATUS: ')
  if '=' in value:k,val=value.split('=',1);row[k]=val
 elif line.startswith('INSTRUMENTATION_STATUS_CODE: '):
  if line.endswith(': 0'):passed.append((row['class'],row['test']))
  row={}
counts=collections.Counter(c.rsplit('.',1)[1] for c,t in passed)
assert len(passed)==len(set(passed))==70
assert counts=={'OnlinePendingBackupInstrumentedTest':8,'OnlinePendingCaptureInstrumentedTest':4,'OnlinePendingExportInstrumentedTest':9,'OnlinePendingInboxInstrumentedTest':9,'OnlineSyncInstrumentedTest':15,'OnlineLifecycleInstrumentedTest':7,'OnlineHttpInstrumentedTest':7,'OnlineQuantityRecoveryHttpInstrumentedTest':11}
proofs={}
for phase in ['stage','receive','source-verify']:
 t=log(out/f'install-{phase}.log');assert 'OK (1 test)' in t
 for bad in ['FAILURES!!!','INSTRUMENTATION_FAILED','shortMsg=','Process crashed','AssumptionViolated','STATUS_CODE: -3']:assert bad not in t
 lines=[line.split('ccsCrossInstallProof=',1)[1] for line in t.splitlines() if line.startswith('INSTRUMENTATION_STATUS: ccsCrossInstallProof=')]
 assert len(lines)==1;proofs[phase]=json.loads(lines[0]);assert proofs[phase]['mode']==phase
for bad in ['FAILURES!!!','INSTRUMENTATION_FAILED','shortMsg=','Process crashed','AssumptionViolated','STATUS_CODE: -3']:assert bad not in text
stage,receive,verify=[proofs[p] for p in ['stage','receive','source-verify']]
install=read(out/'secondary-installation.json')
assert stage['uid']==verify['uid']<100000 and receive['uid']//100000==install['user']>0
assert stage['uid']%100000==receive['uid']%100000 and stage['uid']!=receive['uid']
assert install['state']=='RUNNING_UNLOCKED' and stage['run']==receive['run']==verify['run']==install['run']
assert stage['sourceQueueUnchanged'] and verify['sourceQueueUnchanged'] and receive['noAutomaticSend']
assert receive['serverQuantity']==verify['serverQuantity']==8 and receive['newOperationRecorded'] and receive['originalHeadRecorded'] and not receive['originalTailRecorded']
body=(out/'quantity-install-pending-qa.json').read_bytes();packet=json.loads(body)
assert sha(body)==stage['sha256']==receive['sha256']==verify['sha256']
assert receive['newId'] not in [stage['head'],stage['tail']]
for field in ['backupId','sourceDeviceId']:assert str(uuid.UUID(packet[field]))==packet[field]
fixture=read(out/'fixture-identity.json');db=read(out/'database-after.json');assert fixture['ownedDatabase'] and db['internalErrorCount']==0 and db['fixturesRetained']
cross=[e for e in db['entries'] if e['business_date']=='2026-09-10'];assert len(cross)==2
changed=[e for e in cross if e['quantity']==8];assert len(changed)==1;menu=changed[0]['menu_id'];assert changed[0]['updated_by']==fixture['employee'] and changed[0]['entry_revision']=='2'
name=next(m['name'] for m in fixture['catalog']['menus'] if m['id']==menu)
expected=dict(format='closing-count-online-pending-backup',schemaVersion=1,backupId=packet['backupId'],createdAt=packet['createdAt'],sourceDeviceId=packet['sourceDeviceId'],origin='http://10.0.2.2:4371',account=dict(id=fixture['employee'],username=fixture['employeeUsername'],name='Dummy rani'),outlet=dict(id=fixture['outlet'],name=fixture['outletName']),closings=[dict(id=stage['closing'],date='2026-09-10',generation=1,revision=0,state='DRAFT',entries=[dict(menuId=menu,name=name,baseQuantity=0,baseRevision=0,localQuantity=8,status='PENDING')],operations=[dict(operationId=stage['head'],menuId=menu,generation=1,expectedRevision=0,quantity=4,status='SENT'),dict(operationId=stage['tail'],menuId=menu,generation=1,expectedRevision=None,quantity=8,status='QUEUED')],lifecycle=None)],admin=[])
assert packet==expected
crossReceipts=[r for r in db['receipts'] if r['business_date']=='2026-09-10'];assert {r['operation_id'] for r in crossReceipts}=={stage['head'],receive['newId']} and all(r['actor_id']==fixture['employee'] for r in crossReceipts)
crossAudit=[r for r in db['audit'] if r['business_date']=='2026-09-10'];assert [r['after_quantity'] for r in crossAudit]==[4,8] and all(r['actor_id']==fixture['employee'] for r in crossAudit)
audit=[r for r in db['audit'] if r['business_date']=='2026-09-01'];assert [r['after_quantity'] for r in audit]==[4,6,8] and [r['actor_id'] for r in audit]==[fixture['employee'],fixture['colleague'],fixture['employee']]
for date,value in [('2026-09-02',4),('2026-09-03',3),('2026-09-04',8),('2026-09-05',0),('2026-09-06',0),('2026-09-07',0),('2026-09-08',0),('2026-09-09',0)]:
 rows=[e for e in db['entries'] if e['business_date']==date];assert len(rows)==2 and sum(e['quantity'] for e in rows)==value,(date,rows)
assert all(e['generation']=='2' for e in db['entries'] if e['business_date']=='2026-09-09')
inputs=read(out/'apk-inputs.json')
for p in inputs:assert sha(Path(p['Path']).read_bytes()).upper()==p['Hash']
jvmRows=[ET.parse(p).getroot().attrib for p in (root/'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')]
assert sum(int(r['tests']) for r in jvmRows)==25 and all(int(r['failures'])==int(r['errors'])==int(r['skipped'])==0 for r in jvmRows)
lint=ET.parse(root/'app/build/reports/lint-results-debug.xml').getroot();warnings=sum(e.attrib['severity']=='Warning' for e in lint);assert not any(e.attrib['severity'] in ['Error','Fatal'] for e in lint)
proof=dict(passed=True,androidTests=70,testGroups=dict(counts),crossInstallationInvocations=3,appUids=[stage['uid'],receive['uid']],secondaryUser=install['user'],sameEmulator=True,sourceQueueUnchanged=True,noAutomaticSend=True,oldHeadRecorded=True,oldTailRecorded=False,newId=receive['newId'],newOperationRecorded=True,packetSha256=sha(body),packetBytes=len(body),packetBusinessModelCompared=True,serverAuditAndQuantitiesCompared=True,jvmTests=25,lintErrors=0,lintWarnings=warnings,deviceSettingsAndUsersRestored=True,scope='PT05 reviewed quantity recovery core; native picker/UI, remaining intent types, physical and public acceptance remain pending.')
(out/'runtime-proof.json').write_text(json.dumps(proof,indent=2)+'\n',encoding='utf8')
if '--staged' in sys.argv:
 assert git(root,'rev-parse','HEAD').decode().strip()==baseline
 assert git(root,'branch','--show-current').decode().strip()=='portfolio/closing-online' and not git(root,'diff','--name-only').strip()
 assert not git(root,'diff',baseline,'--','service','app/src/main/java/com/closingcount/app/ui','app/src/main/java/com/closingcount/app/MainActivity.kt','app/build.gradle.kts','gradle','build.gradle.kts','settings.gradle.kts','gradle.properties').strip()
 schema=read(root/'app/schemas/com.closingcount.app.data.local.ClosingCountDatabase/9.json')['database'];oldSchema=read(root/'app/schemas/com.closingcount.app.data.local.ClosingCountDatabase/8.json')['database']
 assert schema['version']==9 and [e for e in schema['entities'] if e['tableName']!='online_recovery_claims']==oldSchema['entities']
 for name,marker in [('data/local/OnlineSyncDao.kt','    @Query("SELECT * FROM online_lifecycle_operations'),('data/online/OnlineRepository.kt','    suspend fun team(outlet:'),('data/online/OnlineSyncEngine.kt','    private fun same(session:')]:
  rel='app/src/main/java/com/closingcount/app/'+name;old=git(root,'show',baseline+':'+rel).decode();new=(root/rel).read_text(encoding='utf8');assert old[old.index(marker):]==new[new.index(marker):],name
 rel='app/src/main/java/com/closingcount/app/data/local/OnlineMigration.kt';old=git(root,'show',baseline+':'+rel).decode().strip();new=(root/rel).read_text(encoding='utf8').split('val Migration8To9')[0].strip();assert old==new
 canonical=[]
 for repo in read(program/'evidence/portfolio-run-r1/git-setup.json')['repos']:
  folder=Path('D:/Code')/repo['path'];head=subprocess.run(['git','-C',str(folder),'rev-parse','--verify','HEAD'],capture_output=True)
  assert (head.stdout.decode().strip() if head.returncode==0 else None)==repo['head'];assert sha(git(folder,'status','--porcelain=v1','-uall'))==repo['sourceStatusSha256'];canonical.append(dict(code=repo['code'],headAndStatusUnchanged=True))
 secret=read(Path(os.environ['LOCALAPPDATA'])/'PortfolioQA/secrets/closing-count-local-r1.json');records=[]
 allowedMain={'data/local/'+n+'.kt' for n in ['ClosingCountDatabase','OnlineEntities','OnlineMigration','OnlineSyncDao']}|{'data/online/'+n+'.kt' for n in ['OnlineApi','OnlineRepository','OnlineSyncEngine']}|{'data/transfer/OnlineQuantityRecovery.kt'}
 allowedTest={'Online'+n+'InstrumentedTest.kt' for n in ['Http','LifecycleHttp','Lifecycle','ProcessRecovery','Sync','QuantityRecoveryHttp','QuantityRecoveryInstall']}
 for name in git(root,'diff','--cached','--name-only').decode().splitlines():
  assert name in {'app/src/main/java/com/closingcount/app/'+p for p in allowedMain}|{'app/src/androidTest/java/com/closingcount/app/'+p for p in allowedTest}|{'app/schemas/com.closingcount.app.data.local.ClosingCountDatabase/9.json','README.md','docs/ONLINE_PENDING_TRANSFER_PLAN_R1.md','docs/QUANTITY_RECOVERY_R1.md'} or name.startswith('docs/quantity-recovery-evidence-r1/'),name
  if name=='docs/quantity-recovery-evidence-r1/source-preflight.json':continue
  b=git(root,'show',':'+name)
  for key in ['adminPassword','appPassword','instance']:assert secret[key].encode() not in b,name
  records.append(dict(file=name,indexSha256=sha(b)))
 assert git(root,'show',':docs/quantity-recovery-evidence-r1/quantity-install-pending-qa.json')==body
 result=dict(passed=True,baseline=baseline,files=records,canonical=canonical,knownPrivateValuesAbsent=True,existingServiceUiDependencyUnchanged=True,existingDaoAndEngineMutationTailUnchanged=True,oldSchemasAndMigrationsPreserved=True,exactPacketIndexBytes=True,androidTests=70,crossInstallationInvocations=3,jvmTests=25,scope=proof['scope'])
 (out/'source-preflight.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8');print(json.dumps(dict(passed=True,stagedFilesExcludingPreflight=len(records),canonicalUnchanged=8,androidTests=70,crossInstallationInvocations=3)))
else:print(json.dumps(proof))
