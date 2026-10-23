"""Copy reviewed, non-secret QA evidence into the existing project repository."""
from pathlib import Path
import shutil,json
from html.parser import HTMLParser
program=Path('D:/Code/Documents/Career-Portfolio-2026')
root=program/'evidence/closing-count-report-export-r1'
repo=Path('D:/Code/Worktrees/Closing-Count-Portfolio')
dest=repo/'docs/report-export-evidence-r1'
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
assert read(root/'runtime-proof.json')['passed']
assert read(root/'database-proof.json')['passed']
assert read(root/'visual-review.json')['reviewed']
assert not dest.exists(),'Preserve existing packaged evidence; review before replacing.'
dest.mkdir()
for name in ['http-final','light-stable-final','dark-stable-final','history-http','history-native','inspection-http-release','inspection-light-release','inspection-dark-release']:
    shutil.copytree(root/name,dest/name)
for name in ['runtime-proof.json','database-proof.json','jvm-tests.json','lint.txt','visual-review.json','runtime-device-baseline.json','runtime-device-restored.json','final-build-3.log','native-contact-sheet.png','workbook-contact-sheet.png','history-contact-sheet.png']:
    shutil.copy2(root/name,dest/name)
h=dest/'harness';h.mkdir()
for name in ['check-report-export-runtime-r1.py','check-report-export-source-r1.py','verify-report-workbook-r1.py','verify-report-database-r1.mjs','serve-report-export-fixture-r1.mjs','package-report-export-evidence-r1.py','render-report-export-contact-sheets-r1.py']:
    shutil.copy2(program/'automation/closing-count'/name,h/name)
for name in ['Check-ClosingReportExportR1.ps1','Run-ClosingReportExportRuntimeR1.ps1','Check-ClosingHistoryR1.ps1']:
    shutil.copy2(program/'automation'/name,h/name)
shutil.copy2(Path('D:/Code/ToolCache/PortfolioQA/report-export-inspection-r1/inspect.mjs'),h/'inspect-workbook.mjs')
superseded=dest/'superseded';superseded.mkdir()
for name,source in [
 ('repeated-back-failure.log',root/'native-light/instrumentation.log'),
 ('external-picker-recreate-failure.log',root/'native-light-attempt-2/instrumentation.log'),
 ('status-bar-before-focus-reapply.png',root/'before-window-focus-fix/light-stable-final/report-export-light-stable-final-saved.png'),
 ('host-wrapper-error.log',root/'runtime-final.log')]:
    shutil.copy2(source,superseded/name)
readme='''# Locked report export evidence R1

25 JVM tests passed; final app/test builds and lint passed with 0 errors and 17 warnings. Nine selected Android cases passed on the same final production/instrumentation APK pair: five real HTTP/access/parser cases, two actual DocumentsUI export journeys (light 100% and dark 200% at 360x800dp), plus the existing history HTTP and native UI regressions. runtime-proof.json records exact hashes and scope. Original device settings were restored and the owned emulator stopped.

Three exact XLSX files are retained: one from the HTTP repository callback and two from native system save. Each workbook-verification.json compares every menu/ingredient data cell, actor, numeric count/date, cached SUM and identities with the selected frozen server JSON. database-proof.json independently compares stored PostgreSQL snapshots and current quantities for seven closings: frozen 4/current 6, one report and five business receipts per closing; no export-induced closing mutation. HTTP changes include one intentional outlet rename and revoke/restore membership. No service/schema/dependency change.

inspection-*-release directories are read-only artifact-tool imports, table inspection and two-sheet renders of the actual saved files; sourceSHA remains unchanged. visual-review.json records what was actually inspected. This does not claim desktop Microsoft Excel, physical storage provider or public demo acceptance. The gallery shows exact native screenshots and workbook renders.

The denied Android Save action creates a provider-owned empty placeholder; authorization failure writes no report bytes. Cancellation, Activity recreation, legacy snapshot preservation and explicit reauthentication are covered. Activity recreation is not process-kill recovery. Full local demo packaging and online pending-data/import safety remain open.

## Superseded attempts

- The first test cancelled DocumentsUI with repeated Back and accidentally left the report page. Fixed the harness to send one Back then await the app window.
- ActivityScenario.recreate waited for RESUMED while the external picker was foreground. Fixed the harness to request actual Activity.recreate on main and verify the new Activity instance after Save.
- The outer PowerShell *> redirection treated adb pull progress on stderr as a terminating error; the original wrapper also lost its ExitCode handle. Existing passed HTTP artifacts were recovered, logging was corrected and process handles retained. This was a host collection error, not an app acceptance pass.
- A white-on-light status bar remained in a saved screenshot despite the window appearance flag being correct. The final implementation reapplies system-bar style on return of window focus; final native images are reviewed separately. Earlier screenshot is retained for comparison. Capture now waits for SystemUI accessibility idle; that change alone did not resolve the earlier appearance.

Earlier full evidence is preserved under the global career program evidence directory. Superseded results are not counted as final passes. The source-preflight report is added after explicit Git staging; it verifies eight canonical checkouts, unchanged service/legacy/Room/dependencies, known private values absent and exact workbook bytes in the Git index. Raw credential fixtures, APKs, signing material and crash dumps are excluded.

## Reproduction context

harness/ preserves the owner-machine Windows scripts with their explicit D:/Code QA paths. They require the project's owned PostgreSQL QA instance, private instance marker outside the repository, existing Android SDK/AVD and the service's installed development dependencies. They do not configure operational databases or deployment. inspect-workbook.mjs additionally requires the documented trusted artifact-tool runtime through the private QA inspection directory, not repository dependencies. Do not copy a credential file into this folder.
'''
(dest/'README.md').write_text(readme,encoding='utf8')
class Links(HTMLParser):
    def handle_starttag(self,tag,attrs):
        for name,value in attrs:
            if name in ['src','href'] and value and not value.startswith(('http:','https:','#','data:')):
                assert (repo/'docs'/value).exists(),value
Links().feed((repo/'docs/report-export-review-r1.html').read_text(encoding='utf8'))
print(json.dumps({'packaged':True,'files':sum(p.is_file() for p in dest.rglob('*')),'galleryLinksExist':True}))
