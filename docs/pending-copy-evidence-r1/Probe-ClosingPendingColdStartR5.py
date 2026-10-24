from pathlib import Path
import subprocess,json,re,time
out=Path('D:/Code/Documents/Career-Portfolio-2026/evidence/closing-count-pending-copy-r5')
adb='D:/Code/ToolCache/AndroidSdk/platform-tools/adb.exe';prefix=[adb,'-s','emulator-5580']
def shell(*args):return subprocess.check_output(prefix+['shell',*args],timeout=35).decode('utf-8','replace').strip()
assert 'PortfolioQA_Phone_API36' in subprocess.check_output(prefix+['emu','avd','name'],timeout=10).decode().splitlines()
owned=json.loads((out/'secondary-installation.json').read_text(encoding='utf-8-sig'));user=owned['user'];assert user>0
def guard():
    assert f"UserInfo{{{user}:{owned['name']}:" in shell('pm','list','users') and shell('am','get-current-user')==str(user)
    rows=shell('cmd','package','list','packages','-U','--user',str(user),'com.closingcount.app')
    matches=re.findall(r'^package:com\.closingcount\.app\s+uid:(\d+)\s*$',rows,re.M);assert len(matches)==1
    uid=int(matches[0]);assert uid//100000==user;return uid
proof=[]
for index,(font,night) in enumerate([('1.0','no'),('2.0','yes'),('1.0','no')],1):
    uid=guard();shell('settings','--user',str(user),'put','system','font_scale',font);shell('cmd','uimode','night',night)
    time.sleep(1);response=shell('am','start','--user',str(user),'-W','-n','com.closingcount.app/.MainActivity')
    (out/f'cold-start-{index}.txt').write_text(response,encoding='utf-8');assert 'Status: ok' in response
    time.sleep(4)
    processes=shell('ps','-A','-o','PID,UID,STAT,NAME')
    rows=[m for line in processes.splitlines() if (m:=re.fullmatch(r'\s*(\d+)\s+(\d+)\s+(\S+)\s+com\.closingcount\.app\s*',line)) and int(m[2])==uid and not m[3].startswith('Z')]
    assert len(rows)==1,'Ordinary app cold start did not remain alive'
    activities=shell('dumpsys','activity','activities')
    foreground=[line for line in activities.splitlines() if ('topResumedActivity' in line or 'mResumedActivity' in line) and f'u{user} ' in line and 'com.closingcount.app/' in line]
    assert foreground,'Ordinary app MainActivity was not resumed'
    (out/f'cold-start-{index}-processes.txt').write_text(processes,encoding='utf-8')
    (out/f'cold-start-{index}-foreground.txt').write_text('\n'.join(foreground),encoding='utf-8')
    proof.append(dict(index=index,user=user,uid=uid,pid=int(rows[0][1]),fontScale=float(font),night=night,resumed=True,aliveAfterFourSeconds=True))
    guard();shell('am','force-stop','--user',str(user),'com.closingcount.app')
    deadline=time.monotonic()+12
    while True:
        after=shell('ps','-A','-o','PID,UID,STAT,NAME')
        alive=[m for line in after.splitlines() if (m:=re.fullmatch(r'\s*(\d+)\s+(\d+)\s+(\S+)\s+(.+?)\s*',line)) and int(m[2])==uid and not m[3].startswith('Z')]
        if not alive:break
        assert time.monotonic()<deadline,'Owned app UID did not become quiescent'
        time.sleep(.25)
assert len({p['pid'] for p in proof})==3
(out/'ordinary-cold-starts.json').write_text(json.dumps(dict(passed=True,starts=proof,physicalPhoneAccessed=False,notAnInstrumentationTest=True),indent=2)+'\n',encoding='utf-8')
print(json.dumps(dict(ordinaryColdStartsPassed=3,configurations=['light/100%','dark/200%','light/100%'],physicalPhoneAccessed=False)))
