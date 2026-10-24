"""Create a reviewable source evidence set only after the independent oracle passes."""
from pathlib import Path
import hashlib,html,json,re,sys
base=Path('D:/Code/Documents/Career-Portfolio-2026');repo=Path('D:/Code/Worktrees/Closing-Count-Portfolio')
attempt=sys.argv[1];assert re.fullmatch(r'r[1-9]',attempt)
raw=base/'evidence'/('closing-count-demo-native-'+attempt)
oracle=json.loads((raw/'independent-oracle.json').read_text());assert oracle['passed']
verification=json.loads((raw/'verification.json').read_text());assert verification['passed'] and len(verification['phases'])==9
dest=repo/'docs/demo-native-evidence-r1';assert not dest.exists();dest.mkdir()
files=[]
def copy(source,name=None):
    original=source.read_bytes();data=original;name=name or source.name
    assert '/' not in name and '\\' not in name
    if name in ['owner-before.sha256','owner-after.sha256']:
        data=('\n'.join(line.rstrip() for line in original.decode().splitlines() if line.strip())+'\n').encode()
    (dest/name).write_bytes(data)
    files.append(dict(file=name,sha256=hashlib.sha256(data).hexdigest(),rawSha256=hashlib.sha256(original).hexdigest(),bytes=len(data)))
for name in ['verification.json','independent-oracle.json','build-verification.json','server-snapshot.json','source-inputs.json','device-baseline.json','device-after.json','owner-before.sha256','owner-after.sha256','owned-installations.json','app-debug.apk.json','app-debug-androidTest.apk.json']:
    copy(raw/name)
scenes=[
 ('rani-prime','contribution','Rani menyimpan total menu','Cappuccino 4 dan Latte 5 sudah terkonfirmasi server.'),
 ('dimas-work','merged-team','Kontribusi dua karyawan','Dimas memperbarui Latte menjadi 6; angka Cappuccino tetap 4.'),
 ('rani-offline','recreated-offline','Draft bertahan saat server offline','Angka 8 dan identitas operasi tetap ada setelah Activity dibuat ulang.'),
 ('rani-resolve','conflict-review','Perbedaan angka terlihat jelas','Server 5 dan draft 8 ditampilkan bersama pencatatnya; keputusan wajib dipilih.'),
 ('rani-resolve','submitted','Siap diperiksa supervisor','Karyawan mengajukan jumlah 8/6; penguncian menunggu supervisor.'),
 ('supervisor','supervisor-review','Review supervisor','Arif memeriksa kontribusi sebelum mengunci closing.'),
 ('supervisor','frozen-report','Laporan generasi terkunci','Snapshot 8/6 memakai resep dan atribusi saat penguncian.'),
 ('supervisor','reopened-edited','Koreksi dengan alasan','Jumlah berjalan 9/6 setelah reopen; laporan lama tetap 8/6.'),
 ('admin','catalog-published','Katalog baru, histori tetap','Admin menerbitkan revisi resep tanpa menulis ulang snapshot closing.'),
 ('after-reset','old-draft-backed-up','Draft lama tetap milik akun asal','Setelah reset server, draft 9 dapat dicadangkan melalui pemilih file Android.'),
 ('new-generation','fresh-generation','Demo baru yang bersih','Akun/outlet baru menerima baseline 3/5; draft lama tidak dikirim ke generasi baru.'),
]
for phase in [p['phase'] for p in verification['phases']]:
    copy(raw/phase/(phase+'-proof.json'))
    copy(raw/(phase+'.stdout.log'))
for phase,scene,*_ in scenes:copy(raw/phase/(phase+'-'+scene+'.png'))
for phase,name in [('supervisor','supervisor-frozen-report.json'),('supervisor','supervisor-report.xlsx'),('after-reset','after-reset-pending-backup.json')]:copy(raw/phase/name)
for name in ['Check-ClosingDemoNativeR1.py','ClosingDemoNativeFixtureR1.mjs','Verify-ClosingDemoNativeR1.py','Curate-ClosingDemoNativeR1.py','Checkpoint-ClosingDemoNativeR1.py']:
    copy(base/'automation'/name)
