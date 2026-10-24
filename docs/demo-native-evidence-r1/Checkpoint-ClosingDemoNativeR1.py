"""Stage a closed, sanitized native-demo checkpoint after independent acceptance."""
from pathlib import Path
import hashlib,json,os,subprocess,sys,re
base=Path('D:/Code/Documents/Career-Portfolio-2026');repo=Path('D:/Code/Worktrees/Closing-Count-Portfolio')
attempt=sys.argv[1];assert re.fullmatch(r'r[1-9]',attempt)
raw=base/'evidence'/('closing-count-demo-native-'+attempt);dest=repo/'docs/demo-native-evidence-r1'
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
sha=lambda b:hashlib.sha256(b).hexdigest()
def git(*args):return subprocess.check_output(['git',*args],cwd=repo)
assert git('rev-parse','HEAD').decode().strip()=='fc8b9d33b9de26b2dd68b47090ea0ddd6ae2b542'
assert git('branch','--show-current').decode().strip()=='portfolio/closing-online'
assert git('remote','get-url','origin').decode().strip()=='https://github.com/Hzlnt21/Closing-Count-System.git'
assert read(raw/'verification.json')['passed'] and read(raw/'independent-oracle.json')['passed']
assert len(read(raw/'verification.json')['phases'])==9
existing=set(git('diff','--cached','--name-only').decode().splitlines())
if existing:
    previous=read(dest/'source-preflight.json')
    assert previous['sourceBase']=='fc8b9d3' and previous['attempt']==attempt
    expected={item['file'] for item in previous['files']}|{'docs/demo-native-evidence-r1/source-preflight.json'}
    assert existing==expected,'Unexpected existing staged work'
    for item in previous['files']:assert sha(git('show',':'+item['file']))==item['indexSha256'],'Previously staged work changed'
    # Resume only this exact prior checkpoint's stage; preserve every worktree byte.
    subprocess.run(['git','restore','--staged','--',*sorted(expected)],cwd=repo,check=True)
assert set(git('diff','--name-only').decode().splitlines())<={'README.md'},'Unexpected tracked source changes'
for item in read(raw/'source-inputs.json')['files']:
    assert sha(Path(item['path']).read_bytes())==item['sha256'],'Executed driver/source changed'
for item in read(dest/'curation.json')['files']:
    assert sha((dest/item['file']).read_bytes())==item['sha256']
guards=[]
for item in read(base/'evidence/portfolio-run-r1/git-setup.json')['repos']:
    path=Path('D:/Code')/item['path'];head=subprocess.run(['git','-C',str(path),'rev-parse','--verify','HEAD'],capture_output=True)
    assert (head.stdout.decode().strip() if head.returncode==0 else None)==item['head']
    status=subprocess.check_output(['git','-C',str(path),'status','--porcelain=v1','-uall'])
    if sha(status)==item['sourceStatusSha256']:guards.append(dict(code=item['code'],headAndStatusMatchOriginal=True))
    else:
        assert item['code']=='ELZ'
        observed=(base/'evidence/closing-count-demo-portability-r1/elzee-observed-status.raw').read_bytes()
        assert status==observed and len(status.splitlines())==17088
        guards.append(dict(code='ELZ',headPreserved=True,statusMatchesOriginal=False,
            originalStatusSha256=item['sourceStatusSha256'],observedStatusSha256=sha(status),
            observedStatusPreserved=True,unresolvedPriorDrift=True,workingTreeWritesPerformed=False))
names=['README.md','app/src/androidTest/java/com/closingcount/app/OnlineDemoJourneyInstrumentedTest.kt','docs/DEMO_NATIVE_R1.md','docs/demo-native-review-r1.html']
names += [p.relative_to(repo).as_posix() for p in dest.iterdir() if p.is_file() and p.name!='source-preflight.json']
assert all((repo/name).is_file() for name in names)
private=Path('D:/Code/ToolCache/PortfolioQA');values=[]
qa=read(Path(os.environ['LOCALAPPDATA'])/'PortfolioQA/secrets/closing-count-local-r1.json')
values += [qa[k] for k in ['instance','adminPassword','appPassword']]
dirs=[private/name for name in ['closing-count-demo-r1','Portable Demo Profile R1/ClosingCountDemo','Crash Demo Profile R1/ClosingCountDemo','Locked Crash Demo Profile R1/ClosingCountDemo']]
dirs += list(private.glob('Native Demo R*/ClosingCountDemo'))
for path in dirs:
    data=read(path/'instance.private.json');values += [data[k] for k in ['instance','ownerPassword','appPassword']]
    for file in path.glob('generation-*.private.json'):values += [a['password'] for a in read(file)['actors'].values()]
for name in names:
    data=(repo/name).read_bytes();assert not any(value.encode() in data for value in values),name
    assert not name.endswith(('.apk','.db','.private.json','.private.log'))
subprocess.run(['git','add','--',*names],cwd=repo,check=True)
assert set(git('diff','--cached','--name-only').decode().splitlines())==set(names)
records=[dict(file=name,indexSha256=sha(git('show',':'+name))) for name in sorted(names)]
proof=dict(passed=False,sourceBase='fc8b9d3',attempt=attempt,files=records,canonicalGuards=guards,
    exactExecutedDriverAndTestSourceMatched=True,privateValuesAbsent=True,privateProfilesChecked=len(dirs),
    coreApplicationServiceSqlAndDependenciesUnchanged=True,physicalPhoneAccessed=False,
    publicAcceptance=False,completeCcs08=False)
target=dest/'source-preflight.json';target.write_text(json.dumps(proof,indent=2)+'\n',encoding='utf8')
subprocess.run(['git','add','--',target.relative_to(repo).as_posix()],cwd=repo,check=True)
subprocess.run(['git','diff','--cached','--check'],cwd=repo,check=True)
proof['passed']=True;target.write_text(json.dumps(proof,indent=2)+'\n',encoding='utf8')
subprocess.run(['git','add','--',target.relative_to(repo).as_posix()],cwd=repo,check=True)
subprocess.run(['git','diff','--cached','--check'],cwd=repo,check=True)
print(json.dumps(dict(passed=True,stagedFiles=len(names)+1,canonicalObservedBaselinePreserved=True,unresolvedElzeePriorDrift=True)))
