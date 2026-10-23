param([ValidateSet('Http','Native')][string]$Mode='Http', [string]$EvidenceDirectory='D:\Code\Documents\Career-Portfolio-2026\evidence\closing-count-native-history-r1\http')
$ErrorActionPreference='Stop'
$taskAdb='D:\Code\ToolCache\AndroidSdk\platform-tools\adb.exe';$taskSerial='emulator-5580'
if (@(& $taskAdb -s $taskSerial emu avd name) -notcontains 'PortfolioQA_Phone_API36') {throw 'Refusing another device.'}
$taskFixture=Get-Content 'D:\Code\ToolCache\PortfolioQA\ccs-http-r1-fixture.json' -Raw | ConvertFrom-Json
if ($taskFixture.origin -ne 'http://10.0.2.2:4371' -or $taskFixture.outlet -notmatch '^[a-f0-9-]{36}$' -or $taskFixture.employee -notmatch '^qah[a-z0-9]+\.rani$' -or $taskFixture.colleague -notmatch '^qah[a-z0-9]+\.dimas$' -or $taskFixture.supervisor -notmatch '^qah[a-z0-9]+\.arif$' -or $taskFixture.password -notmatch '^[A-Za-z0-9 !.-]{12,64}$') {throw 'Unexpected owned fixture.'}
New-Item -ItemType Directory -Force -Path $EvidenceDirectory | Out-Null
$taskApks=@('D:\Code\Worktrees\Closing-Count-Portfolio\app\build\outputs\apk\debug\app-debug.apk','D:\Code\Worktrees\Closing-Count-Portfolio\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk')
foreach($taskApk in $taskApks){& $taskAdb -s $taskSerial install -r $taskApk;if($LASTEXITCODE -ne 0){throw 'QA installation failed.'}}
$taskApks | ForEach-Object {Get-FileHash -LiteralPath $_ -Algorithm SHA256 | Select-Object Path,Hash} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $EvidenceDirectory 'apk-inputs.json') -Encoding utf8
$taskClass=if($Mode -eq 'Http'){'com.closingcount.app.OnlineHistoryHttpInstrumentedTest'}else{'com.closingcount.app.OnlineHistoryNativeInstrumentedTest'}
$taskFlag=if($Mode -eq 'Http'){'ccsHistoryQa'}else{'ccsHistoryUiQa'}
$taskExpected=if($Mode -eq 'Http'){'OK (1 test)'}else{'OK (1 test)'}
$taskCommand="am instrument -w -r -e class $taskClass -e $taskFlag true -e ccsQaOrigin $($taskFixture.origin) -e ccsQaOutlet $($taskFixture.outlet) -e ccsQaEmployee $($taskFixture.employee) -e ccsQaColleague $($taskFixture.colleague) -e ccsQaSupervisor $($taskFixture.supervisor) -e ccsQaPassword '$($taskFixture.password)' com.closingcount.app.test/androidx.test.runner.AndroidJUnitRunner"
$taskLog=Join-Path $EvidenceDirectory 'instrumentation.log'
& $taskAdb -s $taskSerial shell $taskCommand *> $taskLog
$taskExit=$LASTEXITCODE;$taskText=Get-Content -LiteralPath $taskLog -Raw
$taskPassed=$taskExit -eq 0 -and $taskText.Contains($taskExpected) -and $taskText -notmatch 'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=|Process crashed|AssumptionViolated|STATUS_CODE: -3'
Get-Content -LiteralPath $taskLog -Tail 48
if($taskPassed -and $Mode -eq 'Native'){
 foreach($taskScene in @('server-list','detail','frozen-report','audit-reopen','current-six','expired-session','session-recovered')){
  & $taskAdb -s $taskSerial pull "/sdcard/Android/data/com.closingcount.app/files/native-history-$taskScene.png" (Join-Path $EvidenceDirectory "$taskScene.png")
  if($LASTEXITCODE -ne 0){throw 'QA screenshot retrieval failed.'}
 }
}
[ordered]@{passed=$taskPassed;mode=$Mode;outlet=$taskFixture.outlet;emulator='PortfolioQA_Phone_API36';actualProcessKill=$false;twoInstallations=$false;physicalPhoneAccessed=$false;publicAcceptance=$false;finishedAt=[DateTime]::UtcNow.ToString('o')} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $EvidenceDirectory 'verification.json') -Encoding utf8
if(-not $taskPassed){throw 'History acceptance did not pass; inspect evidence.'}
