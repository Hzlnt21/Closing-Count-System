$ErrorActionPreference='Stop'
$taskRoot='D:/Code/Documents/Career-Portfolio-2026/evidence/closing-count-pending-inbox-r1'
$taskAdb='D:/Code/ToolCache/AndroidSdk/platform-tools/adb.exe'
$taskSerial='emulator-5580'
function Device([string[]]$taskArguments){
 $taskOutput=@(& $taskAdb -s $taskSerial shell @taskArguments)
 if($LASTEXITCODE -ne 0){throw 'Owned emulator command failed.'}
 return ($taskOutput -join "`n").Trim()
}
& 'D:/Code/Documents/Career-Portfolio-2026/automation/QAEmulator.ps1' -Action start -Profile Phone -Renderer host -MemoryMb 1536
if($LASTEXITCODE -ne 0){throw 'Owned emulator failed to start.'}
if(@(& $taskAdb -s $taskSerial emu avd name) -notcontains 'PortfolioQA_Phone_API36'){throw 'Refusing another device.'}
$taskBaseline=[ordered]@{size=(Device -taskArguments @('wm','size'));density=(Device -taskArguments @('wm','density'));font=(Device -taskArguments @('settings','get','system','font_scale'));night=(Device -taskArguments @('cmd','uimode','night'))}
$taskBaseline | ConvertTo-Json | Set-Content -LiteralPath "$taskRoot/device-baseline.json" -Encoding utf8
$taskPassed=$false
try {
 $taskApks=@('D:/Code/Worktrees/Closing-Count-Portfolio/app/build/outputs/apk/debug/app-debug.apk','D:/Code/Worktrees/Closing-Count-Portfolio/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')
 foreach($taskApk in $taskApks){& $taskAdb -s $taskSerial install -r $taskApk;if($LASTEXITCODE -ne 0){throw 'QA installation failed.'}}
 $taskApks | ForEach-Object {Get-FileHash -LiteralPath $_ -Algorithm SHA256 | Select-Object Path,Hash} | ConvertTo-Json | Set-Content -LiteralPath "$taskRoot/apk-inputs.json" -Encoding utf8
 $taskClasses='com.closingcount.app.OnlinePendingBackupInstrumentedTest,com.closingcount.app.OnlinePendingCaptureInstrumentedTest,com.closingcount.app.OnlinePendingExportInstrumentedTest,com.closingcount.app.OnlineSyncInstrumentedTest,com.closingcount.app.OnlinePendingInboxInstrumentedTest'
 & $taskAdb -s $taskSerial shell am instrument -w -r -e class $taskClasses com.closingcount.app.test/androidx.test.runner.AndroidJUnitRunner *> "$taskRoot/instrumentation.log"
 $taskExit=$LASTEXITCODE;$taskText=Get-Content -LiteralPath "$taskRoot/instrumentation.log" -Raw
 $taskPassed=$taskExit -eq 0 -and $taskText.Contains('OK (45 tests)') -and $taskText -notmatch 'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=|Process crashed|AssumptionViolated|STATUS_CODE: -3'
 Get-Content -LiteralPath "$taskRoot/instrumentation.log" -Tail 55
 if($taskPassed){
  $taskRun=[Guid]::NewGuid().ToString()
  foreach($taskMode in @('stage','verify')) {
   & $taskAdb -s $taskSerial shell am instrument -w -r -e class com.closingcount.app.OnlinePendingInboxProcessInstrumentedTest -e ccsInboxMode $taskMode -e ccsInboxQaRun $taskRun com.closingcount.app.test/androidx.test.runner.AndroidJUnitRunner *> "$taskRoot/process-$taskMode.log"
   $taskProcessExit=$LASTEXITCODE;$taskProcessText=Get-Content -LiteralPath "$taskRoot/process-$taskMode.log" -Raw
   if($taskProcessExit -ne 0 -or -not $taskProcessText.Contains('OK (1 test)') -or $taskProcessText -match 'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=|Process crashed|AssumptionViolated|STATUS_CODE: -3'){$taskPassed=$false;Get-Content "$taskRoot/process-$taskMode.log" -Tail 35;throw 'Inbox process proof failed.'}
   if($taskMode -eq 'stage'){& $taskAdb -s $taskSerial shell am force-stop com.closingcount.app;if($LASTEXITCODE -ne 0){throw 'Owned app force-stop failed.'}}
  }
 }
 if($taskPassed){foreach($taskName in @('pending-inbox-process-qa.json','pending-inbox-process-proof.json')){& $taskAdb -s $taskSerial pull "/sdcard/Android/data/com.closingcount.app/files/$taskName" (Join-Path $taskRoot $taskName);if($LASTEXITCODE -ne 0){throw 'QA packet collection failed.'}}}
} finally {
 $taskRestored=[ordered]@{size=(Device -taskArguments @('wm','size'));density=(Device -taskArguments @('wm','density'));font=(Device -taskArguments @('settings','get','system','font_scale'));night=(Device -taskArguments @('cmd','uimode','night'))}
 $taskRestored | ConvertTo-Json | Set-Content -LiteralPath "$taskRoot/device-after.json" -Encoding utf8
 $taskSettingsSame=($taskBaseline | ConvertTo-Json -Compress) -eq ($taskRestored | ConvertTo-Json -Compress)
 [ordered]@{passed=($taskPassed -and $taskSettingsSame);tests=45;inboxTests=9;processTests=2;codecTests=8;captureTests=4;exportTests=9;syncRegressions=15;deviceSettingsUnchanged=$taskSettingsSame;emulator='PortfolioQA_Phone_API36';physicalPhoneAccessed=$false;realHttpAcceptance=$false;nativeBackupUiAcceptance=$false;finishedAt=[DateTime]::UtcNow.ToString('o')} | ConvertTo-Json | Set-Content -LiteralPath "$taskRoot/verification.json" -Encoding utf8
 & 'D:/Code/Documents/Career-Portfolio-2026/automation/QAEmulator.ps1' -Action stop -Profile Phone
}
if(-not $taskPassed -or -not $taskSettingsSame){throw 'Pending inbox acceptance did not pass.'}
