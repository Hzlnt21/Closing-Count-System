"""Real native old/fixed ordering control on the preserved R5 UUID counterexample.

Only views existing closings/reports; normal login/logout and idempotent open.
Compare business snapshots before/after. Exact temporary users/owner hashes guarded.
"""
from pathlib import Path
import hashlib,json,re,subprocess,time,uuid,sys
base=Path('D:/Code/Documents/Career-Portfolio-2026');repo=Path('D:/Code/Worktrees/Closing-Count-Portfolio')
attempt=sys.argv[1] if len(sys.argv)>1 else 'r1'
assert re.fullmatch(r'r[1-9][0-9]*',attempt)
out=base/('evidence/closing-count-demo-ordering-'+attempt);assert not out.exists();out.mkdir()
private=Path('D:/Code/ToolCache/PortfolioQA/Native Demo R5/ClosingCountDemo')
adb='D:/Code/ToolCache/AndroidSdk/platform-tools/adb.exe';prefix=[adb,'-s','emulator-5580']
run=str(uuid.uuid4());users={};owned=False;before=None;owner_before=None;passed=False;errors=[];results=[]
def write(name,value): (out/name).write_text(json.dumps(value,indent=2)+'\n',encoding='utf8')
def cmd(args,timeout=90):
    result=subprocess.run(args,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=timeout)
    if result.returncode:
        if str(args[0])==adb:write('adb-error-'+str(time.time_ns())+'.json',dict(code=result.returncode,stderr=result.stderr.decode(errors='replace')[:2000]))
        raise RuntimeError('Guarded command failed; arguments withheld')
    return result.stdout
def shell(*args):return cmd(prefix+['shell',*args]).decode().strip()
def identity():assert 'PortfolioQA_Phone_API36' in cmd(prefix+['emu','avd','name']).decode().splitlines()
def emulator(action):
    with (out/('emulator-'+action+'.stdout.log')).open('wb') as log,(out/('emulator-'+action+'.stderr.log')).open('wb') as err:
        result=subprocess.run(['powershell','-NoProfile','-File',str(base/'automation/QAEmulator.ps1'),'-Action',action,'-Profile','Phone','-Renderer','host','-MemoryMb','1536'],stdout=log,stderr=err,timeout=340)
    assert result.returncode==0
def snapshot():return {k:shell(*v) for k,v in dict(size=['wm','size'],density=['wm','density'],font=['settings','get','system','font_scale'],night=['cmd','uimode','night'],users=['pm','list','users'],current=['am','get-current-user']).items()}
def owner_hash():return shell("run-as com.closingcount.app --user 0 sh -c 'sha256sum databases/closing_count.db*'")
def guard(user):
    identity();assert user>0 and user in users
    assert f'UserInfo{{{user}:{users[user]}:' in shell('pm','list','users')
    ids=re.findall(r'^package:com\.closingcount\.app\s+uid:(\d+)\s*$',shell('cmd','package','list','packages','-U','--user',str(user),'com.closingcount.app'),re.M)
    assert len(ids)==1 and int(ids[0])//100000==user;return int(ids[0])
def stop(user):
    uid=guard(user);shell('am','force-stop','--user',str(user),'com.closingcount.app')
    for _ in range(24):
        live=[line for line in shell('ps','-A','-o','PID,UID,STAT,NAME').splitlines() if (m:=re.match(r'\s*(\d+)\s+(\d+)\s+(\S+)\s+(.+)',line)) and int(m[2])==uid and not m[3].startswith('Z')]
        if not live:return
        time.sleep(.25)
    raise RuntimeError('Owned UID still active')
def remove(user):
    guard(user);stop(user);shell('am','switch-user','-w','0');shell('am','stop-user','-w','-f',str(user));shell('pm','remove-user',str(user))
    for _ in range(30):
        if f'UserInfo{{{user}:' not in shell('pm','list','users'):del users[user];return
        time.sleep(.5)
    raise RuntimeError('Owned user removal not verified')
def runtime(action):
    value=json.loads(cmd(['node',str(repo/'demo/runtime.mjs'),action,'--state-dir',str(private)],180).decode());write('runtime-'+action+'.json',value)
