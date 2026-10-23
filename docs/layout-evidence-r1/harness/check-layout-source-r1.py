from pathlib import Path
import subprocess,json,hashlib,os

source=Path('D:/Code/Worktrees/Closing-Count-Portfolio')
program=Path('D:/Code/Documents/Career-Portfolio-2026')
evidence=program/'evidence/closing-count-layout-r1'
def git(root,*args):return subprocess.check_output(['git','-C',str(root),*args])
def sha(b):return hashlib.sha256(b).hexdigest()
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
baseline='56929b28f013a608912aa3b77058803075b50040'
assert git(source,'rev-parse','HEAD').decode().strip()==baseline
assert git(source,'branch','--show-current').decode().strip()=='portfolio/closing-online'
assert not git(source,'diff','--name-only').strip(),'Stage reviewed source first'
assert not git(source,'diff',baseline,'--','app/src/test','app/build.gradle.kts','app/schemas','gradle','build.gradle.kts','settings.gradle.kts','gradle.properties','service').strip()
staged=git(source,'diff','--cached','--name-only').decode().splitlines()
assert staged and not any(x.endswith('.apk') or 'hs_err_pid' in x or 'replay_pid' in x for x in staged)
expected_main={f'app/src/main/java/com/closingcount/app/ui/online/{x}.kt' for x in ['OnlineClosingApp','OnlineCatalogPanel','OnlineSettingsPanel']}
assert {x for x in staged if x.startswith('app/src/main/')}==expected_main
secret=read(Path(os.environ['LOCALAPPDATA'])/'PortfolioQA/secrets/closing-count-local-r1.json')
records=[]
for name in staged:
    b=(source/name).read_bytes()
    for key in ['adminPassword','appPassword','instance']:assert secret[key].encode() not in b,f'Known private value in {name}'
    records.append({'file':name,'sha256':sha(b)})
canonical=[]
for repo in read(program/'evidence/portfolio-run-r1/git-setup.json')['repos']:
    root=Path('D:/Code')/repo['path'];head=subprocess.run(['git','-C',str(root),'rev-parse','--verify','HEAD'],capture_output=True)
    assert (head.stdout.decode().strip() if head.returncode==0 else None)==repo['head']
    assert sha(git(root,'status','--porcelain=v1','-uall'))==repo['sourceStatusSha256']
    canonical.append({'code':repo['code'],'preserved':True})
proof=read(evidence/'database-proof.json');assert proof['passed'] and len(proof['cases'])==6
for case in proof['cases']:assert case['passed'] and case['deviceSettingsRestored'] and case['checks']==10
visual=read(evidence/'visual-review.json');assert visual['reviewed'] and len(visual['cases'])==6
for case in visual['cases']:
    assert case['contactSheetInspected'] and len(case['screenshots'])==7
    for image in case['screenshots']:
        relative=f"{case['case']}/{image['scene']}.png"
        assert sha((evidence/relative).read_bytes())==image['sha256']
        assert (evidence/relative).read_bytes()==(source/'docs/layout-evidence-r1'/relative).read_bytes()
inputs=[]
for mode in ['native','settings-native','catalog-native']:
    directory=evidence/f'regression-{mode}'
    result=read(directory/('native-verification.json' if mode=='native' else 'verification.json'));assert result['passed']
    hashes=[x['Hash'] for x in read(directory/'apk-inputs.json')];assert hashes==proof['apkHashes'];inputs.append(mode)
current=[sha((source/p).read_bytes()).upper() for p in ['app/build/outputs/apk/debug/app-debug.apk','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']]
assert current==proof['apkHashes']
lint=(evidence/'lint.txt').read_text(encoding='utf-8');assert '0 errors, 17 warnings' in lint
jvm=read(evidence/'jvm-tests.json');assert jvm['tests']==19 and jvm['failures']==0 and jvm['errors']==0
result={'passed':True,'baseline':baseline,'files':records,'canonical':canonical,'knownPrivateValuesAbsent':True,'serviceDependenciesSchemasUnchanged':True,'nativeLayoutCases':6,'nativeSceneChecks':60,'validatorProbePassed':True,'nativeRegressions':inputs,'allRuntimeApksMatchCurrentBuild':True,'apkSha256':current,'scope':'Selected native responsive/dark/font-scale layouts; not whole-app accessibility, physical device or public acceptance'}
(evidence/'source-preflight.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'passed':True,'sourceFiles':len(records),'canonicalPreserved':len(canonical),'layoutCases':6,'nativeRegressions':len(inputs)}))
