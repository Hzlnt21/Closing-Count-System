"""Owned-emulator diagnostics only; no application data/session reads or mutation."""
from pathlib import Path
import subprocess,json,sys
out=Path('D:/Code/Documents/Career-Portfolio-2026/evidence/closing-count-pending-copy-r5')
phase=sys.argv[1];assert phase in ['startup','settings','team','catalog','lifecycle','failure']
adb='D:/Code/ToolCache/AndroidSdk/platform-tools/adb.exe'
prefix=[adb,'-s','emulator-5580']
assert 'PortfolioQA_Phone_API36' in subprocess.check_output(prefix+['emu','avd','name'],timeout=10).decode().splitlines()
owned=json.loads((out/'secondary-installation.json').read_text(encoding='utf-8-sig'))
users=subprocess.check_output(prefix+['shell','pm','list','users'],timeout=10).decode()
assert owned['user']>0 and f"UserInfo{{{owned['user']}:{owned['name']}:" in users
target=out/'diagnostics';target.mkdir(exist_ok=True)
commands={
 'system-log':['logcat','-d','-v','threadtime','-b','all','-t','5000','ActivityManager:I','ActivityTaskManager:I','AndroidRuntime:E','lmkd:I','Zygote:I','libc:F','DEBUG:F','TestRunner:I','*:S'],
 'exit-info':['shell','dumpsys','activity','exit-info','com.closingcount.app'],
 'device-memory':['shell','cat','/proc/meminfo']}
for name,command in commands.items():
    body=subprocess.check_output(prefix+command,timeout=25)
    (target/f'{phase}-{name}.txt').write_bytes(body)
print(json.dumps(dict(diagnosticsCollected=True,phase=phase,ownedEmulator=True,applicationDataRead=False,acceptanceClaim=False)))
