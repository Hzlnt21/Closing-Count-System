$ErrorActionPreference='Stop'
$taskRoot='D:/Code/Documents/Career-Portfolio-2026/evidence/closing-count-pending-ui-r1'
$taskAdb='D:/Code/ToolCache/AndroidSdk/platform-tools/adb.exe'
$taskSerial='emulator-5580'
function Device([string[]]$taskArguments){
 $taskOutput=@(& $taskAdb -s $taskSerial shell @taskArguments)
 if($LASTEXITCODE -ne 0){throw 'Owned emulator command failed.'}
 return ($taskOutput -join "`n").Trim()
}
function CheckInstrumentation([string]$taskFile,[string]$taskCommand,[int]$taskCount){
 & $taskAdb -s $taskSerial shell $taskCommand *> "$taskRoot/$taskFile"
 $taskExit=$LASTEXITCODE;$taskText=Get-Content -LiteralPath "$taskRoot/$taskFile" -Raw
 $taskExpected=if($taskCount -eq 1){'OK (1 test)'}else{"OK ($taskCount tests)"}
 if($taskExit -ne 0 -or -not $taskText.Contains($taskExpected) -or $taskText -match 'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=|Process crashed|AssumptionViolated|STATUS_CODE: -3'){
  Get-Content -LiteralPath "$taskRoot/$taskFile" -Tail 65;throw 'Intent recovery instrumentation failed.'
 }
}
$taskFixture=Get-Content -LiteralPath D:/Code/ToolCache/PortfolioQA/ccs-pending-ui-r1-fixture.json -Raw | ConvertFrom-Json
if($taskFixture.origin -ne 'http://10.0.2.2:4371' -or $taskFixture.outlet -notmatch '^[a-f0-9-]{36}$' -or $taskFixture.employee -notmatch '^qah[a-z0-9]+\.rani$' -or $taskFixture.colleague -notmatch '^qah[a-z0-9]+\.dimas$' -or $taskFixture.admin -notmatch '^qah[a-z0-9]+\.admin$' -or $taskFixture.password -notmatch '^[A-Za-z0-9 !.-]{12,64}$'){throw 'Refusing unexpected QA fixture.'}
& 'D:/Code/Documents/Career-Portfolio-2026/automation/QAEmulator.ps1' -Action start -Profile Phone -Renderer host -MemoryMb 1536
if($LASTEXITCODE -ne 0){throw 'Owned emulator failed to start.'}
if(@(& $taskAdb -s $taskSerial emu avd name) -notcontains 'PortfolioQA_Phone_API36'){throw 'Refusing another device.'}
$taskBaseline=[ordered]@{size=(Device -taskArguments @('wm','size'));density=(Device -taskArguments @('wm','density'));font=(Device -taskArguments @('settings','get','system','font_scale'));night=(Device -taskArguments @('cmd','uimode','night'));users=(Device -taskArguments @('pm','list','users'))}
$taskBaseline | ConvertTo-Json | Set-Content -LiteralPath "$taskRoot/device-baseline.json" -Encoding utf8
$taskPassed=$false;$taskUser=$null;$taskRun=[Guid]::NewGuid().ToString();$taskUserName="PortfolioQARecovery$($taskRun.Substring(0,8))"
try {
 $taskApks=@('D:/Code/Worktrees/Closing-Count-Portfolio/app/build/outputs/apk/debug/app-debug.apk','D:/Code/Worktrees/Closing-Count-Portfolio/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')
 foreach($taskApk in $taskApks){& $taskAdb -s $taskSerial install -r $taskApk;if($LASTEXITCODE -ne 0){throw 'QA installation failed.'}}
 $taskApks | ForEach-Object {Get-FileHash -LiteralPath $_ -Algorithm SHA256 | Select-Object Path,Hash} | ConvertTo-Json | Set-Content -LiteralPath "$taskRoot/apk-inputs.json" -Encoding utf8
 $taskArguments="-e ccsRecoveryQa true -e ccsHttpQa true -e ccsQaOrigin $($taskFixture.origin) -e ccsQaOutlet $($taskFixture.outlet) -e ccsQaEmployee $($taskFixture.employee) -e ccsQaColleague $($taskFixture.colleague) -e ccsQaAdmin $($taskFixture.admin) -e ccsQaPassword '$($taskFixture.password)'"
 $taskClasses='com.closingcount.app.OnlinePendingBackupInstrumentedTest,com.closingcount.app.OnlinePendingCaptureInstrumentedTest,com.closingcount.app.OnlinePendingExportInstrumentedTest,com.closingcount.app.OnlinePendingInboxInstrumentedTest,com.closingcount.app.OnlineSyncInstrumentedTest,com.closingcount.app.OnlineLifecycleInstrumentedTest,com.closingcount.app.OnlineHttpInstrumentedTest,com.closingcount.app.OnlineQuantityRecoveryHttpInstrumentedTest'
 CheckInstrumentation -taskFile 'instrumentation.log' -taskCount 70 -taskCommand "am instrument --user 0 -w -r -e class $taskClasses $taskArguments com.closingcount.app.test/androidx.test.runner.AndroidJUnitRunner"
 $taskAdminArgs="$taskArguments -e ccsIntentQa true -e ccsQaAdminOutlet $($taskFixture.adminOutlet) -e ccsQaBackupAdmin $($taskFixture.backupAdmin)"
 if($taskFixture.adminOutlet -notmatch '^[a-f0-9-]{36}$' -or $taskFixture.backupAdmin -notmatch '^qah[a-z0-9]+\.backupadmin$'){throw 'Unexpected admin recovery fixture.'}
 CheckInstrumentation -taskFile 'admin-instrumentation.log' -taskCount 18 -taskCommand "am instrument --user 0 -w -r -e class com.closingcount.app.OnlineRecoveredAdminInstrumentedTest,com.closingcount.app.OnlineIntentRecoveryHttpInstrumentedTest $taskAdminArgs com.closingcount.app.test/androidx.test.runner.AndroidJUnitRunner"
 $taskInstallClass='com.closingcount.app.OnlineQuantityRecoveryInstallInstrumentedTest'
 CheckInstrumentation -taskFile 'install-stage.log' -taskCount 1 -taskCommand "am instrument --user 0 -w -r -e class $taskInstallClass $taskArguments -e ccsInstallMode stage -e ccsInstallRun $taskRun com.closingcount.app.test/androidx.test.runner.AndroidJUnitRunner"
 & $taskAdb -s $taskSerial pull /sdcard/Android/data/com.closingcount.app/files/quantity-install-pending-qa.json "$taskRoot/quantity-install-pending-qa.json"
 if($LASTEXITCODE -ne 0){throw 'Source packet collection failed.'}
 $taskPacket=[Convert]::ToBase64String([IO.File]::ReadAllBytes("$taskRoot/quantity-install-pending-qa.json"))
 if($taskPacket.Length -ge 10000 -or $taskPacket -notmatch '^[A-Za-z0-9+/=]+$'){throw 'Unexpected cross-install packet.'}
 $taskCreated=Device -taskArguments @('pm','create-user',$taskUserName)
 if($taskCreated -notmatch 'Success: created user id (\d+)'){throw 'Owned emulator secondary user creation failed.'}
 $taskUser=[int]$Matches[1];if($taskUser -le 0){throw 'Refusing primary user.'}
 $taskUsers=Device -taskArguments @('pm','list','users')
 if($taskUsers -notmatch "UserInfo\{$($taskUser):$($taskUserName):"){throw 'Secondary user ownership check failed.'}
 $taskUserData=[ordered]@{run=$taskRun;name=$taskUserName;user=$taskUser;before=$taskBaseline.users;during=$taskUsers;sameEmulator=$true;separateAppUserIds=$true;physicalPhoneAccessed=$false}
 foreach($taskPackage in @('com.closingcount.app','com.closingcount.app.test')){Device -taskArguments @('cmd','package','install-existing','--user',"$taskUser",$taskPackage) | Out-Null}
 Device -taskArguments @('am','start-user','-w',"$taskUser") | Out-Null
 $taskUserData.state=Device -taskArguments @('am','get-started-user-state',"$taskUser")
 $taskUserData | ConvertTo-Json | Set-Content -LiteralPath "$taskRoot/secondary-installation.json" -Encoding utf8
 CheckInstrumentation -taskFile 'install-receive.log' -taskCount 1 -taskCommand "am instrument --user $taskUser -w -r -e class $taskInstallClass $taskArguments -e ccsInstallMode receive -e ccsInstallRun $taskRun -e ccsInstallPacket $taskPacket com.closingcount.app.test/androidx.test.runner.AndroidJUnitRunner"
 CheckInstrumentation -taskFile 'install-source-verify.log' -taskCount 1 -taskCommand "am instrument --user 0 -w -r -e class $taskInstallClass $taskArguments -e ccsInstallMode source-verify -e ccsInstallRun $taskRun com.closingcount.app.test/androidx.test.runner.AndroidJUnitRunner"
 CheckInstrumentation -taskFile 'pending-ui-local.log' -taskCount 7 -taskCommand "am instrument --user 0 -w -r -e class com.closingcount.app.OnlinePendingInboxManagementInstrumentedTest,com.closingcount.app.OnlinePendingTransferViewModelInstrumentedTest com.closingcount.app.test/androidx.test.runner.AndroidJUnitRunner"
 $taskNativeArgs="$taskArguments -e ccsPendingUiQa true -e ccsPendingUiCase native-light"
 CheckInstrumentation -taskFile 'native-light.log' -taskCount 1 -taskCommand "am instrument --user 0 -w -r -e class com.closingcount.app.OnlinePendingTransferNativeInstrumentedTest $taskNativeArgs com.closingcount.app.test/androidx.test.runner.AndroidJUnitRunner"
 New-Item -ItemType Directory -Path "$taskRoot/native-light" -Force | Out-Null
 & $taskAdb -s $taskSerial pull /sdcard/Android/data/com.closingcount.app/files/ "$taskRoot/native-light/"
 if($LASTEXITCODE -ne 0){throw 'Native evidence collection failed.'}
 $taskPassed=$true
} finally {
 if($null -ne $taskUser){
  $taskUsers=Device -taskArguments @('pm','list','users')
  if($taskUser -le 0 -or $taskUsers -notmatch "UserInfo\{$($taskUser):$($taskUserName):"){throw 'Refusing removal of an unowned Android user.'}
  Device -taskArguments @('am','stop-user','-w','-f',"$taskUser") | Out-Null
  Device -taskArguments @('pm','remove-user',"$taskUser") | Out-Null
  for($taskWait=0;$taskWait -lt 15;$taskWait++){
   $taskRemaining=Device -taskArguments @('pm','list','users')
   if($taskRemaining -notmatch "UserInfo\{$($taskUser):"){break}
   Start-Sleep -Seconds 2
  }
  if($taskRemaining -match "UserInfo\{$($taskUser):"){throw 'Owned secondary user removal did not finish.'}
 }
 $taskRestored=[ordered]@{size=(Device -taskArguments @('wm','size'));density=(Device -taskArguments @('wm','density'));font=(Device -taskArguments @('settings','get','system','font_scale'));night=(Device -taskArguments @('cmd','uimode','night'));users=(Device -taskArguments @('pm','list','users'))}
 $taskRestored | ConvertTo-Json | Set-Content -LiteralPath "$taskRoot/device-after.json" -Encoding utf8
 $taskSame=($taskBaseline | ConvertTo-Json -Compress) -eq ($taskRestored | ConvertTo-Json -Compress)
 [ordered]@{passed=($taskPassed -and $taskSame);tests=95;nativeUiTests=1;mainQuantityAndRegressions=70;recoveredAdminStoreTests=10;intentRecoveryHttpTests=8;crossInstallationInvocations=3;deviceSettingsAndUsersUnchanged=$taskSame;emulator='PortfolioQA_Phone_API36';physicalPhoneAccessed=$false;nativePickerJourneyPassed=$taskPassed;completePt07Acceptance=$false;finishedAt=[DateTime]::UtcNow.ToString('o')} | ConvertTo-Json | Set-Content -LiteralPath "$taskRoot/verification.json" -Encoding utf8
 & 'D:/Code/Documents/Career-Portfolio-2026/automation/QAEmulator.ps1' -Action stop -Profile Phone
}
if(-not $taskPassed -or -not $taskSame){throw 'Intent recovery acceptance did not pass.'}
Get-Content "$taskRoot/verification.json"
