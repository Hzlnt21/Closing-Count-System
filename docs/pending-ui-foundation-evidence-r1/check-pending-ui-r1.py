from pathlib import Path
import subprocess,json,hashlib,os,sys,collections,xml.etree.ElementTree as ET,uuid
root=Path('D:/Code/Worktrees/Closing-Count-Portfolio');program=Path('D:/Code/Documents/Career-Portfolio-2026')
out=program/'evidence/closing-count-pending-ui-r1';baseline='b13635d9b48171df92012e94da4a1d8daac08619'
def git(where,*args):return subprocess.check_output(['git','-C',str(where),*args])
def sha(b):return hashlib.sha256(b).hexdigest()
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def log(p):
 b=p.read_bytes();return b.decode('utf-16' if b[:2] in [b'\xff\xfe',b'\xfe\xff'] else 'utf-8-sig')
v=read(out/'verification.json');assert v['passed'] and v['tests']==95 and not v['physicalPhoneAccessed'] and v['nativePickerJourneyPassed'] and not v['completePt07Acceptance']
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
for p in inputs:
 if p['Path'].endswith('/debug/app-debug.apk'):assert sha(Path(p['Path']).read_bytes()).upper()==p['Hash']
jvmRows=[ET.parse(p).getroot().attrib for p in (root/'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')]
assert sum(int(r['tests']) for r in jvmRows)==25 and all(int(r['failures'])==int(r['errors'])==int(r['skipped'])==0 for r in jvmRows)
lint=ET.parse(root/'app/build/reports/lint-results-debug.xml').getroot();warnings=sum(e.attrib['severity']=='Warning' for e in lint);assert not any(e.attrib['severity'] in ['Error','Fatal'] for e in lint)
proof=dict(passed=True,androidTests=70,testGroups=dict(counts),crossInstallationInvocations=3,appUids=[stage['uid'],receive['uid']],secondaryUser=install['user'],sameEmulator=True,sourceQueueUnchanged=True,noAutomaticSend=True,oldHeadRecorded=True,oldTailRecorded=False,newId=receive['newId'],newOperationRecorded=True,packetSha256=sha(body),packetBytes=len(body),packetBusinessModelCompared=True,serverAuditAndQuantitiesCompared=True,jvmTests=25,lintErrors=0,lintWarnings=warnings,deviceSettingsAndUsersRestored=True,scope='PT05 reviewed quantity recovery core; native picker/UI, remaining intent types, physical and public acceptance remain pending.')
(out/'runtime-proof.json').write_text(json.dumps(proof,indent=2)+'\n',encoding='utf8')

adminText=log(out/'admin-instrumentation.log');assert 'OK (18 tests)' in adminText
adminCases=[];row={}
for line in adminText.splitlines():
 if line.startswith('INSTRUMENTATION_STATUS: '):
  value=line.removeprefix('INSTRUMENTATION_STATUS: ')
  if '=' in value:k,val=value.split('=',1);row[k]=val
 elif line.startswith('INSTRUMENTATION_STATUS_CODE: '):
  if line.endswith(': 0'):adminCases.append((row['class'],row['test']))
  row={}
adminCounts=collections.Counter(c.rsplit('.',1)[1] for c,t in adminCases)
assert len(adminCases)==len(set(adminCases))==18 and adminCounts=={'OnlineRecoveredAdminInstrumentedTest':10,'OnlineIntentRecoveryHttpInstrumentedTest':8}
for bad in ['FAILURES!!!','INSTRUMENTATION_FAILED','shortMsg=','Process crashed','AssumptionViolated','STATUS_CODE: -3']:assert bad not in adminText
a=read(out/'admin-database-after.json');assert a['outletId']==fixture['adminOutlet']
assert {k:len(a[k]) for k in ['outlets','memberships','catalog_versions','operation_receipts','outlet_settings_audit','membership_audit','closings','closing_audit']}==dict(outlets=1,memberships=5,catalog_versions=2,operation_receipts=14,outlet_settings_audit=3,membership_audit=5,closings=2,closing_audit=7)
def withoutSecrets(value):
 if isinstance(value,dict):
  for k,v in value.items():assert k.lower() not in {'password','password_hash','passwordhash','access_token','accesstoken','token','fingerprint'};withoutSecrets(v)
 elif isinstance(value,list):
  for v in value:withoutSecrets(v)
