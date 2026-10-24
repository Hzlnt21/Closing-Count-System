"""Fresh local DEMO journey; two explicitly owned emulator users, normal production UI.

No physical phone, source/operational DB, owner launch, password logging or PID kill.
Evidence namespaces are single-use. Failure preserves observations and performs guarded cleanup.
"""
from pathlib import Path
import hashlib,json,os,re,subprocess,time,uuid,sys

base=Path('D:/Code/Documents/Career-Portfolio-2026')
source=Path('D:/Code/Worktrees/Closing-Count-Portfolio')
attempt=sys.argv[1] if len(sys.argv)==2 else 'r1'
assert re.fullmatch(r'r[1-9]',attempt)
out=base/('evidence/closing-count-demo-native-'+attempt)
private=Path('D:/Code/ToolCache/PortfolioQA')/('Native Demo '+attempt.upper())/'ClosingCountDemo'
adb='D:/Code/ToolCache/AndroidSdk/platform-tools/adb.exe'
prefix=[adb,'-s','emulator-5580'];run=str(uuid.uuid4())
assert not out.exists() and not private.exists(),'Fresh namespaces required'
out.mkdir(parents=True);users={};baseline=None;owner_before=None;owned_emulator=False
passed=False;cleanup=[];proofs=[];active=None
def write(name,value):
    (out/name).write_text(json.dumps(value,indent=2)+'\n',encoding='utf8')
def cmd(args,timeout=60):
    r=subprocess.run(args,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=timeout)
    if r.returncode:
        if str(args[0])==adb:
            write('adb-host-failure-'+str(time.time_ns())+'.json',dict(exitCode=r.returncode,stderr=r.stderr.decode(errors='replace')[:3000],argumentsWithheld=True))
        raise RuntimeError('Command refused or failed; captured output withheld to protect private arguments')
    return r.stdout
def shell(*args):return cmd(prefix+['shell',*args]).decode().strip()
def emulator(action):
    # File-backed child output avoids waiting for a detached emulator descendant
    # to release inherited Windows pipe handles after PowerShell has exited.
    with (out/('emulator-'+action+'.stdout.log')).open('wb') as log,(out/('emulator-'+action+'.stderr.log')).open('wb') as err:
        result=subprocess.run(['powershell','-NoProfile','-File',str(base/'automation/QAEmulator.ps1'),'-Action',action,'-Profile','Phone','-Renderer','host','-MemoryMb','1536'],stdout=log,stderr=err,timeout=340)
    assert result.returncode==0
def identity():assert 'PortfolioQA_Phone_API36' in cmd(prefix+['emu','avd','name']).decode().splitlines()
def snapshot():
    return {k:shell(*v) for k,v in dict(size=['wm','size'],density=['wm','density'],font=['settings','get','system','font_scale'],night=['cmd','uimode','night'],users=['pm','list','users'],current=['am','get-current-user']).items()}
def owner_hash():return shell("run-as com.closingcount.app --user 0 sh -c 'sha256sum databases/closing_count.db*'")
def runtime(action):
    data=cmd(['node',str(source/'demo/runtime.mjs'),action,'--state-dir',str(private)],timeout=180)
    value=json.loads(data.decode().strip());write(f'runtime-{action}-{time.time_ns()}.json',value)
    print('Runtime '+action+' completed',flush=True)
def fixture():
    cmd(['node',str(base/'automation/ClosingDemoNativeFixtureR1.mjs'),'fixture',str(private),str(private/'native-handover.private.json')])
    return json.loads((private/'native-handover.private.json').read_text())
def guard(user):
    identity();assert user>0
    name=next(row['name'] for row in users.values() if row['id']==user)
    assert f'UserInfo{{{user}:{name}:' in shell('pm','list','users')
    rows=shell('cmd','package','list','packages','-U','--user',str(user),'com.closingcount.app')
    ids=re.findall(r'^package:com\.closingcount\.app\s+uid:(\d+)\s*$',rows,re.M)
    assert len(ids)==1 and int(ids[0])//100000==user
    return int(ids[0])
def stop_app(user):
    uid=guard(user);shell('am','force-stop','--user',str(user),'com.closingcount.app')
    for _ in range(24):
        rows=shell('ps','-A','-o','PID,UID,STAT,NAME')
        live=[r for r in rows.splitlines() if (m:=re.match(r'\s*(\d+)\s+(\d+)\s+(\S+)\s+(.+)',r)) and int(m[2])==uid and not m[3].startswith('Z')]
        if not live:return
        time.sleep(.25)
    raise RuntimeError('Owned package UID did not quiesce')
