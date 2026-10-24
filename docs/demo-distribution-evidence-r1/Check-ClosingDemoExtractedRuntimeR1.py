"""Start the extracted operator against the preserved owned dummy fixture only."""
from pathlib import Path
import json,subprocess,urllib.request,sys,re
base=Path('D:/Code/Documents/Career-Portfolio-2026')
operator=Path('D:/Code/ToolCache/PortfolioQA/Closing Count Reviewer R1/operator')
private=Path('D:/Code/ToolCache/PortfolioQA/Native Demo R5/ClosingCountDemo')
attempt=sys.argv[1] if len(sys.argv)>1 else 'r1';assert re.fullmatch(r'r[1-9][0-9]*',attempt)
out=base/'evidence/closing-count-demo-distribution-r1'/('extracted-runtime-'+attempt)
assert not out.exists();out.mkdir()
def run(args):
    value=subprocess.run(args,cwd=operator,capture_output=True,timeout=180)
    if value.returncode:raise RuntimeError('Extracted operator command failed; private arguments withheld')
    return value.stdout
def runtime(action):
    value=json.loads(run(['node','demo/runtime.mjs',action,'--state-dir',str(private)]))
    (out/('extracted-runtime-'+action+'.json')).write_text(json.dumps(value,indent=2)+'\n',encoding='utf8')
    return value
def snapshot(name):
    run(['node',str(base/'automation/ClosingDemoNativeFixtureR1.mjs'),'snapshot',str(private),str(out/name)])
    return json.loads((out/name).read_text(encoding='utf8'))
passed=False
try:
    started=runtime('start');assert started['running']
    before=snapshot('extracted-business-before.json')
    status=runtime('status');assert status['running']
    with urllib.request.urlopen('http://127.0.0.1:4372/api/health',timeout=10) as response:health=json.load(response)['data']
    assert health['status']=='ok' and health['environment']=='DEMO' and health['version']=='0.2.0-dev.8'
    after=snapshot('extracted-business-after.json');assert after==before
    passed=True
finally:
    stopped=runtime('stop');assert not stopped['running'] and stopped['historyAndVolumeRetained']
    proof=dict(passed=passed,extractedOperatorPath=str(operator),health=health if passed else None,businessSnapshotUnchanged=passed,stopped=True,volumeAndHistoryRetained=True,physicalPhoneAccessed=False,publicAcceptance=False)
    (out/'extracted-runtime-verification.json').write_text(json.dumps(proof,indent=2)+'\n',encoding='utf8')
print(json.dumps(proof))
