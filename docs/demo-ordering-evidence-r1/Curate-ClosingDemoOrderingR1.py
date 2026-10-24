"""Publish scoped ordering evidence only after real old/fixed native checks pass."""
from pathlib import Path
import hashlib,html,json,re,sys
base=Path('D:/Code/Documents/Career-Portfolio-2026')
repo=Path('D:/Code/Worktrees/Closing-Count-Portfolio')
attempt=sys.argv[1];assert re.fullmatch(r'r[1-9][0-9]*',attempt)
raw=base/('evidence/closing-count-demo-ordering-'+attempt)
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
verification=read(raw/'verification.json');assert verification['passed']
assert verification['businessSnapshotUnchanged'] and not verification['cleanupErrors']
control,fixed=verification['results']
assert control['expectedAssertionFailureObserved'] and not control['normalTestPass']
assert fixed['normalTestPass'] and fixed['proof']['pagesVerified']==['input','review','frozenReport']
build=read(raw/'build-verification.json');assert build['passed']
assert [a['sha256'] for a in read(raw/'fixed-apks.json')]==[a['sha256'] for a in build['apkInputs']]
assert read(raw/'business-before.json')==read(raw/'business-after.json')
assert read(raw/'device-before.json')==read(raw/'device-after.json')
assert (raw/'owner-before.sha256').read_bytes()==(raw/'owner-after.sha256').read_bytes()
for item in read(raw/'source-inputs.json')['files']:
    assert hashlib.sha256(Path(item['path']).read_bytes()).hexdigest()==item['sha256']
dest=repo/'docs/demo-ordering-evidence-r1';assert not dest.exists();dest.mkdir()
records=[]
def copy(source,name=None):
    body=source.read_bytes();name=name or source.name
    assert '/' not in name and '\\' not in name
    if name.endswith('.sha256'):body=('\n'.join(line.rstrip() for line in body.decode().splitlines() if line.strip())+'\n').encode()
    (dest/name).write_bytes(body)
    records.append(dict(file=name,sha256=hashlib.sha256(body).hexdigest(),rawSha256=hashlib.sha256(source.read_bytes()).hexdigest()))
for name in ['verification.json','business-before.json','business-after.json','source-inputs.json','device-before.json','device-after.json','owner-before.sha256','owner-after.sha256','control-apks.json','fixed-apks.json','control.stdout.log','fixed.stdout.log','build-verification.json']:
    copy(raw/name)
for case in ['control','fixed']:copy(raw/case/'proof.json',case+'-proof.json')
copy(raw/'control/input.png','control-input.png')
for scene in ['input','review','report-menus','report-ingredients']:copy(raw/'fixed'/(scene+'.png'),'fixed-'+scene+'.png')
for name in ['Check-ClosingDemoOrderingR1.py','Curate-ClosingDemoOrderingR1.py','Checkpoint-ClosingDemoOrderingR1.py']:copy(base/'automation'/name)
(dest/'curation.json').write_text(json.dumps(dict(attempt=attempt,files=records,privateRuntimeAndDiagnosticsNotPublished=True,roomDatabasesNotPublished=True,scriptsAreArchivedWindowsWorkspaceDrivers=True),indent=2)+'\n',encoding='utf8')
scenes=[('fixed-input','Input sesuai urutan katalog'),('fixed-review','Review dengan urutan menu dan bahan'),('fixed-report-menus','Jumlah menu pada laporan historis'),('fixed-report-ingredients','Pemakaian bahan pada laporan historis'),('control-input','Kontrol versi lama: urutan yang salah')]
figures=''.join('<figure><a href="demo-ordering-evidence-r1/'+name+'.png"><img src="demo-ordering-evidence-r1/'+name+'.png" alt="'+html.escape(title)+'"></a><figcaption>'+html.escape(title)+'</figcaption></figure>' for name,title in scenes)
page='''<!doctype html><html lang="id"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Closing Count — urutan katalog</title><style>*{box-sizing:border-box}body{margin:0;background:#f1f7fb;color:#142d42;font:16px/1.6 system-ui}main{max-width:1100px;margin:auto;padding:36px 24px}section{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:24px}figure{margin:0;border:1px solid #d1e2ed;border-radius:20px;overflow:hidden;background:white}img{display:block;width:100%;height:auto}figcaption{padding:18px}a{color:#176c98}</style><main><h1>Urutan yang konsisten, data tetap sama.</h1><p>Versi 0.1.2: input, review dan laporan mengikuti category/item sortOrder. Kontrol 0.1.1 mereproduksi urutan UUID yang salah pada fixture yang sama. Satu uji positif native lulus; kontrol negatif gagal tepat pada assertion yang diharapkan.</p><p>Jumlah 3/5 pada closing berjalan dan 6/4 pada laporan historis tetap. Snapshot bisnis PostgreSQL, owner Room dan baseline perangkat sama sebelum/sesudah. Ini emulator lokal, bukan UAT HP atau demo publik. Sembilan fase domain tetap bukti baseline 0.1.1, tidak diklaim diulang pada 0.1.2.</p><section>'''+figures+'''</section><p><a href="demo-ordering-evidence-r1/verification.json">Hasil native</a> · <a href="DEMO_ORDERING_R1.md">Cakupan dan batas</a></p></main></html>'''
(repo/'docs/demo-ordering-review-r1.html').write_text(page,encoding='utf8')
print(json.dumps(dict(curated=True,attempt=attempt,files=len(records),positiveNativeTests=1,expectedNegativeControls=1)))
