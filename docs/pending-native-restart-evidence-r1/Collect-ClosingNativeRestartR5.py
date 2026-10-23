"""Collect only known QA artifacts from the exact owned secondary installation."""
from pathlib import Path
import subprocess,sys,json
user=int(sys.argv[1]);mode=sys.argv[2]
assert user>0 and mode in ['stage-review','stage-draft','verify']
out=Path('D:/Code/Documents/Career-Portfolio-2026/evidence/closing-count-native-restart-r5')
owned=json.loads((out/'secondary-installation.json').read_text(encoding='utf-8-sig'));assert owned['user']==user
adb='D:/Code/ToolCache/AndroidSdk/platform-tools/adb.exe'
users=subprocess.check_output([adb,'-s','emulator-5580','shell','pm','list','users'],timeout=10).decode()
assert f"UserInfo{{{user}:{owned['name']}:" in users
names={'stage-review':['review'],'stage-draft':['held-draft'],'verify':['held-after-stop','already-recovered']}[mode]
for name in [f'native-pending-stop-{mode}.json']+[f'native-pending-stop-{mode}-{scene}.png' for scene in names]:
    data=subprocess.check_output([adb,'-s','emulator-5580','exec-out','run-as','com.closingcount.app','--user',str(user),'cat','files/'+name],timeout=10)
    assert data,'Empty isolated-user evidence response'
    if name.endswith('.png'):assert data.startswith(b'\x89PNG\r\n\x1a\n')
    else:
        proof=json.loads(data)
        assert proof['passed'] and proof['mode']==mode and proof['run']==owned['run']
        assert proof['user']==user and proof['uid']//100000==user and proof['pid']>0
        assert (proof.get('waitingForHostStop') is True)==(mode!='verify')
    (out/name).write_bytes(data)
print(json.dumps(dict(collected=True,user=user,phase=mode,files=len(names)+1)))