def business(name):
    cmd(['node',str(base/'automation/ClosingDemoNativeFixtureR1.mjs'),'snapshot',str(private),str(out/name)])
    return json.loads((out/name).read_text())
def collect(user,case):
    guard(user);ra=prefix+['exec-out','run-as','com.closingcount.app','--user',str(user)]
    directory=f'files/native-order-{run}';names=cmd(ra+['ls','-1',directory]).decode().splitlines()
    dest=out/case;dest.mkdir(exist_ok=True)
    for name in names:
        assert re.fullmatch(r'[a-z-]+\.(png|json)',name);(dest/name).write_bytes(cmd(ra+['cat',directory+'/'+name]))
    return json.loads((dest/'proof.json').read_text())
try:
    files=[Path(__file__),repo/'app/src/androidTest/java/com/closingcount/app/OnlineDemoOrderingInstrumentedTest.kt',repo/'app/src/main/java/com/closingcount/app/data/online/OnlineCatalog.kt',repo/'app/src/main/java/com/closingcount/app/ui/online/OnlineViewModel.kt',repo/'app/src/main/java/com/closingcount/app/ui/online/OnlineClosingApp.kt',repo/'app/build.gradle.kts']
    write('source-inputs.json',dict(sourceBase=cmd(['git','-C',str(repo),'rev-parse','HEAD']).decode().strip(),files=[dict(path=str(p),sha256=hashlib.sha256(p.read_bytes()).hexdigest()) for p in files]))
    runtime('start');initial=business('business-before.json')
    cmd(['node',str(base/'automation/ClosingDemoNativeFixtureR1.mjs'),'fixture',str(private),str(private/'ordering-handover.private.json')])
    fixture=json.loads((private/'ordering-handover.private.json').read_text());assert fixture['number']==2
    ids=fixture['ids'];assert ids['latte']<ids['cappuccino'],'Preserved UUID counterexample required'
    history=next(c for c in initial['closings'] if c['outlet_id']==fixture['outlet'] and c['id']!=fixture['closing']);assert history['state']=='LOCKED'
    old=Path('D:/Code/ToolCache/PortfolioQA/closing-count-native-baseline-0.1.1.apk')
    assert hashlib.sha256(old.read_bytes()).hexdigest()==json.loads((base/'evidence/closing-count-demo-native-r5/app-debug.apk.json').read_text())['sha256']
    emulator('start');identity();owned=True;before=snapshot();assert before['current']=='0';write('device-before.json',before)
    owner_before=owner_hash();(out/'owner-before.sha256').write_text(owner_before)
    testapk=repo/'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
    for case,version,apk in [('control','0.1.1',old),('fixed','0.1.2',repo/'app/build/outputs/apk/debug/app-debug.apk')]:
        print('Ordering '+case+' preparation',flush=True)
        for file in [apk,testapk]:cmd(prefix+['install','-r','-d',str(file)])
        write(case+'-apks.json',[dict(file=p.name,sha256=hashlib.sha256(p.read_bytes()).hexdigest()) for p in [apk,testapk]])
        name='PortfolioQAOrder'+run[:8]+case
        match=re.search(r'Success: created user id (\d+)',shell('pm','create-user',name));assert match;user=int(match[1]);assert user>0;users[user]=name;write('owned-users.json',dict(run=run,users=users))
        for package in ['com.closingcount.app','com.closingcount.app.test']:shell('cmd','package','install-existing','--user',str(user),package)
        guard(user)
        disabled=[]
        for package in ['com.google.android.gms','com.google.android.gsf','com.android.vending','com.google.android.googlequicksearchbox','com.google.android.apps.wellbeing','com.google.android.apps.messaging','com.google.android.tts','com.google.android.partnersetup','com.google.android.setupwizard','com.android.chrome','com.google.android.apps.photos']:
            if 'package:'+package in shell('pm','list','packages','--user',str(user),package).splitlines():assert 'disabled-user' in shell('pm','disable-user','--user',str(user),package);disabled.append(package)
        write(case+'-resource-profile.json',dict(user=user,name=name,disabled=disabled,customEmulatorEnvironment=True))
        shell('am','start-user','-w',str(user));shell('am','switch-user','-w',str(user));assert shell('am','get-current-user')==str(user)
        args=dict(Qa='true',Run=run,Case=case,User=str(user),Version=version,Date=fixture['anchor'],Outlet=fixture['outlet'],Current=fixture['closing'],History=history['id'],Actor=fixture['actors']['rani']['id'],Username=fixture['actors']['rani']['username'],Password=fixture['actors']['rani']['password'],Cappuccino=ids['cappuccino'],Latte=ids['latte'],Coffee=ids['coffee'],Milk=ids['milk'],Cocoa=ids['cocoa'])
        assert all(re.fullmatch(r'[A-Za-z0-9!.:/_-]+',v) for v in args.values())
        remote=['am','instrument','--user',str(user),'-w','-r','-e','class','com.closingcount.app.OnlineDemoOrderingInstrumentedTest']
        for k,v in args.items():remote+=['-e','ccsOrder'+k,v]
        remote+=['com.closingcount.app.test/androidx.test.runner.AndroidJUnitRunner']
        with (out/(case+'.stdout.log')).open('wb') as log,(out/(case+'.stderr.log')).open('wb') as err:
            process=subprocess.Popen(prefix+['shell',*remote],stdout=log,stderr=err)
            try:code=process.wait(420)
            except subprocess.TimeoutExpired:stop(user);process.wait(15);raise RuntimeError('Original native invocation timed out') from None
        text=(out/(case+'.stdout.log')).read_text(errors='replace');proof=collect(user,case)
        assert code==0 and proof['run']==run and proof['case']==case and proof['version']==version and proof['user']==user and proof['uid']==guard(user)
        assert proof['legacyBefore']==proof['legacyAfter']
        if not proof.get('uiLogout'):
            raise RuntimeError('Native test did not complete login/logout; inspect retained native proof and private diagnostics')
        if case=='control':
            assert not proof['passed'] and 'Configured menu order' in text and 'FAILURES!!!' in text
            assert proof['inputOrder']==[ids['latte'],ids['cappuccino']] and proof['expectedOrder']==[ids['cappuccino'],ids['latte']]
            assert not re.search(r'Process crashed|ComposeTimeoutException|INSTRUMENTATION_FAILED',text)
        else:assert proof['passed'] and 'OK (1 test)' in text and proof['pagesVerified']==['input','review','frozenReport']
        results.append(dict(case=case,version=version,user=user,expectedAssertionFailureObserved=case=='control',normalTestPass=case=='fixed',proof=proof))
        remove(user);print('Ordering '+case+' verified',flush=True)
    final=business('business-after.json');assert final==initial
    after=snapshot();write('device-after.json',after);assert after==before
    owner_after=owner_hash();(out/'owner-after.sha256').write_text(owner_after);assert owner_before==owner_after
    passed=True
