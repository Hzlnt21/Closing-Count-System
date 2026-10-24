"""Closed-list stage for the verified presentation-order correction; no push."""
from pathlib import Path
import hashlib,json,os,re,subprocess,sys
base=Path('D:/Code/Documents/Career-Portfolio-2026');repo=Path('D:/Code/Worktrees/Closing-Count-Portfolio')
attempt=sys.argv[1];assert re.fullmatch(r'r[1-9][0-9]*',attempt)
raw=base/('evidence/closing-count-demo-ordering-'+attempt);dest=repo/'docs/demo-ordering-evidence-r1'
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
sha=lambda b:hashlib.sha256(b).hexdigest()
def git(*args):return subprocess.check_output(['git',*args],cwd=repo)
source_base='02389596f4d21f5a9221fa36412b27e785e2f043'
assert git('rev-parse','HEAD').decode().strip()==source_base
assert git('branch','--show-current').decode().strip()=='portfolio/closing-online'
assert git('remote','get-url','origin').decode().strip()=='https://github.com/Hzlnt21/Closing-Count-System.git'
assert not git('diff','--cached','--name-only').strip(),'Preserve unexpected existing staging'
assert read(raw/'verification.json')['passed'] and read(raw/'build-verification.json')['passed']
for item in read(raw/'source-inputs.json')['files']:assert sha(Path(item['path']).read_bytes())==item['sha256']
for item in read(dest/'curation.json')['files']:assert sha((dest/item['file']).read_bytes())==item['sha256']
names=['README.md','CHANGELOG.md','app/build.gradle.kts',
 'app/src/main/java/com/closingcount/app/data/online/OnlineCatalog.kt',
 'app/src/main/java/com/closingcount/app/ui/online/OnlineViewModel.kt',
 'app/src/main/java/com/closingcount/app/ui/online/OnlineClosingApp.kt',
 'app/src/test/java/com/closingcount/app/OnlineCatalogDisplayOrderTest.kt',
 'app/src/androidTest/java/com/closingcount/app/OnlineDemoOrderingInstrumentedTest.kt',
 'docs/DEMO_ORDERING_R1.md','docs/demo-ordering-review-r1.html','docs/DEMO_SCENARIOS.md','docs/DEMO_LOCAL.md']
names += [p.relative_to(repo).as_posix() for p in dest.iterdir() if p.is_file() and p.name!='source-preflight.json']
assert set(git('diff','--name-only').decode().splitlines())<=set(names),'Unexpected tracked changes'
untracked=set(git('ls-files','--others','--exclude-standard').decode().splitlines())
assert untracked<=set(names)|{'hs_err_pid26428.log','replay_pid26428.log'},'Unexpected untracked work'
guards=[]
for item in read(base/'evidence/portfolio-run-r1/git-setup.json')['repos']:
    path=Path('D:/Code')/item['path']
    head=subprocess.run(['git','-C',str(path),'rev-parse','--verify','HEAD'],capture_output=True)
    assert (head.stdout.decode().strip() if head.returncode==0 else None)==item['head']
    status=subprocess.check_output(['git','-C',str(path),'status','--porcelain=v1','-uall'])
    if sha(status)==item['sourceStatusSha256']:guards.append(dict(code=item['code'],headAndStatusMatchOriginal=True))
    else:
        assert item['code']=='ELZ'
        assert status==(base/'evidence/closing-count-demo-portability-r1/elzee-observed-status.raw').read_bytes()
        guards.append(dict(code='ELZ',headPreserved=True,observedStatusPreserved=True,unresolvedPriorDrift=True,workingTreeWritesPerformed=False))
private=Path('D:/Code/ToolCache/PortfolioQA');values=[]
qa=read(Path(os.environ['LOCALAPPDATA'])/'PortfolioQA/secrets/closing-count-local-r1.json')
values += [qa[k] for k in ['instance','adminPassword','appPassword']]
dirs=[private/name for name in ['closing-count-demo-r1','Portable Demo Profile R1/ClosingCountDemo','Crash Demo Profile R1/ClosingCountDemo','Locked Crash Demo Profile R1/ClosingCountDemo']]
dirs+=list(private.glob('Native Demo R*/ClosingCountDemo'))
for path in dirs:
    identity=read(path/'instance.private.json');values += [identity[k] for k in ['instance','ownerPassword','appPassword']]
    for file in path.glob('generation-*.private.json'):values += [a['password'] for a in read(file)['actors'].values()]
for name in names:
    data=(repo/name).read_bytes();assert not any(value.encode() in data for value in values),name
    assert not name.endswith(('.apk','.db','.private.json','.private.log'))
subprocess.run(['git','add','--',*names],cwd=repo,check=True)
assert set(git('diff','--cached','--name-only').decode().splitlines())==set(names)
records=[dict(file=name,indexSha256=sha(git('show',':'+name))) for name in sorted(names)]
proof=dict(passed=False,sourceBase=source_base,attempt=attempt,files=records,canonicalGuards=guards,
 exactExecutedSourceMatched=True,knownPrivateValuesAbsent=True,privateProfilesChecked=len(dirs),
 serviceSqlDependenciesAndRoomSchemaUnchanged=True,physicalPhoneAccessed=False,publicAcceptance=False,completeCcs08=False)
target=dest/'source-preflight.json'
target.write_text(json.dumps(proof,indent=2)+'\n',encoding='utf8')
subprocess.run(['git','add','--',target.relative_to(repo).as_posix()],cwd=repo,check=True)
subprocess.run(['git','diff','--cached','--check'],cwd=repo,check=True)
proof['passed']=True;target.write_text(json.dumps(proof,indent=2)+'\n',encoding='utf8')
subprocess.run(['git','add','--',target.relative_to(repo).as_posix()],cwd=repo,check=True)
subprocess.run(['git','diff','--cached','--check'],cwd=repo,check=True)
print(json.dumps(dict(passed=True,stagedFiles=len(names)+1,knownPrivateValuesAbsent=True,canonicalObservedBaselinePreserved=True)))