withoutSecrets(a);assert 'New recovery QA!2026' not in json.dumps(a)
members={m['id']:m for m in a['members']}
assert members[fixture['admin']]['role']==members[fixture['backupAdmin']]['role']=='ADMIN'
assert members[fixture['employee']]['role']==members[fixture['colleague']]['role']=='EMPLOYEE'
assert members[fixture['admin']]['revision']==members[fixture['colleague']]['revision']=='2'
created=[m for m in a['members'] if m['id'] not in [fixture['admin'],fixture['backupAdmin'],fixture['employee'],fixture['colleague']]]
assert len(created)==1 and created[0]['username'].startswith('recovery.') and created[0]['display_name']=='Recovered person QA' and created[0]['role']=='EMPLOYEE'
settings=a['outlets'][0];assert settings['settings_revision']==3
assert {r['after_value']['name'] for r in a['outlet_settings_audit']}=={'Recovered settings QA','Committed QA','Changed after review QA'}
catalogs=sorted(a['catalog_versions'],key=lambda r:r['revision']);assert [r['revision'] for r in catalogs]==[1,2]
oldCatalog,newCatalog=[r['snapshot'] for r in catalogs]
expectedCatalog=json.loads(json.dumps(oldCatalog))
for menu in expectedCatalog['menus']:menu['name']='Recovered '+menu['name']
assert newCatalog==expectedCatalog and catalogs[1]['created_by']==fixture['admin']
closings={r['business_date']:r for r in a['closings']}
assert (closings['2026-08-01']['state'],closings['2026-08-01']['generation'],closings['2026-08-01']['revision'])==('DRAFT',3,5)
assert (closings['2026-08-02']['state'],closings['2026-08-02']['generation'],closings['2026-08-02']['revision'])==('DRAFT',1,0)
assert len({r['operation_id'] for r in a['operation_receipts']})==14
proof.update(androidTests=88,adminStoreTests=10,intentHttpTests=8,testGroups=dict(counts+adminCounts),adminReceipts=14,adminAuditCounts=dict(settings=3,memberships=5,closing=7),allIntentKindsVerified=True,passwordAbsentFromStoredPayloadAndEvidence=True,roomSchema=10,scope='PT06 lifecycle/settings/team/catalog recovery core plus PT01-05 regressions; native picker/review UI, physical and public acceptance remain pending.')
(out/'runtime-proof.json').write_text(json.dumps(proof,indent=2)+'\n',encoding='utf8')

localText=log(out/'pending-ui-local.log');assert 'OK (7 tests)' in localText
nativeText=log(out/'native-light-r2.log');assert 'OK (1 test)' in nativeText
for text in [localText,nativeText]:
 for bad in ['FAILURES!!!','INSTRUMENTATION_FAILED','shortMsg=','Process crashed','AssumptionViolated','STATUS_CODE: -3']:assert bad not in text
nativeFiles=list((out/'native-light-r2').rglob('pending-ui-native-light-r2.json'));assert len(nativeFiles)==1
native=read(nativeFiles[0]);assert native['passed'] and native['existingDatabaseRestored'] and native['legacyDatabaseUnchanged'] and native['activityReviewRetained'] and native['frozenProviderPayloadAccepted'] and native['automaticSyncHeldOnResume'] and native['explicitFinalQuantity']==8 and not native['physicalPhoneAccessed']
assert native['baselineSha256']==native['afterSha256']
assert native['actor']==fixture['employee'] and native['outlet']==fixture['outlet']
assert len(native['exportSha256'])==64
uiRows=[e for e in db['entries'] if e['business_date']=='2026-07-02'];assert len(uiRows)==2 and sum(e['quantity'] for e in uiRows)==8
uiAudit=[e for e in db['audit'] if e['business_date']=='2026-07-02'];assert [e['after_quantity'] for e in uiAudit]==[4,8] and all(e['actor_id']==fixture['employee'] for e in uiAudit)
proof.update(androidTests=95,testGroups=dict(counts+adminCounts+collections.Counter({'OnlinePendingInboxManagementInstrumentedTest':4,'OnlinePendingTransferViewModelInstrumentedTest':3})),localUiControllerAndInboxTests=7,nativePickerJourneys=1,nativeMainActivityAndDocumentsUiPassed=True,completePt07Acceptance=False,physicalPhoneAccessed=False,scope='PT01-06 regressions and initial PT07 native light picker/quantity journey. Dark/large text, native admin routes and actual process restart remain pending.')
(out/'runtime-proof.json').write_text(json.dumps(proof,indent=2)+'\n',encoding='utf-8')
print(json.dumps(proof))
