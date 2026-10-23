$ErrorActionPreference='Stop'
$taskRoot='D:/Code/Documents/Career-Portfolio-2026/evidence/closing-count-pending-visual-r2'
$taskAdb='D:/Code/ToolCache/AndroidSdk/platform-tools/adb.exe'
$taskSerial='emulator-5580'
function Device([string[]]$taskArguments){
 $taskOutput=@(& $taskAdb -s $taskSerial shell @taskArguments)
 if($LASTEXITCODE -ne 0){throw 'Owned emulator command failed.'}
 return ($taskOutput -join "`n").Trim()
}
function Snapshot {
 return [ordered]@{size=(Device -taskArguments @('wm','size'));density=(Device -taskArguments @('wm','density'));font=(Device -taskArguments @('settings','get','system','font_scale'));night=(Device -taskArguments @('cmd','uimode','night'));users=(Device -taskArguments @('pm','list','users'))}
}
$taskFixture=Get-Content -LiteralPath D:/Code/ToolCache/PortfolioQA/ccs-pending-visual-r2-fixture.json -Raw | ConvertFrom-Json
if($taskFixture.origin -ne 'http://10.0.2.2:4371' -or $taskFixture.outlet -notmatch '^[a-f0-9-]{36}$' -or $taskFixture.employee -notmatch '^qah[a-z0-9]+\.rani$' -or $taskFixture.password -notmatch '^[A-Za-z0-9 !.-]{12,64}$'){throw 'Unexpected owned visual fixture.'}
& 'D:/Code/Documents/Career-Portfolio-2026/automation/QAEmulator.ps1' -Action start -Profile Phone -Renderer host -MemoryMb 1536
if($LASTEXITCODE -ne 0){throw 'Owned emulator failed to start.'}
if(@(& $taskAdb -s $taskSerial emu avd name) -notcontains 'PortfolioQA_Phone_API36'){throw 'Unexpected emulator identity.'}
$taskBefore=Snapshot
if($taskBefore.font -notmatch '^[0-9]+(\.[0-9]+)?$'){throw 'Unexpected baseline font.'}
if($taskBefore.night -match '^Night mode: (yes|no|auto)$'){$taskNight=$Matches[1]}else{throw 'Unexpected baseline night mode.'}
$taskBefore | ConvertTo-Json | Set-Content -LiteralPath "$taskRoot/device-baseline.json" -Encoding utf8
$taskResults=@();$taskPassed=$false
$taskCases=@(
 @{code='visual-light';font='1.0';night='no';date='2026-07-01'},
 @{code='visual-dark';font='1.0';night='yes';date='2026-07-02'},
 @{code='visual-light-large';font='2.0';night='no';date='2026-07-03'},
 @{code='visual-dark-large';font='2.0';night='yes';date='2026-07-04'}
)
try {
 $taskApks=@('D:/Code/Worktrees/Closing-Count-Portfolio/app/build/outputs/apk/debug/app-debug.apk','D:/Code/Worktrees/Closing-Count-Portfolio/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')
 foreach($taskApk in $taskApks){& $taskAdb -s $taskSerial install -r $taskApk;if($LASTEXITCODE -ne 0){throw 'QA installation failed.'}}
 $taskApks | ForEach-Object {Get-FileHash -LiteralPath $_ -Algorithm SHA256 | Select-Object Path,Hash} | ConvertTo-Json | Set-Content -LiteralPath "$taskRoot/apk-inputs.json" -Encoding utf8
 & $taskAdb -s $taskSerial shell "am instrument --user 0 -w -r -e class com.closingcount.app.OnlinePendingInboxManagementInstrumentedTest,com.closingcount.app.OnlinePendingTransferViewModelInstrumentedTest com.closingcount.app.test/androidx.test.runner.AndroidJUnitRunner" *> "$taskRoot/local-instrumentation.log"
 $taskLocalExit=$LASTEXITCODE;$taskLocal=Get-Content -LiteralPath "$taskRoot/local-instrumentation.log" -Raw
 if($taskLocalExit -ne 0 -or -not $taskLocal.Contains('OK (7 tests)') -or $taskLocal -match 'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=|Process crashed|AssumptionViolated|STATUS_CODE: -3'){throw 'Local UI/inbox regression failed.'}
 foreach($taskCase in $taskCases){
  Device -taskArguments @('settings','put','system','font_scale',$taskCase.font) | Out-Null
  Device -taskArguments @('cmd','uimode','night',$taskCase.night) | Out-Null
  Device -taskArguments @('am','force-stop','com.closingcount.app') | Out-Null
  $taskCode=$taskCase.code
  $taskArgs="-e ccsPendingUiQa true -e ccsPendingUiCase $taskCode -e ccsPendingUiDate $($taskCase.date) -e ccsQaOrigin $($taskFixture.origin) -e ccsQaOutlet $($taskFixture.outlet) -e ccsQaEmployee $($taskFixture.employee) -e ccsQaPassword '$($taskFixture.password)'"
  & $taskAdb -s $taskSerial shell "am instrument --user 0 -w -r -e class com.closingcount.app.OnlinePendingTransferNativeInstrumentedTest $taskArgs com.closingcount.app.test/androidx.test.runner.AndroidJUnitRunner" *> "$taskRoot/$taskCode.log"
  $taskExit=$LASTEXITCODE;$taskText=Get-Content -LiteralPath "$taskRoot/$taskCode.log" -Raw
  $taskOk=$taskExit -eq 0 -and $taskText.Contains('OK (1 test)') -and $taskText -notmatch 'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=|Process crashed|AssumptionViolated|STATUS_CODE: -3'
  $taskResults+=@{case=$taskCode;passed=$taskOk;font=$taskCase.font;night=$taskCase.night;date=$taskCase.date}
  New-Item -ItemType Directory -Path "$taskRoot/$taskCode" -Force | Out-Null
  $taskFiles=Device -taskArguments @('ls',"/sdcard/Android/data/com.closingcount.app/files/pending-ui-$taskCode*")
  foreach($taskFile in $taskFiles.Split("`n")){
   $taskFile=$taskFile.Trim()
   if($taskFile -notmatch '^/sdcard/Android/data/com\.closingcount\.app/files/pending-ui-visual-[a-z-]+(-[a-z-]+)?\.(json|png)$'){throw 'Unexpected native evidence path.'}
   & $taskAdb -s $taskSerial pull $taskFile "$taskRoot/$taskCode/"
   if($LASTEXITCODE -ne 0){throw 'Native evidence collection failed.'}
  }
  if(-not $taskOk){Get-Content -LiteralPath "$taskRoot/$taskCode.log" -Tail 60;throw 'Native visual journey failed.'}
 }
 $taskPassed=$true
} finally {
 Device -taskArguments @('settings','put','system','font_scale',$taskBefore.font) | Out-Null
 Device -taskArguments @('cmd','uimode','night',$taskNight) | Out-Null
 $taskAfter=Snapshot
 $taskAfter | ConvertTo-Json | Set-Content -LiteralPath "$taskRoot/device-after.json" -Encoding utf8
 $taskSame=($taskBefore | ConvertTo-Json -Compress) -eq ($taskAfter | ConvertTo-Json -Compress)
 [ordered]@{passed=($taskPassed -and $taskSame);cases=$taskResults;deviceSettingsAndUsersRestored=$taskSame;physicalPhoneAccessed=$false;completePt07Acceptance=$false} | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath "$taskRoot/verification.json" -Encoding utf8
 & 'D:/Code/Documents/Career-Portfolio-2026/automation/QAEmulator.ps1' -Action stop -Profile Phone
}
if(-not $taskPassed -or -not $taskSame){throw 'Visual acceptance did not pass.'}
