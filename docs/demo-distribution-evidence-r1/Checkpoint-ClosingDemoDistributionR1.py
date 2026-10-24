"""Stage the verified local-demo candidate; preserve unrelated work and secrets."""
from pathlib import Path
import hashlib,json,os,subprocess
base=Path('D:/Code/Documents/Career-Portfolio-2026');repo=Path('D:/Code/Worktrees/Closing-Count-Portfolio')
raw=base/'evidence/closing-count-demo-distribution-r1';dest=repo/'docs/demo-distribution-evidence-r1'
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
sha=lambda b:hashlib.sha256(b).hexdigest()
def git(*args):return subprocess.check_output(['git',*args],cwd=repo)
source_base='83d675835ef70ac32e0db5a28ab928e6319a4678'
assert git('rev-parse','HEAD').decode().strip()==source_base
assert git('branch','--show-current').decode().strip()=='portfolio/closing-online'
assert git('remote','get-url','origin').decode().strip()=='https://github.com/Hzlnt21/Closing-Count-System.git'
assert not git('diff','--cached','--name-only').strip()
for file in ['verification.json','media-verification.json','apk-inventory.json','extracted-build-verification.json','package-preview-verification.json']:
    assert read(raw/file)['passed']
assert read(raw/'extracted-runtime-r2/extracted-runtime-verification.json')['passed']
assert read(base/'evidence/closing-count-demo-distribution-build-r1/variant-guards.json')['passed']
for item in read(raw/'source-inputs.json')['files']:assert sha(Path(item['path']).read_bytes())==item['sha256']
for item in read(dest/'curation.json')['files']:assert sha((dest/item['file']).read_bytes())==item['sha256']
for name in ['apk-info.json','media-info.json']:
    assert read(repo/'docs/demo-media-r1'/name)['passed']
assert sha((repo/'docs/demo-media-r1/presentation.mp4').read_bytes())==read(repo/'docs/demo-media-r1/media-info.json')['sha256']
names=['README.md','CHANGELOG.md','app/build.gradle.kts','app/src/main/AndroidManifest.xml',
 'app/src/androidTest/java/com/closingcount/app/OnlineDemoOrderingInstrumentedTest.kt',
 'demo/build-reviewer-package.mjs','docs/DEMO_LOCAL.md','docs/DEMO_SCENARIOS.md',
 'docs/DEMO_DISTRIBUTION_R1.md','docs/DEMO_LOCAL_ACCEPTANCE_R1.md']
names += [p.relative_to(repo).as_posix() for folder in [dest,repo/'docs/demo-media-r1'] for p in folder.iterdir() if p.is_file() and p.name!='source-preflight.json']
assert set(git('diff','--name-only').decode().splitlines())<=set(names)
assert set(git('ls-files','--others','--exclude-standard').decode().splitlines())<=set(names)|{'hs_err_pid26428.log','replay_pid26428.log','docs/demo-distribution-evidence-r1/source-preflight.json'}
guards=[]
for item in read(base/'evidence/portfolio-run-r1/git-setup.json')['repos']:
    path=Path('D:/Code')/item['path'];head=subprocess.run(['git','-C',str(path),'rev-parse','--verify','HEAD'],capture_output=True)
    assert (head.stdout.decode().strip() if head.returncode==0 else None)==item['head']
    status=subprocess.check_output(['git','-C',str(path),'status','--porcelain=v1','-uall'])
    if sha(status)==item['sourceStatusSha256']:guards.append(dict(code=item['code'],headAndStatusMatchOriginal=True))
    else:
        assert item['code']=='ELZ' and status==(base/'evidence/closing-count-demo-portability-r1/elzee-observed-status.raw').read_bytes()
        guards.append(dict(code='ELZ',headPreserved=True,observedStatusPreserved=True,unresolvedPriorDrift=True,workingTreeWritesPerformed=False))
private=Path('D:/Code/ToolCache/PortfolioQA');values=[]
qa=read(Path(os.environ['LOCALAPPDATA'])/'PortfolioQA/secrets/closing-count-local-r1.json');values += [qa[k] for k in ['instance','adminPassword','appPassword']]
dirs=[private/name for name in ['closing-count-demo-r1','Portable Demo Profile R1/ClosingCountDemo','Crash Demo Profile R1/ClosingCountDemo','Locked Crash Demo Profile R1/ClosingCountDemo']]+list(private.glob('Native Demo R*/ClosingCountDemo'))
for directory in dirs:
    identity=read(directory/'instance.private.json');values += [identity[k] for k in ['instance','ownerPassword','appPassword']]
    for file in directory.glob('generation-*.private.json'):values += [a['password'] for a in read(file)['actors'].values()]
for name in names:
    body=(repo/name).read_bytes();assert not any(v.encode() in body for v in values),name
    assert not name.endswith(('.apk','.db','.private.json','.private.log','.keystore'))
subprocess.run(['git','add','--',*names],cwd=repo,check=True)
assert set(git('diff','--cached','--name-only').decode().splitlines())==set(names)
records=[dict(file=name,indexSha256=sha(git('show',':'+name))) for name in sorted(names)]
proof=dict(passed=False,sourceBase=source_base,files=records,canonicalGuards=guards,knownPrivateValuesAbsent=True,privateProfilesChecked=len(dirs),exactExecutedAndroidSourceMatched=True,serviceSqlDependenciesRoomAndBusinessWorkflowUnchanged=True,localCandidateEvidenceMapped=True,browserPlaybackVerified=False,physicalAcceptance=False,publicAcceptance=False,fullPortfolioGoalComplete=False)
target=dest/'source-preflight.json';target.write_text(json.dumps(proof,indent=2)+'\n',encoding='utf8')
subprocess.run(['git','add','--',target.relative_to(repo).as_posix()],cwd=repo,check=True)
subprocess.run(['git','diff','--cached','--check'],cwd=repo,check=True)
proof['passed']=True;target.write_text(json.dumps(proof,indent=2)+'\n',encoding='utf8')
subprocess.run(['git','add','--',target.relative_to(repo).as_posix()],cwd=repo,check=True)
subprocess.run(['git','diff','--cached','--check'],cwd=repo,check=True)
print(json.dumps(dict(passed=True,stagedFiles=len(names)+1,knownPrivateValuesAbsent=True,canonicalObservedBaselinePreserved=True)))
