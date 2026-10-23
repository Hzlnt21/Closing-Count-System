$ErrorActionPreference='Stop'
$taskRoot='D:/Code/Documents/Career-Portfolio-2026/evidence/closing-count-native-restart-r5'
$taskAdb='D:/Code/ToolCache/AndroidSdk/platform-tools/adb.exe';$taskSerial='emulator-5580'
function Device([string[]]$taskArguments){
 $taskOutput=@(& $taskAdb -s $taskSerial shell @taskArguments)
 if($LASTEXITCODE -ne 0){throw 'Owned emulator command failed.'}
 return ($taskOutput -join "`n").Trim()
}
function Snapshot {
 return [ordered]@{size=(Device -taskArguments @('wm','size'));density=(Device -taskArguments @('wm','density'));font=(Device -taskArguments @('settings','get','system','font_scale'));night=(Device -taskArguments @('cmd','uimode','night'));users=(Device -taskArguments @('pm','list','users'));currentUser=(Device -taskArguments @('am','get-current-user'))}
}
function PrimaryHash { return Device -taskArguments @("run-as com.closingcount.app sh -c 'sha256sum databases/closing_count.db*'") }
function ReadLog([string]$taskPath){
 $taskLogStream=$null;$taskLogReader=$null
 try {
  # The owned adb child still holds its redirection handle; allow concurrent writes.
  $taskLogStream=[IO.File]::Open($taskPath,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::ReadWrite)
  $taskLogReader=[IO.StreamReader]::new($taskLogStream,[Text.Encoding]::UTF8,$true)
  return $taskLogReader.ReadToEnd()
 }catch [IO.IOException] {
  # A missing/newly-created or temporarily shared log is an observation miss,
  # not evidence that its original process handle has stopped.
  return ''
 }finally {
  if($null -ne $taskLogReader){$taskLogReader.Dispose()}
  elseif($null -ne $taskLogStream){$taskLogStream.Dispose()}
 }
}
function IsOwnedUser {
 if($null -eq $taskUser -or $taskUser -le 0){return $false}
 return (Device -taskArguments @('pm','list','users')) -match "UserInfo\{$($taskUser):$($taskName):"
}
function WaitForProcessGone([int]$taskPid){
 $taskUntil=[DateTime]::UtcNow.AddSeconds(12)
 do {
  if((Device -taskArguments @('ps','-A','-o','PID')) -notmatch "(?m)^\s*$taskPid\s*$"){return $true}
  Start-Sleep -Milliseconds 250
 }while([DateTime]::UtcNow -lt $taskUntil)
 return $false
}
function AssertLiveForeground($taskProof,[string]$taskMode){
 if((Device -taskArguments @('am','get-current-user')) -ne "$taskUser"){throw 'Owned secondary UI user is no longer active.'}
 $taskRows=Device -taskArguments @('ps','-A','-o','PID,UID,NAME')
 $taskProcessLine=@($taskRows.Split("`n") | Where-Object {$_ -match "^\s*$($taskProof.pid)\s+$($taskProof.uid)\s+com\.closingcount\.app\s*$"})
 if($taskProcessLine.Count -ne 1){throw 'Ready native app PID and UID are not live together.'}
 $taskActivities=Device -taskArguments @('dumpsys','activity','activities')
 $taskForegroundLine=@($taskActivities.Split("`n") | Where-Object {$_ -match "(?:topResumedActivity|mResumedActivity)[^\r\n]*\bu$taskUser\b[^\r\n]*com\.closingcount\.app/(?:\.MainActivity|com\.closingcount\.app\.MainActivity)\b"})
 if($taskForegroundLine.Count -lt 1){throw 'Ready MainActivity is not the foreground activity of the owned UI user.'}
 $taskProcessLine | Set-Content -LiteralPath "$taskRoot/$taskMode-live-process.txt" -Encoding utf8
 $taskForegroundLine | Set-Content -LiteralPath "$taskRoot/$taskMode-live-foreground.txt" -Encoding utf8
}
if(-not (Test-Path -LiteralPath $taskRoot)){New-Item -ItemType Directory -Path $taskRoot | Out-Null}
if((Test-Path -LiteralPath "$taskRoot/secondary-installation.json") -or (Test-Path -LiteralPath "$taskRoot/verification.json")){throw 'R5 evidence already contains a run; use a fresh namespace instead of overwriting it.'}
$taskFixture=Get-Content -LiteralPath D:/Code/ToolCache/PortfolioQA/ccs-native-restart-r5-fixture.json -Raw | ConvertFrom-Json
if($taskFixture.origin -ne 'http://10.0.2.2:4371' -or $taskFixture.outlet -notmatch '^[a-f0-9-]{36}$' -or $taskFixture.employee -notmatch '^qah[a-z0-9]+\.rani$' -or $taskFixture.password -notmatch '^[A-Za-z0-9 !.-]{12,64}$'){throw 'Unexpected restart fixture.'}
& 'D:/Code/Documents/Career-Portfolio-2026/automation/QAEmulator.ps1' -Action start -Profile Phone -Renderer host -MemoryMb 1536
if($LASTEXITCODE -ne 0){throw 'Owned emulator start failed.'}
if(@(& $taskAdb -s $taskSerial emu avd name) -notcontains 'PortfolioQA_Phone_API36'){throw 'Refusing another emulator.'}
$taskBefore=Snapshot
if($taskBefore.currentUser -ne '0'){throw 'Expected emulator owner baseline.'}
$taskBefore | ConvertTo-Json | Set-Content -LiteralPath "$taskRoot/device-baseline.json" -Encoding utf8
$taskUser=$null;$taskPassed=$false;$taskProofs=@();$taskStops=@();$taskProcess=$null
$taskPrimaryBefore=$null;$taskPrimarySame=$false;$taskSame=$false;$taskCleanupErrors=@()
$taskRun=[Guid]::NewGuid().ToString();$taskName="PortfolioQANativeStop$($taskRun.Substring(0,8))"
try {
 $taskApks=@('D:/Code/Worktrees/Closing-Count-Portfolio/app/build/outputs/apk/debug/app-debug.apk','D:/Code/Worktrees/Closing-Count-Portfolio/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')
 foreach($taskApk in $taskApks){& $taskAdb -s $taskSerial install -r $taskApk;if($LASTEXITCODE -ne 0){throw 'QA installation failed.'}}
 $taskApks | ForEach-Object {Get-FileHash -LiteralPath $_ -Algorithm SHA256 | Select-Object Path,Hash} | ConvertTo-Json | Set-Content -LiteralPath "$taskRoot/apk-inputs.json" -Encoding utf8
 $taskPrimaryBefore=PrimaryHash
 $taskPrimaryBefore | Set-Content -LiteralPath "$taskRoot/primary-room-before.sha256" -Encoding utf8
 $taskCreated=Device -taskArguments @('pm','create-user',$taskName)
 if($taskCreated -notmatch 'Success: created user id (\d+)'){throw 'Owned secondary user creation failed.'}
 $taskUser=[int]$Matches[1];if($taskUser -le 0){throw 'Refusing owner user.'}
 if(-not (IsOwnedUser)){throw 'Secondary user ownership check failed.'}
 [ordered]@{user=$taskUser;name=$taskName;run=$taskRun;before=$taskBefore.users;during=(Device -taskArguments @('pm','list','users'));physicalPhoneAccessed=$false} | ConvertTo-Json | Set-Content -LiteralPath "$taskRoot/secondary-installation.json" -Encoding utf8
 foreach($taskPackage in @('com.closingcount.app','com.closingcount.app.test')){Device -taskArguments @('cmd','package','install-existing','--user',"$taskUser",$taskPackage) | Out-Null}
 Device -taskArguments @('am','start-user','-w',"$taskUser") | Out-Null
 Device -taskArguments @('am','switch-user','-w',"$taskUser") | Out-Null
 if((Device -taskArguments @('am','get-current-user')) -ne "$taskUser"){throw 'Secondary UI user is not active.'}
 foreach($taskMode in @('stage-review','stage-draft','verify')){
  $taskStdout="$taskRoot/$taskMode-$taskRun.stdout.log";$taskStderr="$taskRoot/$taskMode-$taskRun.stderr.log"
  $taskArgs="-e ccsNativeStopQa true -e ccsNativeStopRun $taskRun -e ccsNativeStopMode $taskMode -e ccsNativeStopUser $taskUser -e ccsQaOrigin $($taskFixture.origin) -e ccsQaOutlet $($taskFixture.outlet) -e ccsQaEmployee $($taskFixture.employee) -e ccsQaPassword '$($taskFixture.password)'"
  $taskRemote="am instrument --user $taskUser -w -r -e class com.closingcount.app.OnlinePendingNativeRestartInstrumentedTest $taskArgs com.closingcount.app.test/androidx.test.runner.AndroidJUnitRunner"
  try {
   $taskProcess=Start-Process -FilePath $taskAdb -ArgumentList "-s $taskSerial shell `"$taskRemote`"" -WindowStyle Hidden -PassThru -RedirectStandardOutput $taskStdout -RedirectStandardError $taskStderr
   # Capture the native handle before HasExited/WaitForExit observations; Windows
   # Start-Process objects can otherwise expose a null ExitCode after termination.
   $taskNativeHandle=$taskProcess.Handle
   if($taskNativeHandle -eq [IntPtr]::Zero){throw 'Native process handle was not captured.'}
  }
  catch {throw 'Could not start the owned native instrumentation process.'}
  $taskExitCode=$null
  $taskProof=$null;$taskReadyUntil=[DateTime]::UtcNow.AddSeconds(180)
  do {
   $taskText=ReadLog $taskStdout
   $taskLines=@($taskText.Split("`n") | Where-Object {$_ -match '^INSTRUMENTATION_STATUS: ccsNativeStopProof='})
   if($taskLines.Count -gt 1){throw 'Duplicate native phase proof.'}
   if($taskLines.Count -eq 1){
    # Redirected stdout may be observed between writes; only a complete JSON proof is ready.
    try {$taskProof=$taskLines[0].Substring('INSTRUMENTATION_STATUS: ccsNativeStopProof='.Length).Trim() | ConvertFrom-Json}
    catch {$taskProof=$null}
    if($null -ne $taskProof){break}
   }
   if($taskProcess.HasExited){throw 'Native instrumentation terminated before its phase proof.'}
   if($taskText -match 'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=|Process crashed|AssumptionViolated|STATUS_CODE: -3'){throw 'Native instrumentation failed before readiness.'}
   Start-Sleep -Milliseconds 250
  }while([DateTime]::UtcNow -lt $taskReadyUntil)
  if($null -eq $taskProof){throw 'Native phase proof was not observed before the bounded readiness deadline.'}
  $taskReadyClock=[Diagnostics.Stopwatch]::StartNew()
  if(-not $taskProof.passed -or $taskProof.run -ne $taskRun -or $taskProof.mode -ne $taskMode -or $taskProof.user -ne $taskUser -or [Math]::Floor([double]$taskProof.uid/100000) -ne $taskUser -or $taskProof.pid -lt 1){throw 'Invalid isolated native proof.'}
  $taskProofs+=$taskProof
  if($taskMode -ne 'verify'){
   if($taskProof.waitingForHostStop -ne $true -or $taskProcess.HasExited){throw 'Native stage is not waiting for an external stop.'}
   AssertLiveForeground $taskProof $taskMode
   python 'D:/Code/Documents/Career-Portfolio-2026/automation/Collect-ClosingNativeRestartR5.py' "$taskUser" $taskMode
   if($LASTEXITCODE -ne 0){throw 'Native ready evidence collection failed.'}
   AssertLiveForeground $taskProof $taskMode
   $taskBeforeKill=ReadLog $taskStdout
   if($taskProcess.HasExited -or $taskReadyClock.Elapsed.TotalSeconds -ge 30 -or $taskBeforeKill -match '(?m)^INSTRUMENTATION_STATUS_CODE:\s*(?:0|-\d+)\s*$|OK \(1 test\)|FAILURES!!!|INSTRUMENTATION_RESULT:'){throw 'Native stage reached teardown or exceeded the safe waiting window before the host stop.'}
   if(-not (IsOwnedUser)){throw 'Owned-user identity changed before host stop.'}
   Device -taskArguments @('am','force-stop','--user',"$taskUser",'com.closingcount.app') | Out-Null
   if(-not (WaitForProcessGone ([int]$taskProof.pid))){throw 'Native stage PID survived the bounded host stop check.'}
   if(-not $taskProcess.WaitForExit(15000)){throw 'The same adb instrumentation handle did not exit after its app was stopped.'}
   $taskProcess.Refresh();$taskExitCode=$taskProcess.ExitCode
   if($null -eq $taskExitCode){throw 'Stopped native instrumentation has no observed exit code.'}
   $taskAfterKill=ReadLog $taskStdout
   if($taskAfterKill.Contains('OK (1 test)')){throw 'A killed stage completed normally; live-stop acceptance is not established.'}
   $taskStops+=@{mode=$taskMode;pid=$taskProof.pid;uid=$taskProof.uid;liveForegroundBeforeStop=$true;waitingForHostStop=$true;observedGone=$true;instrumentationExitCode=[int]$taskExitCode;expectedAbort=$true;readyToStopSeconds=$taskReadyClock.Elapsed.TotalSeconds;command='am force-stop --user OWNED_USER com.closingcount.app'}
  } else {
   if($taskProof.waitingForHostStop -eq $true){throw 'Verify phase unexpectedly waits for a host stop.'}
   if(-not $taskProcess.WaitForExit(60000)){throw 'Final native verification did not finish on its original handle.'}
   $taskProcess.Refresh();$taskExitCode=$taskProcess.ExitCode
   if($null -eq $taskExitCode){throw 'Final native instrumentation has no observed exit code.'}
   $taskText=ReadLog $taskStdout
   if($taskExitCode -ne 0 -or -not $taskText.Contains('OK (1 test)') -or $taskText -match 'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=|Process crashed|AssumptionViolated|STATUS_CODE: -3'){throw 'Final native process-restart verification failed.'}
   python 'D:/Code/Documents/Career-Portfolio-2026/automation/Collect-ClosingNativeRestartR5.py' "$taskUser" $taskMode
   if($LASTEXITCODE -ne 0){throw 'Final native evidence collection failed.'}
  }
  [ordered]@{mode=$taskMode;stdout=[IO.Path]::GetFileName($taskStdout);stderr=[IO.Path]::GetFileName($taskStderr);expectedAbort=($taskMode -ne 'verify');exitCode=[int]$taskExitCode;nativeHandleCaptured=$true;appPid=$taskProof.pid;appUid=$taskProof.uid} | ConvertTo-Json | Set-Content -LiteralPath "$taskRoot/$taskMode-process.json" -Encoding utf8
  $taskProcess.Dispose();$taskProcess=$null
 }
 if(@($taskProofs.pid | Select-Object -Unique).Count -ne 3 -or $taskStops.Count -ne 2){throw 'Expected three distinct app processes and two verified live host stops.'}
 $taskPassed=$true
} finally {
 # Independent cleanup attempts retain diagnostics without skipping the emulator stop.
 try {
  if($null -ne $taskUser){
   $taskOwned=IsOwnedUser
   if(-not $taskOwned){throw 'Refusing unowned Android user cleanup.'}
   Device -taskArguments @('am','force-stop','--user',"$taskUser",'com.closingcount.app') | Out-Null
  }
 }catch {$taskCleanupErrors+='Owned secondary app stop was not verified.'}
 try {if($null -ne $taskProcess){if(-not $taskProcess.HasExited){$taskProcess.Kill();$taskProcess.WaitForExit(5000) | Out-Null};$taskProcess.Dispose()}}
 catch {$taskCleanupErrors+='Owned adb handle cleanup was not verified.'}
 try {
  Device -taskArguments @('am','switch-user','-w','0') | Out-Null
  if((Device -taskArguments @('am','get-current-user')) -ne '0'){throw 'Owner UI not restored.'}
 }catch {$taskCleanupErrors+='Owner UI restoration was not verified.'}
 try {
  if($null -ne $taskUser){
   if(-not (IsOwnedUser)){throw 'Refusing unowned Android user removal.'}
   Device -taskArguments @('am','stop-user','-w','-f',"$taskUser") | Out-Null
   Device -taskArguments @('pm','remove-user',"$taskUser") | Out-Null
   $taskRemovalUntil=[DateTime]::UtcNow.AddSeconds(15)
   do {$taskStillPresent=(Device -taskArguments @('pm','list','users')) -match "UserInfo\{$($taskUser):";if(-not $taskStillPresent){break};Start-Sleep -Milliseconds 500}while([DateTime]::UtcNow -lt $taskRemovalUntil)
   if($taskStillPresent){throw 'Owned Android user removal was not verified.'}
  }
 }catch {$taskCleanupErrors+='Exact owned Android user removal was not verified.'}
 try {
  $taskAfter=Snapshot;$taskSame=($taskBefore | ConvertTo-Json -Compress) -eq ($taskAfter | ConvertTo-Json -Compress)
  $taskAfter | ConvertTo-Json | Set-Content -LiteralPath "$taskRoot/device-after.json" -Encoding utf8
  $taskPrimaryAfter=PrimaryHash;$taskPrimaryAfter | Set-Content -LiteralPath "$taskRoot/primary-room-after.sha256" -Encoding utf8
  $taskPrimarySame=$null -ne $taskPrimaryBefore -and $taskPrimaryBefore -eq $taskPrimaryAfter
 }catch {$taskCleanupErrors+='Device or primary Room preservation could not be verified.'}
 try {
  [ordered]@{passed=($taskPassed -and $taskSame -and $taskPrimarySame -and $taskCleanupErrors.Count -eq 0);phases=$taskProofs;stops=$taskStops;deviceSettingsUsersAndCurrentUserRestored=$taskSame;primaryRoomFilesUnchanged=$taskPrimarySame;cleanupErrors=$taskCleanupErrors;physicalPhoneAccessed=$false;completePt07Acceptance=$false} | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath "$taskRoot/verification.json" -Encoding utf8
 } finally {& 'D:/Code/Documents/Career-Portfolio-2026/automation/QAEmulator.ps1' -Action stop -Profile Phone}
}
if(-not $taskPassed -or -not $taskSame -or -not $taskPrimarySame -or $taskCleanupErrors.Count -ne 0){throw 'Native live-process restart acceptance or cleanup did not pass.'}
