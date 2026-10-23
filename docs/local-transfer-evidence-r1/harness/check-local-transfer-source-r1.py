from pathlib import Path
import subprocess,json,hashlib,os
root=Path('D:/Code/Worktrees/Closing-Count-Portfolio');program=Path('D:/Code/Documents/Career-Portfolio-2026');out=program/'evidence/closing-count-local-transfer-ui-r1'
def git(where,*args):return subprocess.check_output(['git','-C',str(where),*args])
def sha(b):return hashlib.sha256(b).hexdigest()
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
baseline='4ed381bdd30a0ec7cdc4a59a5478ef649e29fb7c'
assert git(root,'rev-parse','HEAD').decode().strip()==baseline
assert git(root,'branch','--show-current').decode().strip()=='portfolio/closing-online'
assert not git(root,'diff','--name-only').strip()
files=git(root,'diff','--cached','--name-only').decode().splitlines()
allowed={'app/src/main/java/com/closingcount/app/'+p for p in ['data/transfer/LocalBackupTransfer.kt','ui/ClosingCountApp.kt','ui/theme/Theme.kt','ui/transfer/DataTransferViewModel.kt']}
assert {p for p in files if p.startswith('app/src/main/')}==allowed
assert not git(root,'diff',baseline,'--','app/src/main/java/com/closingcount/app/data/local','app/src/main/java/com/closingcount/app/data/online','app/src/main/java/com/closingcount/app/data/transfer/BackupJsonCodec.kt','app/src/main/java/com/closingcount/app/ui/online','app/build.gradle.kts','app/schemas','service','gradle','build.gradle.kts','settings.gradle.kts','gradle.properties').strip()
old=git(root,'show',f'{baseline}:.gitattributes').decode('utf-8').rstrip()
assert (root/'.gitattributes').read_text(encoding='utf-8')==old+'\n\n# Preserve the exact verified bytes of native document-provider backup artifacts.\ndocs/local-transfer-evidence-r1/*/exported-backup.json -text\n'
runtime=read(out/'runtime-proof.json');assert runtime['passed'] and runtime['androidTests']==16 and runtime['nativeJourneys']==2
for index,p in enumerate(['app/build/outputs/apk/debug/app-debug.apk','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']):assert sha((root/p).read_bytes()).upper()==runtime['apkSha256'][index]
for code in ['phone-light','phone-dark-large']:
    rel=f'docs/local-transfer-evidence-r1/{code}/exported-backup.json'
    expected=read(out/code/'native-proof.json')['exportSha256']
    assert sha((root/rel).read_bytes())==expected
    assert sha(git(root,'show',':'+rel))==expected,'Staged export bytes differ from verified provider artifact'
    for scene in ['home','picker','invalid','review','restored','backup-written']:
        assert (root/'docs/local-transfer-evidence-r1'/code/(scene+'.png')).read_bytes()==(out/code/(scene+'.png')).read_bytes()
assert read(out/'visual-review.json')['reviewed']
assert read(out/'jvm-tests.json')=={'tests':19,'failures':0,'errors':0,'skipped':0}
assert '0 errors, 17 warnings' in (out/'lint.txt').read_text(encoding='utf-8')
canonical=[]
for repo in read(program/'evidence/portfolio-run-r1/git-setup.json')['repos']:
    folder=Path('D:/Code')/repo['path'];head=subprocess.run(['git','-C',str(folder),'rev-parse','--verify','HEAD'],capture_output=True)
    assert (head.stdout.decode().strip() if head.returncode==0 else None)==repo['head']
    assert sha(git(folder,'status','--porcelain=v1','-uall'))==repo['sourceStatusSha256']
    canonical.append(dict(code=repo['code'],headAndStatusUnchanged=True))
secret=read(Path(os.environ['LOCALAPPDATA'])/'PortfolioQA/secrets/closing-count-local-r1.json');records=[]
for name in files:
    assert not name.endswith('.apk') and 'hs_err_pid' not in name and 'replay_pid' not in name
    b=(root/name).read_bytes()
    for key in ['adminPassword','appPassword','instance']:assert secret[key].encode() not in b,name
    records.append(dict(file=name,sha256=sha(b)))
proof=dict(passed=True,baseline=baseline,files=records,canonical=canonical,knownPrivateValuesAbsent=True,onlineUiServiceSchemasDependenciesLegacyCodecUnchanged=True,androidTests=16,jvmTests=19,allRuntimeApksMatchCurrentBuild=True,verifiedExportIndexBytesPreserved=True,scope='Local provider/review/native workflow; online import/export and physical/cloud providers remain open')
(out/'source-preflight.json').write_text(json.dumps(proof,indent=2)+'\n',encoding='utf-8')
print(json.dumps(dict(passed=True,stagedFiles=len(records),canonicalHeadStatusUnchanged=8,androidTests=16,exactExportIndexBytes=2)))