def private_diagnostics(name):
    # Android dumps may contain instrumentation extras. Never copy or print
    # these private diagnostic files into curated source/evidence.
    target=private/('diagnostic-'+name);target.mkdir(exist_ok=True)
    for key,args in [('crash',['logcat','-b','crash','-d','-v','brief']),('system',['logcat','-b','system','-b','events','-d','-v','brief']),('exit',['shell','dumpsys','activity','exit-info','com.closingcount.app'])]:
        try:
            r=subprocess.run(prefix+args,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=20)
            (target/(key+'.private.log')).write_bytes(r.stdout)
        except Exception:pass
def collect(user,phase,require_pass=True):
    uid=guard(user);ra=prefix+['exec-out','run-as','com.closingcount.app','--user',str(user)]
    directory=f'files/native-demo-{run}'
    names=cmd(ra+['ls','-1',directory]).decode().splitlines()
    dest=out/phase;dest.mkdir(exist_ok=True)
    (dest/'directory-listing.json').write_text(json.dumps(names,indent=2)+'\n')
    for name in names:
        if not name.startswith(phase+'-'):continue
        assert re.fullmatch(r'[a-z0-9-]+\.(?:png|json|xlsx)',name)
        data=cmd(ra+['cat',directory+'/'+name]);assert data
        (dest/name).write_bytes(data)
    if require_pass:
        p=json.loads((dest/(phase+'-proof.json')).read_text())
        assert p['passed'] and p['run']==run and p['user']==user and p['uid']==uid
        assert p['legacyBefore']==p['legacyAfter'] and not p['physicalPhoneAccessed']
        proofs.append(p)
    stop_app(user)
    room=dest/'room';room.mkdir(exist_ok=True)
    names=cmd(ra+['ls','-1','databases']).decode().splitlines()
    for name in ['closing_count.db','closing_count.db-wal','closing_count.db-shm']:
        if name in names:(room/name).write_bytes(cmd(ra+['cat','databases/'+name]))
    assert (room/'closing_count.db').read_bytes().startswith(b'SQLite format 3\0')
def phase(installation,name,g,nextg=None):
    global active
    user=users[installation]['id'];guard(user)
    # Keep the two installations and persistent drafts, with only one secondary
    # user's Android services running at a time on the small owned emulator.
    if shell('am','get-current-user')!=str(user):
        shell('am','switch-user','-w','0')
        for record in users.values():
            if record['id']!=user:
                guard(record['id']);shell('am','stop-user','-w','-f',str(record['id']))
        shell('am','start-user','-w',str(user))
        shell('am','switch-user','-w',str(user))
    assert shell('am','get-current-user')==str(user)
    write(name+'-user-runtime.json',dict(active=user,users=shell('pm','list','users'),twoInstallations=True,secondaryUsersRunSequentially=True))
    args=dict(UiQa='true',Run=run,User=str(user),Installation=installation,Phase=name,Outlet=g['outlet'],Closing=g['closing'],Cappuccino=g['ids']['cappuccino'],Latte=g['ids']['latte'],Cocoa=g['ids']['cocoa'],Date=g['anchor'])
    for key in ['rani','dimas','arif','dewi']:
        for suffix,field in [('Username','username'),('Password','password'),('Actor','id')]:args[key.title()+suffix]=g['actors'][key][field]
    if nextg:
        for suffix,field in [('Username','username'),('Password','password'),('Actor','id')]:args['NextRani'+suffix]=nextg['actors']['rani'][field]
        args.update(NextOutlet=nextg['outlet'],NextClosing=nextg['closing'],NextCappuccino=nextg['ids']['cappuccino'],NextLatte=nextg['ids']['latte'])
    # Quote validated alphanumeric credential/identity values for the remote shell;
    # the command line itself is never written to public logs or printed.
    assert all(re.fullmatch(r'[A-Za-z0-9!.:/_-]+',v) for v in args.values())
    remote=['am','instrument','--user',str(user),'-w','-r','-e','class','com.closingcount.app.OnlineDemoJourneyInstrumentedTest']
    for k,v in args.items():remote+=['-e','ccsDemo'+k,v]
    remote+=['com.closingcount.app.test/androidx.test.runner.AndroidJUnitRunner']
    print('Native phase '+name+' started',flush=True)
    active=(user,name)
    with (out/(name+'.stdout.log')).open('wb') as log,(out/(name+'.stderr.log')).open('wb') as err:
        p=subprocess.Popen(prefix+['shell',*remote],stdout=log,stderr=err)
        try:code=p.wait(timeout=420)
        except subprocess.TimeoutExpired:
            stop_app(user);p.wait(timeout=15);raise RuntimeError('Original native phase exceeded timeout') from None
    text=(out/(name+'.stdout.log')).read_text(errors='replace')
    if code or 'OK (1 test)' not in text or re.search(r'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=|Process crashed',text):
        private_diagnostics(name)
        collect(user,name,False);raise RuntimeError('Native phase failed: '+name)
    collect(user,name);active=None
    print('Native phase '+name+' passed',flush=True)
