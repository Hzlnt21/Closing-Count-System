from pathlib import Path
import json,hashlib
root=Path('D:/Code/Worktrees/Closing-Count-Portfolio')
out=Path('D:/Code/Documents/Career-Portfolio-2026/evidence/closing-count-native-restart-r5')
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
v=read(out/'verification.json');assert v['passed'] and not v['cleanupErrors']
assert v['deviceSettingsUsersAndCurrentUserRestored'] and v['primaryRoomFilesUnchanged'] and not v['physicalPhoneAccessed'] and not v['completePt07Acceptance']
assert read(out/'device-baseline.json')==read(out/'device-after.json')
assert (out/'primary-room-before.sha256').read_bytes()==(out/'primary-room-after.sha256').read_bytes()
phases=v['phases'];assert [p['mode'] for p in phases]==['stage-review','stage-draft','verify']
assert len({p['pid'] for p in phases})==3 and len({p['uid'] for p in phases})==1
owned=read(out/'secondary-installation.json');assert owned['user']>0 and all(p['uid']//100000==owned['user'] and p['user']==owned['user'] for p in phases)
assert all(p['passed'] and p['activityResumed'] and p['legacyUnchanged'] and p['freshPrivateAuthority'] for p in phases)
for p in phases:
    m=p['mode'];process=read(out/(m+'-process.json'));text=(out/process['stdout']).read_text(encoding='utf-8-sig')
    saved=read(out/f'native-pending-stop-{m}.json');assert saved==p
    assert process['appPid']==p['pid'] and process['appUid']==p['uid']
    if m!='verify':
        assert process['expectedAbort'] and p['waitingForHostStop'] and 'OK (1 test)' not in text and 'Host did not stop the process' not in text
        assert str(p['pid']) in (out/(m+'-live-process.txt')).read_text(encoding='utf-8-sig')
        assert 'MainActivity' in (out/(m+'-live-foreground.txt')).read_text(encoding='utf-8-sig')
    else:
        assert not process['expectedAbort'] and not p['waitingForHostStop'] and process['exitCode']==0 and 'OK (1 test)' in text
        for bad in ['FAILURES!!!','INSTRUMENTATION_FAILED','shortMsg=','Process crashed','AssumptionViolated','STATUS_CODE: -3']:assert bad not in text
assert len(v['stops'])==2
for stop,p in zip(v['stops'],phases[:2]):
    assert stop['pid']==p['pid'] and stop['uid']==p['uid'] and stop['expectedAbort'] and stop['observedGone'] and stop['liveForegroundBeforeStop'] and stop['waitingForHostStop']
assert phases[1]['freshReviewRequired'] and phases[1]['noAutomaticSend']
assert phases[2]['localQueueAcknowledged']
assert phases[2]['heldDraftSurvivedProcessStop'] and phases[2]['provenanceSurvived'] and phases[2]['explicitFinalQuantity']==8
assert len({p['fingerprint'] for p in phases})==len({p['closing'] for p in phases})==1
fixture=read(out/'fixture-identity.json');db=read(out/'database-after.json')
assert all(p['actor']==fixture['employee'] and p['outlet']==fixture['outlet'] for p in phases)
assert db['internalErrorCount']==0 and len(db['entries'])==2 and len(db['receipts'])==len(db['audit'])==2
assert all(r['business_date']=='2026-07-05' and r['generation']=='1' and r['revision']=='2' for r in db['entries']) and sum(r['quantity'] for r in db['entries'])==8
assert [r['after_quantity'] for r in db['audit']]==[4,8] and all(r['actor_id']==fixture['employee'] for r in db['audit'])
assert phases[1]['newOperation'] in {r['operation_id'] for r in db['receipts']} and all(r['actor_id']==fixture['employee'] for r in db['receipts'])
for artifact in ['stage-review-review','stage-draft-held-draft','verify-held-after-stop','verify-already-recovered']:
    assert (out/('native-pending-stop-'+artifact+'.png')).read_bytes().startswith(b'\x89PNG\r\n\x1a\n')
for p in read(out/'apk-inputs.json'):assert hashlib.sha256(Path(p['Path']).read_bytes()).hexdigest().upper()==p['Hash']
result=dict(passed=True,readyStagesKilledLive=2,normalFinalVerificationTests=1,distinctProcesses=[p['pid'] for p in phases],isolatedAppUid=phases[0]['uid'],sameEmulator=True,acceptedInboxPersisted=True,freshPrivateReviewRequired=True,heldDraftAndProvenancePersisted=True,noAutomaticSend=True,explicitServerAudit=[4,8],independentPostgresAuditMatched=True,ownerRoomFilesUnchanged=True,deviceSettingsUsersAndCurrentUserRestored=True,physicalPhoneAccessed=False,completePt07Acceptance=False,remaining=['Native administrative recovery continuations','PT08 complete acceptance','CCS08 demo package'])
(out/'runtime-proof.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8');print(json.dumps(result))
