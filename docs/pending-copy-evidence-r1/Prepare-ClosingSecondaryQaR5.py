"""Reduce unrelated Google startup work in ONLY the new disposable QA user.

Do not touch the owner, application permissions, network, DocumentsUI or keyboard.
Removing the named temporary user restores the original emulator user inventory.
This is a custom emulator environment, not a fix for physical-device performance.
"""
from pathlib import Path
import json,subprocess
out=Path('D:/Code/Documents/Career-Portfolio-2026/evidence/closing-count-pending-copy-r5')
prefix=['D:/Code/ToolCache/AndroidSdk/platform-tools/adb.exe','-s','emulator-5580']
def shell(*args):return subprocess.check_output(prefix+['shell',*args],timeout=30).decode().strip()
assert 'PortfolioQA_Phone_API36' in subprocess.check_output(prefix+['emu','avd','name'],timeout=10).decode().splitlines()
owned=json.loads((out/'secondary-installation.json').read_text(encoding='utf-8-sig'));user=owned['user'];assert user>0
assert shell('am','get-current-user')=='0'
assert f"UserInfo{{{user}:{owned['name']}:" in shell('pm','list','users')
packages=['com.google.android.gms','com.google.android.gsf','com.android.vending','com.google.android.googlequicksearchbox','com.google.android.apps.wellbeing','com.google.android.apps.messaging','com.google.android.tts','com.google.android.partnersetup','com.google.android.setupwizard','com.android.chrome','com.google.android.apps.photos']
records=[]
for package in packages:
    installed=shell('pm','list','packages','--user',str(user),package).splitlines()
    if 'package:'+package not in installed:continue
    before=shell('pm','list','packages','-d','--user',str(user),package).splitlines()
    result=shell('pm','disable-user','--user',str(user),package)
    assert 'disabled-user' in result
    records.append(dict(package=package,previouslyDisabled='package:'+package in before,disabledForTemporaryUserOnly=True))
proof=dict(user=user,name=owned['name'],disabled=records,ownerPackagesChanged=False,applicationPermissionsChanged=False,documentsUiAndKeyboardUnchanged=True,physicalPhoneAccessed=False,customEmulatorEnvironment=True)
(out/'secondary-resource-profile.json').write_text(json.dumps(proof,indent=2)+'\n',encoding='utf-8')
print(json.dumps(dict(customTemporaryUserPrepared=True,unrelatedPackagesDisabled=len(records),ownerChanged=False)))
