$ErrorActionPreference='Stop'
$taskRoot='D:/Code/Documents/Career-Portfolio-2026/evidence/closing-count-pending-ui-r1'
$taskAdb='D:/Code/ToolCache/AndroidSdk/platform-tools/adb.exe'
$taskSerial='emulator-5580'
function Device([string[]]$taskArguments){
 $taskOutput=@(& $taskAdb -s $taskSerial shell @taskArguments)
 if($LASTEXITCODE -ne 0){throw 'Owned emulator command failed.'}
 return ($taskOutput -join "`n").Trim()
}
$taskFixture=Get-Content -LiteralPath D:/Code/ToolCache/PortfolioQA/ccs-pending-ui-r1-fixture.json -Raw | ConvertFrom-Json
if($taskFixture.origin -ne 'http://10.0.2.2:4371' -or $taskFixture.outlet -notmatch '^[a-f0-9-]{36}$' -or $taskFixture.employee -notmatch '^qah[a-z0-9]+\.rani$' -or $taskFixture.password -notmatch '^[A-Za-z0-9 !.-]{12,64}$'){throw 'Unexpected owned fixture.'}
& 'D:/Code/Documents/Career-Portfolio-2026/automation/QAEmulator.ps1' -Action start -Profile Phone -Renderer host -MemoryMb 1536
if($LASTEXITCODE -ne 0){throw 'Owned emulator start failed.'}
if(@(& $taskAdb -s $taskSerial emu avd name) -notcontains 'PortfolioQA_Phone_API36'){throw 'Unexpected emulator.'}
$taskPassed=$false
try {
 & $taskAdb -s $taskSerial install -r 'D:/Code/Worktrees/Closing-Count-Portfolio/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
 if($LASTEXITCODE -ne 0){throw 'QA test installation failed.'}
 $taskArgs="-e ccsPendingUiQa true -e ccsPendingUiCase native-light-r2 -e ccsPendingUiDate 2026-07-02 -e ccsQaOrigin $($taskFixture.origin) -e ccsQaOutlet $($taskFixture.outlet) -e ccsQaEmployee $($taskFixture.employee) -e ccsQaPassword '$($taskFixture.password)'"
 & $taskAdb -s $taskSerial shell "am instrument --user 0 -w -r -e class com.closingcount.app.OnlinePendingTransferNativeInstrumentedTest $taskArgs com.closingcount.app.test/androidx.test.runner.AndroidJUnitRunner" *> "$taskRoot/native-light-r2.log"
 $taskExit=$LASTEXITCODE
 $taskText=Get-Content -LiteralPath "$taskRoot/native-light-r2.log" -Raw
 $taskPassed=$taskExit -eq 0 -and $taskText.Contains('OK (1 test)') -and $taskText -notmatch 'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=|Process crashed|AssumptionViolated|STATUS_CODE: -3'
 if(-not $taskPassed){Get-Content -LiteralPath "$taskRoot/native-light-r2.log" -Tail 65}
} finally {
 New-Item -ItemType Directory -Path "$taskRoot/native-light-r2" -Force | Out-Null
 & $taskAdb -s $taskSerial pull /sdcard/Android/data/com.closingcount.app/files/ "$taskRoot/native-light-r2/"
 $taskAfter=[ordered]@{size=(Device -taskArguments @('wm','size'));density=(Device -taskArguments @('wm','density'));font=(Device -taskArguments @('settings','get','system','font_scale'));night=(Device -taskArguments @('cmd','uimode','night'));users=(Device -taskArguments @('pm','list','users'))}
 $taskAfter | ConvertTo-Json | Set-Content -LiteralPath "$taskRoot/device-after.json" -Encoding utf8
 $taskBefore=Get-Content -LiteralPath "$taskRoot/device-baseline.json" -Raw | ConvertFrom-Json
 $taskDifferent=@('size','density','font','night','users') | Where-Object {$taskBefore.$_ -ne $taskAfter[$_]}
 [ordered]@{passed=$taskPassed;tests=95;nativeUiTests=1;crossInstallationInvocations=3;deviceSettingsAndUsersUnchanged=($taskDifferent.Count -eq 0);physicalPhoneAccessed=$false;nativePickerJourneyPassed=$taskPassed;completePt07Acceptance=$false;nativeCase='native-light-r2';nativeDate='2026-07-02'} | ConvertTo-Json | Set-Content -LiteralPath "$taskRoot/verification.json" -Encoding utf8
 & 'D:/Code/Documents/Career-Portfolio-2026/automation/QAEmulator.ps1' -Action stop -Profile Phone
}
if(-not $taskPassed){throw 'Native journey not passed.'}
