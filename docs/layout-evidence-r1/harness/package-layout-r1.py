from pathlib import Path
import json,shutil
program=Path('D:/Code/Documents/Career-Portfolio-2026')
source=Path('D:/Code/Worktrees/Closing-Count-Portfolio')
evidence=program/'evidence/closing-count-layout-r1'
target=source/'docs/layout-evidence-r1'
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
proof=read(evidence/'database-proof.json');assert proof['passed'] and len(proof['cases'])==6
def copy(relative):
    src=evidence/relative;dst=target/relative;dst.parent.mkdir(parents=True,exist_ok=True)
    if src.suffix in ['.json','.txt','.log']:
        b=src.read_bytes();text=b.decode('utf-16' if b[:2] in [b'\xff\xfe',b'\xfe\xff'] else 'utf-8-sig')
        dst.write_text('\n'.join(x.rstrip() for x in text.splitlines())+'\n',encoding='utf-8')
    else:shutil.copyfile(src,dst)
for name in ['database-proof.json','visual-review.json','matrix-run.json','validator-probe.log','validator-probe-apk-inputs.json','lint.txt','lint-triage.json','jvm-tests.json','build-final.log']:
    copy(name)
for case in proof['cases']:
    code=case['case']
    for name in ['layout.json','verification.json','apk-inputs.json','device-baseline.json','device-restored.json','instrumentation.log','fixture/fixture-identity.json','fixture/database-after.json']:
        copy(f'{code}/{name}')
    for scene in ['login','overview','input','unsaved','settings-review','team-form','catalog-form']:copy(f'{code}/{scene}.png')
for mode in ['native','settings-native','catalog-native']:
    folder=evidence/f'regression-{mode}'
    for p in folder.rglob('*'):
        if p.is_file() and p.suffix in ['.log','.json'] and 'launcher' not in p.name:copy(p.relative_to(evidence).as_posix())
for folder in ['phone-large-dark-init-harness','phone-large-dark-baseline','phone-large-dark-settings-clip','phone-landscape-light-unsaved-clip','tablet-dark-large-before-input-stack']:
    for name in ['instrumentation.log','layout.json','verification.json']:
        if (evidence/folder/name).is_file():copy(f'{folder}/{name}')
for folder,scene in [('phone-large-dark-settings-clip','settings-review'),('phone-landscape-light-unsaved-clip','unsaved'),('phone-large-dark-before-input-stack','input')]:
    if (evidence/folder/(scene+'.png')).is_file():copy(f'{folder}/{scene}.png')
harness=target/'harness';harness.mkdir(parents=True,exist_ok=True)
for name in ['Check-ClosingLayoutR1.ps1','Check-ClosingLayoutMatrixR1.ps1','Check-ClosingLayoutRegressionR1.ps1','Make-ClosingLayoutContactSheetsR1.ps1','closing-count/check-layout-database-r1.mjs','closing-count/check-layout-source-r1.py','closing-count/serve-layout-fixture-r1.mjs','closing-count/package-layout-r1.py']:
    text=(program/'automation'/name).read_text(encoding='utf-8-sig')
    (harness/Path(name).name).write_text('\n'.join(x.rstrip() for x in text.splitlines())+'\n',encoding='utf-8')
(target/'README.md').write_text('''# Layout R1 evidence

The six top-level matrix case directories are final acceptance evidence. Named before/clip/init/baseline directories contain retained failed or superseded attempts and do not count as passing final cases. See ../NATIVE_LAYOUT_R1.md and ../layout-review-r1.html.

All accepted runtime cases and three selected native regressions use the APK pair identified in database-proof.json. Build artifacts, secrets and operational data are excluded. Dummy fixture IDs are retained for independent scoped checks.

The harness directory archives the exact local host scripts. These use the owned D:/Code QA lab, restricted PostgreSQL instance, protected secret file and named emulator profiles. They are evidence/reproduction references for that lab, not a portable public demo installer. Portable demo packaging is a later checkpoint.
''',encoding='utf-8')
print(json.dumps({'packaged':True,'layoutCases':6,'galleryScreenshots':42,'target':str(target)}))