try:
    inputs=[source/'app/src/androidTest/java/com/closingcount/app/OnlineDemoJourneyInstrumentedTest.kt',Path(__file__),base/'automation/ClosingDemoNativeFixtureR1.mjs']
    write('source-inputs.json',dict(sourceBase=cmd(['git','-C',str(source),'rev-parse','HEAD']).decode().strip(),files=[dict(path=str(p),sha256=hashlib.sha256(p.read_bytes()).hexdigest()) for p in inputs],coreApplicationChanged=False,physicalPhoneAccessed=False))
    runtime('init');runtime('start');g=fixture();assert g['number']==1
    emulator('start')
    identity();owned_emulator=True;baseline=snapshot();assert baseline['current']=='0';write('device-baseline.json',baseline)
    assert re.fullmatch(r'Night mode: (no|yes|auto)',baseline['night'])
    owner_before=owner_hash();(out/'owner-before.sha256').write_text(owner_before)
    for apk in [source/'app/build/outputs/apk/debug/app-debug.apk',source/'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']:
        cmd(prefix+['install','-r',str(apk)],timeout=90)
        write(apk.name+'.json',dict(sha256=hashlib.sha256(apk.read_bytes()).hexdigest(),bytes=apk.stat().st_size))
    for key in ['a','b']:
        name='PortfolioQADemo'+run[:8]+key
        result=shell('pm','create-user',name);match=re.search(r'Success: created user id (\d+)',result);assert match
        user=int(match[1]);assert user>0;users[key]=dict(id=user,name=name);write('owned-installations.json',dict(run=run,users=users))
        for package in ['com.closingcount.app','com.closingcount.app.test']:shell('cmd','package','install-existing','--user',str(user),package)
        guard(user)
        disabled=[]
        for package in ['com.google.android.gms','com.google.android.gsf','com.android.vending','com.google.android.googlequicksearchbox','com.google.android.apps.wellbeing','com.google.android.apps.messaging','com.google.android.tts','com.google.android.partnersetup','com.google.android.setupwizard','com.android.chrome','com.google.android.apps.photos']:
            if 'package:'+package in shell('pm','list','packages','--user',str(user),package).splitlines():
                assert 'disabled-user' in shell('pm','disable-user','--user',str(user),package);disabled.append(package)
        write('temporary-'+key+'-profile.json',dict(user=user,disabled=disabled,ownerChanged=False,customEmulatorEnvironment=True))
    phase('a','rani-prime',g);phase('b','dimas-work',g)
    runtime('stop');phase('a','rani-offline',g);runtime('start')
    phase('a','rani-resolve',g);phase('a','supervisor',g);phase('a','admin',g)
    runtime('stop');phase('b','dimas-offline',g);runtime('start');runtime('reset');nextg=fixture();assert nextg['number']==2
    phase('b','after-reset',g);phase('a','new-generation',g,nextg)
    cmd(['node',str(base/'automation/ClosingDemoNativeFixtureR1.mjs'),'snapshot',str(private),str(out/'server-snapshot.json')])
    assert len(proofs)==9;passed=True
except Exception as error:
    write('failure.json',dict(type=type(error).__name__,message=str(error) if isinstance(error,RuntimeError) else 'Assertion or host check failed; inspect retained observations',active=active))
    print('Native journey did not pass; observations retained',flush=True)
finally:
    if owned_emulator:
        if active:
            try:collect(*active,require_pass=False)
            except Exception:cleanup.append('Failure evidence collection incomplete')
        for record in users.values():
            try:stop_app(record['id'])
            except Exception:cleanup.append('Owned app stop not verified')
        try:
            shell('am','switch-user','-w','0');assert shell('am','get-current-user')=='0'
            # Global night mode may be inherited by the foreground user; restore
            # exactly the accepted owner baseline. No owner font/density change.
            shell('cmd','uimode','night',baseline['night'].split(': ')[1])
            for record in users.values():
                user=record['id'];guard(user);shell('am','stop-user','-w','-f',str(user));shell('pm','remove-user',str(user))
            for _ in range(30):
                if all(f"UserInfo{{{v['id']}:" not in shell('pm','list','users') for v in users.values()):break
                time.sleep(.5)
            after=snapshot();write('device-after.json',after);assert after==baseline
            owner_after=owner_hash();(out/'owner-after.sha256').write_text(owner_after);assert owner_before==owner_after
        except Exception:cleanup.append('Owner baseline or owned user cleanup not verified')
        try:emulator('stop')
        except Exception:cleanup.append('Owned emulator stop not verified')
    if private.exists():
        try:runtime('stop')
        except Exception:cleanup.append('Owned runtime stop not verified')
    write('verification.json',dict(passed=passed and not cleanup,phases=proofs,cleanupErrors=cleanup,physicalPhoneAccessed=False,customEmulatorEnvironment=True,completeCcs08=False))
sys.exit(0 if passed and not cleanup else 1)
