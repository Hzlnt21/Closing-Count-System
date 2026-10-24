from pathlib import Path
import hashlib,json
base=Path('D:/Code/Documents/Career-Portfolio-2026');repo=Path('D:/Code/Worktrees/Closing-Count-Portfolio')
raw=base/'evidence/closing-count-demo-distribution-r1';build=base/'evidence/closing-count-demo-distribution-build-r1'
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
sha=lambda b:hashlib.sha256(b).hexdigest()
assert read(raw/'verification.json')['passed'] and read(raw/'media-verification.json')['passed']
assert read(raw/'apk-inventory.json')['passed'] and read(build/'variant-guards.json')['passed']
assert read(raw/'extracted-build-verification.json')['passed']
assert read(raw/'extracted-runtime-r2/extracted-runtime-verification.json')['passed']
for item in read(raw/'source-inputs.json')['files']:assert sha(Path(item['path']).read_bytes())==item['sha256']
dest=repo/'docs/demo-distribution-evidence-r1';assert not dest.exists();dest.mkdir()
records=[]
def copy(source,name=None):
    original=source.read_bytes();body=original;name=name or source.name
    if body.startswith(b'\xff\xfe'):body=body.decode('utf-16').encode('utf8')
    if name.endswith('.log'):body=('\n'.join(line.rstrip() for line in body.decode('utf8').splitlines())+'\n').encode('utf8')
    if name.endswith('.sha256'):body=('\n'.join(line.rstrip() for line in body.decode().splitlines() if line.strip())+'\n').encode()
    (dest/name).write_bytes(body)
    records.append(dict(file=name,sha256=sha(body),rawSha256=sha(original)))
for name in ['verification.json','source-inputs.json','business-before.json','business-after.json','device-before.json','device-after.json','owner-before.sha256','owner-after.sha256','fixed-apks.json','fixed.stdout.log','recording.json','media-verification.json','apk-inventory.json','extracted-build-verification.json']:copy(raw/name)
copy(raw/'fixed/proof.json','native-proof.json')
for name in ['build-verification.json','variant-guards.json','reject-peer.log','reject-release.log','apk-badging.txt','apk-signature.txt']:copy(build/name)
for name in ['extracted-runtime-verification.json','extracted-runtime-start.json','extracted-runtime-status.json','extracted-runtime-stop.json']:copy(raw/'extracted-runtime-r2'/name)
for name in ['Check-ClosingDemoPresentationR1.py','Curate-ClosingDemoMediaR1.py','Curate-ClosingDemoDistributionR1.py','Check-ClosingDemoExtractedRuntimeR1.py']:copy(base/'automation'/name)
(dest/'curation.json').write_text(json.dumps(dict(files=records,privateRuntimeAndDiagnosticsNotPublished=True,roomDatabasesNotPublished=True,failedFirstHealthCheckerRetainedGlobally=True),indent=2)+'\n',encoding='utf8')
print(json.dumps(dict(curated=True,files=len(records),physicalAcceptance=False,publicAcceptance=False)))
