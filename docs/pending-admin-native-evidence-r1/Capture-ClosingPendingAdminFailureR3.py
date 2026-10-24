"""Best-effort raw failure screenshot on the exact guarded secondary QA user only.

This is diagnostic evidence, never a passing acceptance result. It must be visually
reviewed before any source curation. No app-private files or credentials are read.
"""
from pathlib import Path
import json,subprocess,sys

out=Path('D:/Code/Documents/Career-Portfolio-2026/evidence/closing-count-pending-admin-native-r3')
phase=sys.argv[1] if len(sys.argv)==2 and sys.argv[1] in ['settings','team','catalog','lifecycle','setup'] else 'setup'
adb='D:/Code/ToolCache/AndroidSdk/platform-tools/adb.exe'
result={'captured':False,'phase':phase,'failureEvidenceOnly':True,'acceptanceClaim':False,'physicalPhoneAccessed':False}
try:
    avd=subprocess.check_output([adb,'-s','emulator-5580','emu','avd','name'],timeout=10).decode()
    assert 'PortfolioQA_Phone_API36' in avd.splitlines()
    owned=json.loads((out/'secondary-installation.json').read_text(encoding='utf-8-sig'))
    user=owned['user'];assert type(user) is int and user>0
    users=subprocess.check_output([adb,'-s','emulator-5580','shell','pm','list','users'],timeout=10).decode()
    assert f"UserInfo{{{user}:{owned['name']}:" in users
    current=subprocess.check_output([adb,'-s','emulator-5580','shell','am','get-current-user'],timeout=10).decode().strip()
    assert current==str(user)
    data=subprocess.check_output([adb,'-s','emulator-5580','exec-out','screencap','-p'],timeout=10)
    assert data.startswith(b'\x89PNG\r\n\x1a\n')
    name=f'failure-{phase}.png';(out/name).write_bytes(data)
    result.update(captured=True,file=name,user=user,run=owned['run'],requiresVisualReviewBeforeCuration=True)
except Exception:
    result['reason']='Guarded secondary failure screenshot unavailable; acceptance and cleanup are unchanged.'
if out.is_dir():(out/f'failure-{phase}-capture.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
print(json.dumps(result))