except Exception as error:
    # These dumps may contain private instrumentation arguments. Keep them out
    # of evidence/curation/source; collect before removing the owned users.
    if owned:
        diagnostics=private/('ordering-diagnostics-'+run+'.private');diagnostics.mkdir(exist_ok=True)
        for name,args in [('lastanr',['dumpsys','activity','lastanr']),('input-method',['dumpsys','input_method']),('crash',['logcat','-b','crash','-d','-t','200'])]:
            try:(diagnostics/(name+'.txt')).write_text(shell(*args),encoding='utf8')
            except Exception:pass
    write('failure.json',dict(type=type(error).__name__,message=str(error) if isinstance(error,RuntimeError) else 'Read retained proof/logs for assertion failure'))
    print('Ordering verification did not pass',flush=True)
finally:
    if owned:
        for user in list(users):
            try:remove(user)
            except Exception:errors.append('Owned user cleanup not verified')
        try:
            after=snapshot();write('device-after.json',after);assert after==before
            owner_after=owner_hash();(out/'owner-after.sha256').write_text(owner_after);assert owner_after==owner_before
        except Exception:errors.append('Owner Room/baseline not preserved')
        try:emulator('stop')
        except Exception:errors.append('Owned emulator stop not verified')
    try:runtime('stop')
    except Exception:errors.append('Matched DEMO runtime stop not verified')
    write('verification.json',dict(passed=passed and not errors,results=results,cleanupErrors=errors,businessSnapshotUnchanged=passed,physicalPhoneAccessed=False,customEmulatorEnvironment=True,completeCcs08=False))
sys.exit(0 if passed and not errors else 1)
