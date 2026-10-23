param(
 [ValidateSet('Http','Native')][string]$Mode='Http',
 [ValidatePattern('^[a-z0-9-]{1,40}$')][string]$Case='native-light',
 [string]$EvidenceDirectory='D:\Code\Documents\Career-Portfolio-2026\evidence\closing-count-report-export-r1\http'
)
$ErrorActionPreference='Stop'
$taskAdb='D:\Code\ToolCache\AndroidSdk\platform-tools\adb.exe';$taskSerial='emulator-5580'
if(@(& $taskAdb -s $taskSerial emu avd name) -notcontains 'PortfolioQA_Phone_API36'){throw 'Refusing another device.'}
$taskFixture=Get-Content -LiteralPath 'D:\Code\ToolCache\PortfolioQA\ccs-http-r1-fixture.json' -Raw | ConvertFrom-Json
if($taskFixture.origin -ne 'http://10.0.2.2:4371' -or $taskFixture.outlet -notmatch '^[a-f0-9-]{36}$' -or $taskFixture.employee -notmatch '^qah[a-z0-9]+\.rani$' -or $taskFixture.colleague -notmatch '^qah[a-z0-9]+\.dimas$' -or $taskFixture.supervisor -notmatch '^qah[a-z0-9]+\.arif$' -or $taskFixture.admin -notmatch '^qah[a-z0-9]+\.[a-z]+$' -or $taskFixture.password -notmatch '^[A-Za-z0-9 !.-]{12,64}$'){throw 'Unexpected owned fixture.'}
New-Item -ItemType Directory -Force -Path $EvidenceDirectory | Out-Null
$taskApks=@('D:\Code\Worktrees\Closing-Count-Portfolio\app\build\outputs\apk\debug\app-debug.apk','D:\Code\Worktrees\Closing-Count-Portfolio\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk')
foreach($taskApk in $taskApks){& $taskAdb -s $taskSerial install -r $taskApk;if($LASTEXITCODE -ne 0){throw 'QA installation failed.'}}
$taskApks | ForEach-Object {Get-FileHash -LiteralPath $_ -Algorithm SHA256 | Select-Object Path,Hash} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $EvidenceDirectory 'apk-inputs.json') -Encoding utf8
$taskClass=if($Mode -eq 'Http'){'com.closingcount.app.OnlineReportExportHttpInstrumentedTest'}else{'com.closingcount.app.OnlineReportExportNativeInstrumentedTest'}
$taskFlag=if($Mode -eq 'Http'){'ccsReportExportQa'}else{'ccsReportExportUiQa'}
$taskExpected=if($Mode -eq 'Http'){'OK (5 tests)'}else{'OK (1 test)'}
$taskCommand="am instrument -w -r -e class $taskClass -e $taskFlag true -e ccsExportCase $Case -e ccsQaOrigin $($taskFixture.origin) -e ccsQaOutlet $($taskFixture.outlet) -e ccsQaEmployee $($taskFixture.employee) -e ccsQaColleague $($taskFixture.colleague) -e ccsQaSupervisor $($taskFixture.supervisor) -e ccsQaAdmin $($taskFixture.admin) -e ccsQaPassword '$($taskFixture.password)' com.closingcount.app.test/androidx.test.runner.AndroidJUnitRunner"
$taskLog=Join-Path $EvidenceDirectory 'instrumentation.log'
& $taskAdb -s $taskSerial shell $taskCommand *> $taskLog
$taskExit=$LASTEXITCODE;$taskText=Get-Content -LiteralPath $taskLog -Raw
$taskPassed=$taskExit -eq 0 -and $taskText.Contains($taskExpected) -and $taskText -notmatch 'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=|Process crashed|AssumptionViolated|STATUS_CODE: -3'
Get-Content -LiteralPath $taskLog -Tail 50
if($taskPassed){
 $taskNames=if($Mode -eq 'Http'){@('report-export-http.xlsx','report-export-http-source.json','report-export-http-context.json')}else{@("report-export-$Case.json","report-export-$Case-source.json","report-export-$Case-current.json")+@('frozen-four','picker','saved','expired-access' | ForEach-Object {"report-export-$Case-$_.png"})}
 foreach($taskName in $taskNames){& $taskAdb -s $taskSerial pull "/sdcard/Android/data/com.closingcount.app/files/$taskName" (Join-Path $EvidenceDirectory $taskName);if($LASTEXITCODE -ne 0){throw 'QA artifact retrieval failed.'}}
 if($Mode -eq 'Native'){
  $taskProof=Get-Content -LiteralPath (Join-Path $EvidenceDirectory "report-export-$Case.json") -Raw | ConvertFrom-Json
  if(-not $taskProof.passed -or -not $taskProof.legacyDataUnchanged -or $taskProof.filename -notmatch '^ccs-qa-report-[a-f0-9-]+\.xlsx$'){throw 'Native export proof incomplete.'}
  $taskOutput=Join-Path $EvidenceDirectory $taskProof.filename
  & $taskAdb -s $taskSerial pull "/sdcard/Download/$($taskProof.filename)" $taskOutput
  if($LASTEXITCODE -ne 0 -or (Get-FileHash -LiteralPath $taskOutput -Algorithm SHA256).Hash.ToLowerInvariant() -ne $taskProof.sha256){throw 'Native workbook hash mismatch.'}
 }
}
[ordered]@{passed=$taskPassed;mode=$Mode;case=$Case;outlet=$taskFixture.outlet;emulator='PortfolioQA_Phone_API36';physicalPhoneAccessed=$false;publicAcceptance=$false;finishedAt=[DateTime]::UtcNow.ToString('o')} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $EvidenceDirectory 'verification.json') -Encoding utf8
if(-not $taskPassed){throw 'Report export acceptance did not pass; inspect evidence.'}
