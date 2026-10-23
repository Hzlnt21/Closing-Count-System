from pathlib import Path
import hashlib, json, re, xml.etree.ElementTree as ET

root = Path('D:/Code/Documents/Career-Portfolio-2026/evidence/closing-count-report-export-r1')
source = Path('D:/Code/Worktrees/Closing-Count-Portfolio')
def read(p): return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def log(p):
    b=p.read_bytes()
    return b.decode('utf-16' if b[:2] in [b'\xff\xfe', b'\xfe\xff'] else 'utf-8-sig')
hashes = [sha(source/p).upper() for p in ['app/build/outputs/apk/debug/app-debug.apk','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']]
groups=[]
for code, count in [('http-final',5),('light-stable-final',1),('dark-stable-final',1),('history-http',1),('history-native',1)]:
    folder=root/code
    v=read(folder/'verification.json');assert v['passed'] and not v['physicalPhoneAccessed'] and not v['publicAcceptance']
    inputs=[p['Hash'] for p in read(folder/'apk-inputs.json')]
    assert inputs==hashes
    text=log(folder/'instrumentation.log')
    assert f"OK ({count} test{'s' if count!=1 else ''})" in text
    assert not re.search(r'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=|Process crashed|AssumptionViolated|STATUS_CODE: -3',text)
    item=dict(case=code,tests=count,passed=True,productionApkSha256=inputs[0],instrumentationApkSha256=inputs[1])
    if code in ['light-stable-final','dark-stable-final']:
        assert inputs==hashes
        p=read(folder/f'report-export-{code}.json')
        assert p['passed'] and p['legacyDataUnchanged'] and p['pickerRecreatedWithoutRelaunch']
        assert p['expiredBytes']==0 and p['dark']==code.startswith('dark-')
        assert abs(p['fontScale']-(2 if code.startswith('dark-') else 1))<0.03
        assert abs(p['widthDp']-360)<8 and abs(p['heightDp']-800)<8
        assert re.fullmatch(r'ccs-qa-report-[a-f0-9-]+\.xlsx',p['filename'])
        assert sha(folder/p['filename'])==p['sha256']
        report=read(folder/f'report-export-{code}-source.json')
        current=read(folder/f'report-export-{code}-current.json')
        assert report['id']==p['reportId'] and report['generation']==1 and current['generation']==2
        assert next(e['quantity'] for e in report['closing']['entries'] if e['menuId']==p['menuId'])==4
        assert next(e['quantity'] for e in current['entries'] if e['menuId']==p['menuId'])==6
        assert read(folder/'workbook-verification.json')['passed']
        for scene in ['frozen-four','picker','saved','expired-access']:
            assert (folder/f'report-export-{code}-{scene}.png').read_bytes().startswith(b'\x89PNG\r\n\x1a\n')
        item.update(fontScale=p['fontScale'],dark=p['dark'],legacyUnchanged=True,activityRecreated=True,exportSha256=p['sha256'])
    groups.append(item)
assert read(root/'http-final/workbook-verification.json')['passed']
assert read(root/'database-proof.json')['passed']
assert read(root/'runtime-device-baseline.json')==read(root/'runtime-device-restored.json')
tests=[ET.parse(p).getroot().attrib for p in (source/'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')]
jvm={key:sum(int(t.get(key,0)) for t in tests) for key in ['tests','failures','errors','skipped']}
assert jvm==dict(tests=25,failures=0,errors=0,skipped=0)
issues=ET.parse(source/'app/build/reports/lint-results-debug.xml').getroot()
assert not any(i.get('severity') in ['Error','Fatal'] for i in issues)
warnings=sum(i.get('severity')=='Warning' for i in issues)
assert 'BUILD SUCCESSFUL' in log(root/'final-build-3.log')
result=dict(passed=True,apkSha256=hashes,groups=groups,androidTests=9,nativeJourneys=2,workbooksCompared=3,productionApkSameAcrossGroups=True,
 instrumentationDifference='All selected groups used the same final production and instrumentation APK pair.',
 jvm=jvm,lintErrors=0,lintWarnings=warnings,deviceSettingsRestored=True,
 scope='Owned emulator DocumentsUI and local real HTTP/PostgreSQL; no physical provider or public acceptance. Online import and complete demo remain open.')
(root/'runtime-proof.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
(root/'jvm-tests.json').write_text(json.dumps(jvm,indent=2)+'\n',encoding='utf8')
(root/'lint.txt').write_text(f'0 errors, {warnings} warnings\n',encoding='utf8')
print(json.dumps(dict(passed=True,androidTests=9,jvmTests=25,nativeJourneys=2,workbooksCompared=3,lintWarnings=warnings)))
