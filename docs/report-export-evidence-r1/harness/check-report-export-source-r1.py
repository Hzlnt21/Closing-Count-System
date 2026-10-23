from pathlib import Path
import subprocess,json,hashlib,os
root=Path('D:/Code/Worktrees/Closing-Count-Portfolio')
program=Path('D:/Code/Documents/Career-Portfolio-2026')
out=program/'evidence/closing-count-report-export-r1'
baseline='0b58ea0da67b2eaea60acf754c5ad8461f26f6e1'
def git(where,*args):return subprocess.check_output(['git','-C',str(where),*args])
def sha(b):return hashlib.sha256(b).hexdigest()
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
assert git(root,'rev-parse','HEAD').decode().strip()==baseline
assert git(root,'branch','--show-current').decode().strip()=='portfolio/closing-online'
assert git(root,'remote','get-url','origin').decode().strip()=='https://github.com/Hzlnt21/Closing-Count-System.git'
assert not git(root,'diff','--name-only').strip()
files=git(root,'diff','--cached','--name-only').decode().splitlines()
allowed={'app/src/main/java/com/closingcount/app/'+p for p in ['MainActivity.kt','data/online/OnlineApi.kt','data/online/OnlineRepository.kt','data/online/FrozenClosingReport.kt','data/transfer/OnlineReportExcelExporter.kt','ui/online/OnlineClosingApp.kt','ui/online/OnlineReportExportViewModel.kt']}
assert {p for p in files if p.startswith('app/src/main/')}==allowed
for p in files:
 assert p in allowed or p in ['.gitattributes','README.md','docs/ONLINE_REPORT_EXPORT_R1.md','docs/report-export-review-r1.html'] or p.startswith('docs/report-export-evidence-r1/') or p in ['app/src/androidTest/java/com/closingcount/app/OnlineReportExportHttpInstrumentedTest.kt','app/src/androidTest/java/com/closingcount/app/OnlineReportExportNativeInstrumentedTest.kt','app/src/androidTest/java/com/closingcount/app/ReportExportQaFixture.kt','app/src/test/java/com/closingcount/app/OnlineReportExcelExporterTest.kt'],p
assert not git(root,'diff',baseline,'--','service','app/schemas','app/src/main/java/com/closingcount/app/data/local','app/src/main/java/com/closingcount/app/ui/ClosingCountApp.kt','app/src/main/java/com/closingcount/app/ui/theme','app/src/main/java/com/closingcount/app/ui/transfer','app/src/main/java/com/closingcount/app/data/transfer/BackupJsonCodec.kt','app/src/main/java/com/closingcount/app/data/transfer/LocalBackupTransfer.kt','app/build.gradle.kts','gradle','build.gradle.kts','settings.gradle.kts','gradle.properties').strip()
runtime=read(out/'runtime-proof.json');assert runtime['passed'] and runtime['androidTests']==9 and runtime['jvm']['tests']==25 and runtime['lintErrors']==0
for i,p in enumerate(['app/build/outputs/apk/debug/app-debug.apk','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']):assert sha((root/p).read_bytes()).upper()==runtime['apkSha256'][i]
assert read(out/'visual-review.json')['reviewed']
for code in ['http-final','light-stable-final','dark-stable-final']:
 folder=out/code
 filename='report-export-http.xlsx' if code=='http-final' else read(folder/f'report-export-{code}.json')['filename']
 b=(folder/filename).read_bytes();rel=f'docs/report-export-evidence-r1/{code}/{filename}'
 assert (root/rel).read_bytes()==b and git(root,'show',':'+rel)==b
 assert read(folder/'workbook-verification.json')['workbookSha256']==sha(b)
canonical=[]
for repo in read(program/'evidence/portfolio-run-r1/git-setup.json')['repos']:
 folder=Path('D:/Code')/repo['path'];head=subprocess.run(['git','-C',str(folder),'rev-parse','--verify','HEAD'],capture_output=True)
 assert (head.stdout.decode().strip() if head.returncode==0 else None)==repo['head']
 assert sha(git(folder,'status','--porcelain=v1','-uall'))==repo['sourceStatusSha256']
 canonical.append(dict(code=repo['code'],headAndStatusUnchanged=True))
secret=read(Path(os.environ['LOCALAPPDATA'])/'PortfolioQA/secrets/closing-count-local-r1.json')
records=[]
for name in files:
 assert not name.endswith(('.apk','.jks','.keystore')) and 'hs_err_pid' not in name and 'replay_pid' not in name
 if name=='docs/report-export-evidence-r1/source-preflight.json':continue
 b=git(root,'show',':'+name)
 for key in ['adminPassword','appPassword','instance']:assert secret[key].encode() not in b,name
 records.append(dict(file=name,indexSha256=sha(b)))
proof=dict(passed=True,baseline=baseline,files=records,canonical=canonical,knownPrivateValuesAbsent=True,
 serviceSchemasDependenciesLegacyUnchanged=True,allProductionApksMatchCurrentBuild=True,exactWorkbookIndexBytesPreserved=3,
 androidTests=9,jvmTests=25,lintErrors=0,lintWarnings=runtime['lintWarnings'],
 scope='Selected immutable online report export and native save/access states. Online import, complete local demo, physical providers and public deployment remain open.')
(out/'source-preflight.json').write_text(json.dumps(proof,indent=2)+'\n',encoding='utf8')
print(json.dumps(dict(passed=True,stagedFilesExcludingPreflight=len(records),canonicalHeadStatusUnchanged=8,exactWorkbooks=3)))
