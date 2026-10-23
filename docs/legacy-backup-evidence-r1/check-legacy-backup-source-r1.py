from pathlib import Path
import subprocess,json,hashlib,os
root=Path('D:/Code/Worktrees/Closing-Count-Portfolio')
program=Path('D:/Code/Documents/Career-Portfolio-2026')
out=program/'evidence/closing-count-legacy-transfer-r1'
def git(where,*args):return subprocess.check_output(['git','-C',str(where),*args])
def sha(b):return hashlib.sha256(b).hexdigest()
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
baseline='fd3e04f7eb099aca1801a9121cddaa3e5f96a2fe'
assert git(root,'rev-parse','HEAD').decode().strip()==baseline
assert git(root,'branch','--show-current').decode().strip()=='portfolio/closing-online'
assert not git(root,'diff','--name-only').strip()
changed=git(root,'diff','--cached','--name-only').decode().splitlines()
codec='app/src/main/java/com/closingcount/app/data/transfer/BackupJsonCodec.kt'
assert {p for p in changed if p.startswith('app/src/main/')}=={codec}
assert not git(root,'diff',baseline,'--','app/build.gradle.kts','app/schemas','service','gradle','build.gradle.kts','settings.gradle.kts','gradle.properties').strip()
current=(root/codec).read_text(encoding='utf-8');prior=git(root,'show',f'{baseline}:{codec}').decode('utf-8')
encoder=lambda s:s.split('    fun encode(',1)[1].split('    fun decode(',1)[0]
assert encoder(current)==encoder(prior)
assert 'const val SCHEMA_VERSION = 1' in current
verification=read(out/'verification.json');assert verification['passed'] and verification['tests']==10
hashes=read(out/'apk-inputs.json');assert len(hashes)==2
for item in hashes:assert sha(Path(item['Path']).read_bytes()).upper()==item['Hash']
assert read(out/'jvm-tests.json')=={'tests':19,'failures':0,'errors':0,'skipped':0}
assert '0 errors, 17 warnings' in (out/'lint.txt').read_text(encoding='utf-8')
canonical=[]
for repo in read(program/'evidence/portfolio-run-r1/git-setup.json')['repos']:
    folder=Path('D:/Code')/repo['path'];head=subprocess.run(['git','-C',str(folder),'rev-parse','--verify','HEAD'],capture_output=True)
    assert (head.stdout.decode().strip() if head.returncode==0 else None)==repo['head']
    assert sha(git(folder,'status','--porcelain=v1','-uall'))==repo['sourceStatusSha256']
    canonical.append({'code':repo['code'],'headAndStatusUnchanged':True})
secret=read(Path(os.environ['LOCALAPPDATA'])/'PortfolioQA/secrets/closing-count-local-r1.json')
files=[]
for name in changed:
    assert not name.endswith('.apk') and 'hs_err_pid' not in name and 'replay_pid' not in name
    b=(root/name).read_bytes()
    for key in ['adminPassword','appPassword','instance']:assert secret[key].encode() not in b
    files.append({'file':name,'sha256':sha(b)})
proof={'passed':True,'baseline':baseline,'files':files,'canonical':canonical,'knownPrivateValuesAbsent':True,'version1EncoderUnchanged':True,'uiServiceSchemasDependenciesUnchanged':True,'androidBackupTests':10,'jvmTests':19,'apkInputsMatchCurrentBuild':True,'scope':'Legacy JSON codec and isolated Room transactions; native file picker and online transfer remain open'}
(out/'source-preflight.json').write_text(json.dumps(proof,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'passed':True,'files':len(files),'canonicalHeadStatusUnchanged':len(canonical),'backupTests':10,'version1EncoderUnchanged':True}))
