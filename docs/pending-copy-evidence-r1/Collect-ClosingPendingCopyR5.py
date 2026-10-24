"""Collect exact draft-test artifacts from the named isolated QA installation only."""
from pathlib import Path
import hashlib,json,re,subprocess,sys

user=int(sys.argv[1]);phase=sys.argv[2]
assert user>0 and phase in ['settings','team','catalog','lifecycle']
frozen=len(sys.argv)==4 and sys.argv[3]=='--frozen'
assert len(sys.argv)==3 or frozen
out=Path('D:/Code/Documents/Career-Portfolio-2026/evidence/closing-count-pending-copy-r5')
owned=json.loads((out/'secondary-installation.json').read_text(encoding='utf-8-sig'))
assert owned['user']==user
adb='D:/Code/ToolCache/AndroidSdk/platform-tools/adb.exe'
avd=subprocess.check_output([adb,'-s','emulator-5580','emu','avd','name'],timeout=10).decode()
assert 'PortfolioQA_Phone_API36' in avd.splitlines()
users=subprocess.check_output([adb,'-s','emulator-5580','shell','pm','list','users'],timeout=10).decode()
assert f"UserInfo{{{user}:{owned['name']}:" in users
package_rows=subprocess.check_output([adb,'-s','emulator-5580','shell','cmd','package','list','packages','-U','--user',str(user),'com.closingcount.app'],timeout=10).decode()
package_uids=re.findall(r'^package:com\.closingcount\.app\s+uid:(\d+)\s*$',package_rows,re.M)
assert len(package_uids)==1
package_uid=int(package_uids[0]);assert package_uid//100000==user
run_as=[adb,'-s','emulator-5580','exec-out','run-as','com.closingcount.app','--user',str(user)]

