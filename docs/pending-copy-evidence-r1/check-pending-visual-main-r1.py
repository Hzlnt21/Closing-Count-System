from pathlib import Path
import json,hashlib,xml.etree.ElementTree as ET
root=Path('D:/Code/Worktrees/Closing-Count-Portfolio')
out=Path('D:/Code/Documents/Career-Portfolio-2026/evidence/closing-count-pending-visual-main-r1')
def read(path):return json.loads(path.read_text(encoding='utf-8-sig'))
def log(path):
    b=path.read_bytes()
    return b.decode('utf-16' if b[:2] in [b'\xff\xfe',b'\xfe\xff'] else 'utf-8-sig')
v=read(out/'verification.json')
local=log(out/'local-instrumentation.log')
assert 'OK (7 tests)' in local
for bad in ['FAILURES!!!','INSTRUMENTATION_FAILED','shortMsg=','Process crashed','AssumptionViolated','STATUS_CODE: -3']:assert bad not in local
assert v['passed'] and v['deviceSettingsAndUsersRestored'] and not v['physicalPhoneAccessed'] and not v['completePt07Acceptance']
assert read(out/'device-baseline.json')==read(out/'device-after.json')
fixture=read(out/'fixture-identity.json');db=read(out/'database-after.json')
assert db['internalErrorCount']==0 and len(db['entries'])==8 and len(db['receipts'])==len(db['audit'])==8
cases=[]
for name,font,dark,date in [('visual-light',1,False,'2026-07-01'),('visual-dark',1,True,'2026-07-02'),('visual-light-large',2,False,'2026-07-03'),('visual-dark-large',2,True,'2026-07-04')]:
    text=log(out/(name+'.log'));assert 'OK (1 test)' in text
    for bad in ['FAILURES!!!','INSTRUMENTATION_FAILED','shortMsg=','Process crashed','AssumptionViolated','STATUS_CODE: -3']:assert bad not in text
    p=read(out/name/('pending-ui-'+name+'.json'))
    assert p['passed'] and p['existingDatabaseRestored'] and p['legacyDatabaseUnchanged'] and p['baselineSha256']==p['afterSha256']
    assert p['fontScale']==font and p['dark']==dark and not p['physicalPhoneAccessed']
    assert p['activityReviewRetained'] and p['frozenProviderPayloadAccepted'] and p['automaticSyncHeldOnResume'] and p['explicitFinalQuantity']==8
    assert p['actor']==fixture['employee'] and p['outlet']==fixture['outlet']
    assert len(p['layouts'])>=8 and all(r['measuredStaticTexts']>0 and not r['overflow'] for r in p['layouts'])
    rows=[r for r in db['entries'] if r['business_date']==date]
    audit=[r for r in db['audit'] if r['business_date']==date]
    assert len(rows)==2 and sum(r['quantity'] for r in rows)==8
    assert [r['after_quantity'] for r in audit]==[4,8] and all(r['actor_id']==fixture['employee'] for r in audit)
    assert sum(1 for r in db['receipts'] if r['business_date']==date)==2
    for scene in ['inbox','export-review','quantity-review','held-draft','synced']:
        image=out/name/f'pending-ui-{name}-{scene}.png'
        assert image.read_bytes().startswith(b'\x89PNG\r\n\x1a\n')
    cases.append(dict(case=name,fontScale=font,dark=dark,measuredScenes=len(p['layouts']),businessDate=date,serverAudit=[4,8],existingDataRestored=True))
for p in read(out/'apk-inputs.json'):assert hashlib.sha256(Path(p['Path']).read_bytes()).hexdigest().upper()==p['Hash']
jvm=[ET.parse(p).getroot().attrib for p in (root/'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')]
assert sum(int(r['tests']) for r in jvm)==25 and all(int(r['failures'])==int(r['errors'])==int(r['skipped'])==0 for r in jvm)
lint=ET.parse(root/'app/build/reports/lint-results-debug.xml').getroot()
assert not any(r.attrib['severity'] in ['Error','Fatal'] for r in lint)
result=dict(passed=True,localUiAndInboxTests=7,nativeQuantityJourneys=4,cases=cases,jvmTests=25,lintErrors=0,lintWarnings=sum(r.attrib['severity']=='Warning' for r in lint),independentDatabaseAuditMatched=True,deviceSettingsAndUsersRestored=True,physicalPhoneAccessed=False,completePt07Acceptance=False,remaining=['Native administrative continuations','Actual native process stop/restart','PT08 complete acceptance','CCS08 demo packaging'])
(out/'runtime-proof.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
print(json.dumps(result))
