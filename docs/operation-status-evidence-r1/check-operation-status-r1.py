"""Owned PostgreSQL PT03 verification; never prints credentials."""
from pathlib import Path
import hashlib,json,os,subprocess,sys

root=Path('D:/Code/Worktrees/Closing-Count-Portfolio')
program=Path('D:/Code/Documents/Career-Portfolio-2026')
out=program/'evidence/closing-count-operation-status-r1'
baseline='7e8e214d685b3458d006b576e17c1a0882f852ad'
def sha(b):return hashlib.sha256(b).hexdigest()
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def git(where,*args):return subprocess.check_output(['git','-C',str(where),*args])
secret=read(Path(os.environ['LOCALAPPDATA'])/'PortfolioQA/secrets/closing-count-local-r1.json')
private=[secret[k].encode() for k in ['adminPassword','appPassword','instance']]
def safe(b):
 for value in private:assert value not in b,'Private value detected; output withheld'

if '--staged' not in sys.argv:
 out.mkdir(parents=True,exist_ok=True)
 env=dict(os.environ,CCS_ENVIRONMENT='QA',CCS_INSTANCE=secret['instance'],CCS_DATABASE_URL='postgresql://ccs_portfolio_app:'+secret['appPassword']+'@127.0.0.1:55436/closing_count_portfolio_qa')
 suites={
  'unit':(['unit','catalog','lifecycle-unit','operation-status-unit'],22),
  'integration':(['integration','closing','lifecycle','settings','team','operation-status'],80)
 }
 counts={}
 for name,(tests,count) in suites.items():
  r=subprocess.run(['node','--test','--test-timeout=60000','--test-concurrency=1',*[f'test/{t}.test.mjs' for t in tests]],cwd=root/'service',env=env,capture_output=True,timeout=180)
  body=r.stdout+r.stderr;safe(body);(out/f'{name}.log').write_bytes(body)
  text=body.decode('utf8');assert r.returncode==0 and f'tests {count}' in text and f'pass {count}' in text and 'fail 0' in text and 'skipped 0' in text,(name,r.returncode)
  counts[name]=count
 files=sorted(p for p in (root/'service').rglob('*') if p.is_file() and 'node_modules' not in p.parts)
 fingerprints=[dict(file=p.relative_to(root).as_posix(),sha256=sha(p.read_bytes())) for p in files]
 proof=dict(passed=True,unitTests=counts['unit'],httpPostgresTestsIncludingParents=counts['integration'],newScenarios=7,newScenariosIncludingParent=8,readOnlyTransactionsChecked=True,physicalPhoneAccessed=False,androidRerun=False,publicAcceptance=False,source=fingerprints,scope='PT03 service only; recovery inbox/client/UI/restore remain pending.')
 (out/'runtime-proof.json').write_text(json.dumps(proof,indent=2)+'\n',encoding='utf8')
 print(json.dumps({k:v for k,v in proof.items() if k!='source'}))
else:
 proof=read(out/'runtime-proof.json');assert proof['passed']
 for f in proof['source']:assert sha((root/f['file']).read_bytes())==f['sha256'],f['file']
 assert git(root,'rev-parse','HEAD').decode().strip()==baseline
 assert git(root,'branch','--show-current').decode().strip()=='portfolio/closing-online'
 assert not git(root,'diff','--name-only').strip(),'Unstaged tracked changes'
 assert not git(root,'diff',baseline,'--','app','gradle','build.gradle.kts','settings.gradle.kts','gradle.properties','service/migrations','service/src/closing-routes.mjs','service/src/settings-routes.mjs','service/src/team-routes.mjs').strip()
 old=read(root/'service/package.json');base=json.loads(git(root,'show',baseline+':service/package.json'))
 assert old['dependencies']==base['dependencies'] and old['engines']==base['engines']
 lock=read(root/'service/package-lock.json');previous=json.loads(git(root,'show',baseline+':service/package-lock.json'))
 lock['version']=previous['version'];lock['packages']['']['version']=previous['packages']['']['version'];assert lock==previous
 canonical=[]
 for repo in read(program/'evidence/portfolio-run-r1/git-setup.json')['repos']:
  folder=Path('D:/Code')/repo['path'];head=subprocess.run(['git','-C',str(folder),'rev-parse','--verify','HEAD'],capture_output=True)
  assert (head.stdout.decode().strip() if head.returncode==0 else None)==repo['head']
  assert sha(git(folder,'status','--porcelain=v1','-uall'))==repo['sourceStatusSha256'];canonical.append(dict(code=repo['code'],headAndStatusUnchanged=True))
 allowed={'README.md','docs/ONLINE_CONTRACT.md','docs/ONLINE_PENDING_TRANSFER_PLAN_R1.md','docs/PENDING_OPERATION_STATUS_R1.md','service/README.md','service/CHANGELOG.md','service/package.json','service/package-lock.json','service/src/app.mjs','service/src/operation-status-routes.mjs','service/test/operation-status-unit.test.mjs','service/test/operation-status.test.mjs'}
 records=[]
 for name in git(root,'diff','--cached','--name-only').decode().splitlines():
  assert name in allowed or name.startswith('docs/operation-status-evidence-r1/'),name
  if name=='docs/operation-status-evidence-r1/source-preflight.json':continue
  b=git(root,'show',':'+name);safe(b);records.append(dict(file=name,indexSha256=sha(b)))
 result=dict(passed=True,baseline=baseline,files=records,canonical=canonical,knownPrivateValuesAbsent=True,androidSchemaDependenciesMutationRoutesUnchanged=True,runtime=proof['scope'],unitTests=22,httpPostgresTestsIncludingParents=80)
 (out/'source-preflight.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
 print(json.dumps(dict(passed=True,stagedFilesExcludingPreflight=len(records),canonicalUnchanged=len(canonical),unitTests=22,httpPostgresTestsIncludingParents=80)))