scenes={
    'settings':['review','held-after-recreation','confirmed','1-handled'],
    'team':['create-review','fresh-password-required','create-identity-source','update-identity-source','created','2-handled','update-review','update-held','updated','3-handled'],
    'catalog':['review','file-recipes','server-recipes','editable-after-recreation','publish-review','published','4-handled'],
    'lifecycle':[scene for index,action in enumerate(['submit','return','submit','lock','reopen']) for scene in [f'{index}-{action}-review',f'{index}-{action}-held',f'{index}-{action}-confirmed',f'{index+5}-handled']],
}[phase]
counts={'settings':1,'team':3,'catalog':4,'lifecycle':9}
phase_counts={'settings':1,'team':2,'catalog':1,'lifecycle':5}
phase_kinds={'settings':['SETTINGS'],'team':['TEAM_CREATE','TEAM_UPDATE'],'catalog':['CATALOG'],'lifecycle':['LIFECYCLE']*5}
if frozen:
    proof=json.loads((out/f'native-pending-admin-{phase}.json').read_text(encoding='utf-8'))
    assert proof['run']==owned['run'] and proof['user']==user and proof['phase']==phase
    assert proof['uid']==package_uid
    snapshot=out/'snapshots'/phase
    snapshot.mkdir(parents=True,exist_ok=True)
    processes=subprocess.check_output([adb,'-s','emulator-5580','shell','ps','-A','-o','PID,UID,STAT,NAME'],timeout=10).decode()
    (snapshot/'frozen-guard-package.txt').write_text(package_rows,encoding='utf-8')
    (snapshot/'frozen-guard-processes.txt').write_text(processes,encoding='utf-8')
    rows=[match for line in processes.splitlines() if (match:=re.fullmatch(r'\s*(\d+)\s+(\d+)\s+(\S+)\s+(.+?)\s*',line))]
    live=[row.group(0) for row in rows if int(row[2])==package_uid and not row[3].startswith('Z')]
    assert not live,'Owned private app UID must have no live writers before frozen collection'
    packet_listing=subprocess.check_output(run_as+['ls','-1','no_backup/online-recovery-inbox'],timeout=10).decode()
    (snapshot/'inbox-directory-listing.txt').write_text(packet_listing,encoding='utf-8')
    packets=packet_listing.splitlines()
    accepted=sorted(name for name in packets if re.fullmatch(r'[a-f0-9]{64}\.json',name))
    assert len(accepted)==counts[phase]
    expected={row['packetSha256']+'.json' for row in proof['allCases']}
    assert set(accepted)==expected
    manifest={'phase':phase,'user':user,'uid':proof['uid'],'run':owned['run'],'appStopped':True,'appUidQuiescent':True,'guardMode':'exact-owned-user-package-uid-normal-teardown','liveStopClaim':False,'inbox':[],'room':[],'sessionCredentialsCollected':False}
    def save(relative,data):
        target=snapshot/relative;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
        if phase=='lifecycle':
            alias=out/relative;alias.parent.mkdir(parents=True,exist_ok=True);alias.write_bytes(data)
    for name in accepted:
        data=subprocess.check_output(run_as+['cat','no_backup/online-recovery-inbox/'+name],timeout=10)
        assert 0<len(data)<=20*1024*1024
        digest=hashlib.sha256(data).hexdigest();assert digest+'.json'==name
        json.loads(data)
        save(Path('inbox')/name,data)
        manifest['inbox'].append({'name':name,'bytes':len(data),'sha256':digest})
    database_listing=subprocess.check_output(run_as+['ls','-1','databases'],timeout=10).decode()
    (snapshot/'room-directory-listing.txt').write_text(database_listing,encoding='utf-8')
    database_names=database_listing.splitlines()
    assert 'closing_count.db' in database_names
    for name in ['closing_count.db','closing_count.db-wal','closing_count.db-shm']:
        if name not in database_names:continue
        data=subprocess.check_output(run_as+['cat','databases/'+name],timeout=10)
        assert len(data)<=256*1024*1024
        if name=='closing_count.db':assert data.startswith(b'SQLite format 3\x00')
        save(Path('room')/name,data)
        manifest['room'].append({'name':name,'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()})
    (snapshot/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
    if phase=='lifecycle':(out/'frozen-secondary-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(dict(collected=True,frozen=True,user=user,phase=phase,inboxFiles=len(accepted),roomFiles=len(manifest['room']),sessionCredentialsCollected=False)))
    sys.exit(0)
names=[f'native-pending-admin-{phase}.json']+[f'native-pending-admin-{phase}-{scene}.png' for scene in scenes]
for name in names:
    data=subprocess.check_output(run_as+['cat','files/'+name],timeout=10)
    assert data,'Empty isolated-user evidence response'
    if name.endswith('.png'):
        assert data.startswith(b'\x89PNG\r\n\x1a\n')
    else:
        proof=json.loads(data)
        assert proof['passed'] and proof['phase']==phase and proof['run']==owned['run']
        assert proof['user']==user and proof['uid']==package_uid and proof['pid']>0
        assert proof['inboxCount']==counts[phase] and len(proof['allCases'])==counts[phase]
        assert len(proof['phaseCases'])==phase_counts[phase]
        assert [row['kind'] for row in proof['phaseCases']]==phase_kinds[phase]
        assert proof['legacyUnchanged'] and proof['legacyBeforeSha256']==proof['legacyAfterSha256']
        assert proof['allFinalSendsUsedProductionUi'] and proof['packetsSeededDirectlyIntoInbox']
        assert not proof['documentsPickerExercised'] and not proof['physicalPhoneAccessed'] and not proof['completePt07Acceptance']
        assert len({row['originalId'] for row in proof['allCases']})==counts[phase]
        assert len({row['newId'] for row in proof['allCases']})==counts[phase]
        assert all(row['originalId']!=row['newId'] and not row['originalRecorded'] and row['newRecorded'] and row['duplicateActionBlocked'] for row in proof['allCases'])
        if phase=='lifecycle':
            assert [row['action'] for row in proof['phaseCases']]==['submit','return','submit','lock','reopen']
    (out/name).write_bytes(data)
print(json.dumps(dict(collected=True,user=user,phase=phase,files=len(names),cumulativeCases=counts[phase],phaseCases=phase_counts[phase])))