(dest/'curation.json').write_text(json.dumps(dict(attempt=attempt,rawEvidence=raw.as_posix(),files=files,
    roomDatabasesNotPublished=True,privateRuntimeAndDiagnosticsNotPublished=True,
    scriptsAreArchivedWindowsWorkspaceDrivers=True),indent=2)+'\n',encoding='utf8')
figures='\n'.join(f'<figure><a href="demo-native-evidence-r1/{phase}-{scene}.png"><img loading="lazy" src="demo-native-evidence-r1/{phase}-{scene}.png" alt="{html.escape(title)}"></a><figcaption><b>{html.escape(title)}</b><p>{html.escape(caption)}</p></figcaption></figure>' for phase,scene,title,caption in scenes)
page='''<!doctype html><html lang="id"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Closing Count — native DEMO review</title>
<style>*{box-sizing:border-box}body{margin:0;background:#f1f7fb;color:#142d42;font:16px/1.6 system-ui,sans-serif}main{max-width:1150px;margin:auto;padding:52px 24px}header{background:linear-gradient(125deg,#137ead,#75c4ec);color:white;padding:40px;border-radius:28px;margin-bottom:28px}h1{font-size:clamp(30px,5vw,48px);line-height:1.15;margin:12px 0}header p{max-width:750px}.label{font-size:13px;letter-spacing:.08em;text-transform:uppercase}.limits{background:white;border:1px solid #cddfea;border-radius:20px;padding:24px;margin:25px 0}section{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:24px;align-items:start}figure{margin:0;background:white;border-radius:22px;overflow:hidden;border:1px solid #dae5ed}img{display:block;width:100%;height:auto}figcaption{padding:20px}figcaption p{font-size:14px;color:#52677a;margin-bottom:0}a{color:#12668e}footer{padding-top:32px;color:#52677a}</style>
<main><header><span class="label">Closing Count · kandidat demo lokal</span><h1>Satu tim.<br>Satu angka yang jelas.</h1><p>Alur nyata dari input bersama, draft offline dan penyelesaian konflik sampai review supervisor, laporan historis dan reset demo.</p></header>
<div class="limits"><b>9 fase native lulus dan dicocokkan dengan database server.</b><p>Dua instalasi Android terpisah pada dua profil emulator, berjalan bergantian. Semua tindakan pengguna pada perjalanan ini melalui UI produksi; seed dan reset dilakukan operator melalui CLI demo. Data seluruhnya fiktif.</p><p>Temuan visual terbuka: urutan menu pada generasi baru mengikuti susunan array snapshot, belum urutan tampilan katalog. Perbaikan ini dikerjakan setelah checkpoint baseline ini.</p><p>Ini bukti lokal pada emulator. UAT HP, kondisi kamera/perangkat fisik dan deployment publik masih terpisah. CCS08 media/APK/paket lengkap serta program delapan proyek belum dinyatakan selesai.</p></div>
<section>'''+figures+'''</section><footer><a href="demo-native-evidence-r1/independent-oracle.json">Oracle independen</a> · <a href="demo-native-evidence-r1/verification.json">9 fase dan preservasi</a> · <a href="demo-native-evidence-r1/supervisor-report.xlsx">Contoh laporan Excel</a><p>Android0.1.1/code9 · Room10 · service0.2.0-dev.8. Skrip arsip memakai workspace Windows pengembang; bukan bagian paket operator portabel.</p></footer></main></html>'''
(repo/'docs/demo-native-review-r1.html').write_text(page,encoding='utf8')
print(json.dumps(dict(curated=True,files=len(files),screenshots=len(scenes),attempt=attempt)))
