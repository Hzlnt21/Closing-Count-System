$ErrorActionPreference='Stop'
$taskRoot='D:/Code/Documents/Career-Portfolio-2026/evidence/closing-count-pending-admin-native-r3'
$taskAdb='D:/Code/ToolCache/AndroidSdk/platform-tools/adb.exe';$taskSerial='emulator-5580'
function Device([string[]]$taskArguments){
 $taskOutput=@(& $taskAdb -s $taskSerial shell @taskArguments)
 if($LASTEXITCODE -ne 0){throw 'Owned emulator command failed.'}
 return ($taskOutput -join "`n").Trim()
}
function Snapshot {
 return [ordered]@{size=(Device -taskArguments @('wm','size'));density=(Device -taskArguments @('wm','density'));font=(Device -taskArguments @('settings','get','system','font_scale'));night=(Device -taskArguments @('cmd','uimode','night'));users=(Device -taskArguments @('pm','list','users'));currentUser=(Device -taskArguments @('am','get-current-user'))}
}
function PrimaryHash {return Device -taskArguments @("run-as com.closingcount.app sh -c 'sha256sum databases/closing_count.db*'")}
function ReadLog([string]$taskPath){
 $taskLogStream=$null;$taskLogReader=$null
 try {
  $taskLogStream=[IO.File]::Open($taskPath,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::ReadWrite)
  $taskLogReader=[IO.StreamReader]::new($taskLogStream,[Text.Encoding]::UTF8,$true)
  return $taskLogReader.ReadToEnd()
 }catch [IO.IOException] {return ''}
 finally {if($null -ne $taskLogReader){$taskLogReader.Dispose()}elseif($null -ne $taskLogStream){$taskLogStream.Dispose()}}
}
function IsOwnedUser {
 if($null -eq $taskUser -or $taskUser -le 0){return $false}
 return (Device -taskArguments @('pm','list','users')) -match "UserInfo\{$($taskUser):$($taskName):"
}
function AssertOwnedSurface {
 if(@(& $taskAdb -s $taskSerial emu avd name) -notcontains 'PortfolioQA_Phone_API36'){throw 'Refusing another emulator.'}
 if(-not (IsOwnedUser)){throw 'Exact owned secondary user identity is not present.'}
 if((Device -taskArguments @('am','get-current-user')) -ne "$taskUser"){throw 'Owned secondary UI user is not active.'}
}
function OwnedLiveRows([string]$taskRows,[long]$taskUid){
 # A zombie has exited and cannot write the database. Require every other
 # process under this private app UID to be gone, including service/name variants.
 return @($taskRows.Split("`n") | Where-Object {
  $taskParsed=[regex]::Match($_,'^\s*(\d+)\s+(\d+)\s+(\S+)\s+(.+?)\s*$')
  $taskParsed.Success -and [long]$taskParsed.Groups[2].Value -eq $taskUid -and -not $taskParsed.Groups[3].Value.StartsWith('Z')
 })
}
function StopOwnedApp($taskProof){
 AssertOwnedSurface
 $script:taskStopCheck+=1
 $taskGuardPhase=if($null -eq $taskProof){'cleanup'}else{$taskProof.phase}
 $taskGuardBase="$taskRoot/guards/$taskGuardPhase-$($script:taskStopCheck)"
 if(-not(Test-Path -LiteralPath "$taskRoot/guards")){New-Item -ItemType Directory -Path "$taskRoot/guards" | Out-Null}
 $taskPackageRows=Device -taskArguments @('cmd','package','list','packages','-U','--user',"$taskUser",'com.closingcount.app')
 $taskRows=Device -taskArguments @('ps','-A','-o','PID,UID,STAT,NAME')
 # Retain observations before any safety rejection. Normal instrumentation may
 # reap/reuse its proof PID; the host never targets that stale PID for a kill.
 $taskPackageRows | Set-Content -LiteralPath "$taskGuardBase-package.txt" -Encoding utf8
 $taskRows | Set-Content -LiteralPath "$taskGuardBase-processes-before.txt" -Encoding utf8
 $taskPackageMatches=@($taskPackageRows.Split("`n") | Where-Object {$_ -match '^package:com\.closingcount\.app\s+uid:\d+\s*$'})
 if($taskPackageMatches.Count -ne 1){throw 'Exact owned package UID is not authoritative; observations retained.'}
 $taskUid=[long]([regex]::Match($taskPackageMatches[0],'uid:(\d+)').Groups[1].Value)
 if([Math]::Floor([double]$taskUid/100000) -ne $taskUser -or ($null -ne $taskProof -and $taskUid -ne [long]$taskProof.uid)){throw 'Installed package UID differs from the owned user/proof; observations retained.'}
 $taskPidRows=if($null -eq $taskProof){@()}else{@($taskRows.Split("`n") | Where-Object {$_ -match "^\s*$($taskProof.pid)\s+"})}
 $taskGuard=[ordered]@{guardMode='exact-owned-user-package-uid-normal-teardown';user=$taskUser;installedPackageUid=$taskUid;proofPid=if($null -eq $taskProof){$null}else{$taskProof.pid};proofPidRows=@($taskPidRows);proofPidIsDiagnosticOnly=$true;pidKillUsed=$false;liveStopClaim=$false;appUidQuiescent=$false}
 $taskGuard | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath "$taskGuardBase.json" -Encoding utf8
 Device -taskArguments @('am','force-stop','--user',"$taskUser",'com.closingcount.app') | Out-Null
 $taskUntil=[DateTime]::UtcNow.AddSeconds(12);$taskQuiet=$false
 do {
  $taskAfterRows=Device -taskArguments @('ps','-A','-o','PID,UID,STAT,NAME')
  if(@(OwnedLiveRows $taskAfterRows $taskUid).Count -eq 0){$taskQuiet=$true;break}
  Start-Sleep -Milliseconds 250
 }while([DateTime]::UtcNow -lt $taskUntil)
 $taskAfterRows | Set-Content -LiteralPath "$taskGuardBase-processes-after.txt" -Encoding utf8
 $taskGuard.appUidQuiescent=$taskQuiet
 $taskGuard | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath "$taskGuardBase.json" -Encoding utf8
 if(-not $taskQuiet){throw 'Owned app UID still has live processes after bounded scoped stop; observations retained.'}
}
if(-not (Test-Path -LiteralPath $taskRoot)){New-Item -ItemType Directory -Path $taskRoot | Out-Null}
if((Test-Path -LiteralPath "$taskRoot/secondary-installation.json") -or (Test-Path -LiteralPath "$taskRoot/verification.json")){throw 'Admin R3 evidence already contains a run; use a fresh namespace instead of overwriting it.'}
$taskFixture=Get-Content -LiteralPath D:/Code/ToolCache/PortfolioQA/ccs-pending-admin-native-r3-fixture.json -Raw | ConvertFrom-Json
if($taskFixture.origin -ne 'http://10.0.2.2:4371' -or $taskFixture.adminOutlet -notmatch '^[a-f0-9-]{36}$' -or $taskFixture.admin -notmatch '^qah[a-z0-9]+\.admin$' -or $taskFixture.password -notmatch '^[A-Za-z0-9 !.-]{12,64}$'){throw 'Unexpected owned admin fixture.'}
$taskRun=[Guid]::NewGuid().ToString();$taskName="PortfolioQAAdminNative$($taskRun.Substring(0,8))"
$taskUser=$null;$taskProcess=$null;$taskBefore=$null;$taskPrimaryBefore=$null;$taskLastProof=$null
$taskPassed=$false;$taskSame=$false;$taskPrimarySame=$false;$taskEmulatorOwned=$false;$taskUiConfigurationChanged=$false
$taskProofs=@();$taskProcesses=@();$taskCleanupErrors=@()
$script:taskStopCheck=0
$taskExpectedCounts=@{settings=1;team=3;catalog=4;lifecycle=9}
$taskExpectedPhaseCounts=@{settings=1;team=2;catalog=1;lifecycle=5}
try {
 & 'D:/Code/Documents/Career-Portfolio-2026/automation/QAEmulator.ps1' -Action start -Profile Phone -Renderer host -MemoryMb 1536
 if($LASTEXITCODE -ne 0){throw 'Owned emulator start failed.'}
 if(@(& $taskAdb -s $taskSerial emu avd name) -notcontains 'PortfolioQA_Phone_API36'){throw 'Refusing another emulator.'}
 $taskEmulatorOwned=$true;$taskBefore=Snapshot
 if($taskBefore.currentUser -ne '0'){throw 'Expected emulator owner baseline.'}
 if($taskBefore.night -notmatch '^Night mode:\s*(no|yes|auto)\s*$'){throw 'Unsupported night-mode baseline; no UI configuration will be changed.'}
 $taskBaselineNight=$Matches[1]
 if($taskBefore.font -ne 'null' -and $taskBefore.font -notmatch '^\d+(?:\.\d+)?$'){throw 'Unsupported font-scale baseline; no UI configuration will be changed.'}
 $taskBefore | ConvertTo-Json | Set-Content -LiteralPath "$taskRoot/device-baseline.json" -Encoding utf8
 # Filename-only owner preflight, before install/user/config mutations. No owner
 # database is copied; unexpected naming is reported before a QA run can begin.
 $taskOwnerNamesRaw=Device -taskArguments @('run-as','com.closingcount.app','--user','0','ls','-1','databases')
 $taskOwnerNamesRaw | Set-Content -LiteralPath "$taskRoot/owner-database-filenames-preflight.txt" -Encoding utf8
 $taskOwnerNames=@($taskOwnerNamesRaw.Split("`n") | ForEach-Object {$_.Trim()} | Where-Object {$_})
 if($taskOwnerNames -notcontains 'closing_count.db'){throw 'Expected exact owner Room database filename is missing; filenames retained before mutations.'}
 $taskApks=@('D:/Code/Worktrees/Closing-Count-Portfolio/app/build/outputs/apk/debug/app-debug.apk','D:/Code/Worktrees/Closing-Count-Portfolio/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')
 foreach($taskApk in $taskApks){& $taskAdb -s $taskSerial install -r $taskApk;if($LASTEXITCODE -ne 0){throw 'QA installation failed.'}}
 $taskApks | ForEach-Object {Get-FileHash -LiteralPath $_ -Algorithm SHA256 | Select-Object Path,Hash} | ConvertTo-Json | Set-Content -LiteralPath "$taskRoot/apk-inputs.json" -Encoding utf8
 $taskPrimaryBefore=PrimaryHash
 $taskPrimaryBefore | Set-Content -LiteralPath "$taskRoot/primary-room-before.sha256" -Encoding utf8
 $taskCreated=Device -taskArguments @('pm','create-user',$taskName)
 if($taskCreated -notmatch 'Success: created user id (\d+)'){throw 'Owned secondary user creation failed.'}
 $taskUser=[int]$Matches[1];if($taskUser -le 0 -or -not (IsOwnedUser)){throw 'Secondary user ownership check failed.'}
 [ordered]@{user=$taskUser;name=$taskName;run=$taskRun;before=$taskBefore.users;during=(Device -taskArguments @('pm','list','users'));physicalPhoneAccessed=$false} | ConvertTo-Json | Set-Content -LiteralPath "$taskRoot/secondary-installation.json" -Encoding utf8
 foreach($taskPackage in @('com.closingcount.app','com.closingcount.app.test')){Device -taskArguments @('cmd','package','install-existing','--user',"$taskUser",$taskPackage) | Out-Null}
 Device -taskArguments @('am','start-user','-w',"$taskUser") | Out-Null
 Device -taskArguments @('am','switch-user','-w',"$taskUser") | Out-Null
 AssertOwnedSurface
 foreach($taskPhase in @('settings','team','catalog','lifecycle')){
  AssertOwnedSurface
  $taskPhaseTimeout=if($taskPhase -eq 'lifecycle'){600}else{360}
  $taskFont=if($taskPhase -eq 'team'){'2.0'}else{'1.0'}
  $taskNight=if($taskPhase -eq 'team'){'yes'}else{'no'}
  $taskNightLabel=if($taskPhase -eq 'team'){'dark'}else{'light'}
  $taskUiConfigurationChanged=$true
  Device -taskArguments @('settings','--user',"$taskUser",'put','system','font_scale',$taskFont) | Out-Null
  Device -taskArguments @('cmd','uimode','night',$taskNight) | Out-Null
  Start-Sleep -Seconds 1
  $taskPhaseConfiguration=[ordered]@{phase=$taskPhase;user=$taskUser;requestedFontScale=[double]$taskFont;requestedNightMode=$taskNightLabel;observedSettingFontScale=(Device -taskArguments @('settings','--user',"$taskUser",'get','system','font_scale'));observedNightMode=(Device -taskArguments @('cmd','uimode','night'));physicalPhoneAccessed=$false}
  if([double]$taskPhaseConfiguration.observedSettingFontScale -ne [double]$taskFont -or $taskPhaseConfiguration.observedNightMode -ne "Night mode: $taskNight"){throw 'Owned admin phase UI configuration was not applied.'}
  $taskPhaseConfiguration | ConvertTo-Json | Set-Content -LiteralPath "$taskRoot/$taskPhase-configuration.json" -Encoding utf8
  $taskStdout="$taskRoot/$taskPhase-$taskRun.stdout.log";$taskStderr="$taskRoot/$taskPhase-$taskRun.stderr.log"
  $taskArgs="-e ccsAdminNativeQa true -e ccsAdminNativeRun $taskRun -e ccsAdminNativePhase $taskPhase -e ccsAdminNativeUser $taskUser -e ccsQaOrigin $($taskFixture.origin) -e ccsQaOutlet $($taskFixture.adminOutlet) -e ccsQaAdmin $($taskFixture.admin) -e ccsQaPassword '$($taskFixture.password)'"
  $taskRemote="am instrument --user $taskUser -w -r -e class com.closingcount.app.OnlinePendingAdminNativeInstrumentedTest $taskArgs com.closingcount.app.test/androidx.test.runner.AndroidJUnitRunner"
  try {
   $taskProcess=Start-Process -FilePath $taskAdb -ArgumentList "-s $taskSerial shell `"$taskRemote`"" -WindowStyle Hidden -PassThru -RedirectStandardOutput $taskStdout -RedirectStandardError $taskStderr
   $taskNativeHandle=$taskProcess.Handle
   if($taskNativeHandle -eq [IntPtr]::Zero){throw 'Native process handle was not captured.'}
  }catch {throw 'Could not start the owned admin instrumentation process.'}
  $taskClock=[Diagnostics.Stopwatch]::StartNew();$taskFinished=$false
  do {
   # Observe the same invocation; an incomplete/shared log never triggers a restart.
   $taskText=ReadLog $taskStdout
   if($taskText -match 'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=|Process crashed|AssumptionViolated|STATUS_CODE: -3'){throw 'Native admin instrumentation reported failure.'}
   if($taskProcess.WaitForExit(1000)){$taskFinished=$true;break}
  }while($taskClock.Elapsed.TotalSeconds -lt $taskPhaseTimeout)
  if(-not $taskFinished){throw 'Native admin phase exceeded the bounded wait on its original process handle.'}
  $taskProcess.Refresh();$taskExitCode=$taskProcess.ExitCode
  if($null -eq $taskExitCode){throw 'Completed admin instrumentation has no observed exit code.'}
  $taskText=ReadLog $taskStdout
  if($taskExitCode -ne 0 -or -not $taskText.Contains('OK (1 test)') -or $taskText -match 'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=|Process crashed|AssumptionViolated|STATUS_CODE: -3'){throw 'Native admin phase did not complete normally.'}
  $taskLines=@($taskText.Split("`n") | Where-Object {$_ -match '^INSTRUMENTATION_STATUS: ccsAdminNativeProof='})
  if($taskLines.Count -ne 1){throw 'Expected exactly one native admin phase proof.'}
  $taskProof=$taskLines[0].Substring('INSTRUMENTATION_STATUS: ccsAdminNativeProof='.Length).Trim() | ConvertFrom-Json
  if(-not $taskProof.passed -or $taskProof.run -ne $taskRun -or $taskProof.phase -ne $taskPhase -or $taskProof.user -ne $taskUser -or [Math]::Floor([double]$taskProof.uid/100000) -ne $taskUser -or $taskProof.pid -lt 1 -or $taskProof.outlet -ne $taskFixture.adminOutlet){throw 'Invalid isolated native admin proof.'}
  if($taskProof.inboxCount -ne $taskExpectedCounts[$taskPhase] -or @($taskProof.allCases).Count -ne $taskExpectedCounts[$taskPhase] -or @($taskProof.phaseCases).Count -ne $taskExpectedPhaseCounts[$taskPhase]){throw 'Native admin phase case counts differ from the agreed fixture contract.'}
  if($null -eq $taskProof.actualFontScale -or [Math]::Abs([double]$taskProof.actualFontScale-[double]$taskFont) -gt 0.01 -or $taskProof.actualNightMode -ne $taskNightLabel){throw 'Native activity configuration differs from the requested visual coverage.'}
  if(-not $taskProof.legacyUnchanged -or $taskProof.legacyBeforeSha256 -ne $taskProof.legacyAfterSha256 -or -not $taskProof.allFinalSendsUsedProductionUi -or -not $taskProof.packetsSeededDirectlyIntoInbox -or $taskProof.documentsPickerExercised -or $taskProof.physicalPhoneAccessed -or $taskProof.completePt07Acceptance){throw 'Native admin proof limits or preservation claims are invalid.'}
  if($taskProofs.Count -gt 0 -and ($taskProof.uid -ne $taskProofs[0].uid -or $taskProof.actor -ne $taskProofs[0].actor)){throw 'Native admin actor or isolated app UID changed between phases.'}
  $taskLastProof=$taskProof;$taskProofs+=$taskProof
  python 'D:/Code/Documents/Career-Portfolio-2026/automation/Collect-ClosingPendingAdminNativeR3.py' "$taskUser" $taskPhase
  if($LASTEXITCODE -ne 0){throw 'Native admin evidence collection failed.'}
  $taskProcessRecord=[ordered]@{phase=$taskPhase;stdout=[IO.Path]::GetFileName($taskStdout);stderr=[IO.Path]::GetFileName($taskStderr);normalVerification=$true;exitCode=[int]$taskExitCode;nativeHandleCaptured=$true;elapsedSeconds=$taskClock.Elapsed.TotalSeconds;timeoutSeconds=$taskPhaseTimeout;appPid=$taskProof.pid;appUid=$taskProof.uid;cumulativeCases=$taskExpectedCounts[$taskPhase];phaseCases=$taskExpectedPhaseCounts[$taskPhase]}
  $taskProcesses+=$taskProcessRecord
  $taskProcessRecord | ConvertTo-Json | Set-Content -LiteralPath "$taskRoot/$taskPhase-process.json" -Encoding utf8
  $taskProcess.Dispose();$taskProcess=$null
  # Normal teardown already completed; this only clears the owned installation
  # before the next independent invocation, with an explicit PID/UID guard.
  StopOwnedApp $taskProof
  python 'D:/Code/Documents/Career-Portfolio-2026/automation/Collect-ClosingPendingAdminNativeR3.py' "$taskUser" $taskPhase --frozen
  if($LASTEXITCODE -ne 0){throw 'Frozen secondary-only Room/inbox collection failed.'}
 }
 if($taskProofs.Count -ne 4){throw 'Expected all four sequential native admin phases.'}
 $taskPassed=$true
} catch {
 $taskOriginalFailure=$_
 try {
  $taskFailurePhase=if($taskPhase -in @('settings','team','catalog','lifecycle')){$taskPhase}else{'setup'}
  python 'D:/Code/Documents/Career-Portfolio-2026/automation/Capture-ClosingPendingAdminFailureR3.py' $taskFailurePhase
 }catch {Write-Output 'Best-effort QA failure screenshot unavailable; cleanup continues.'}
 throw $taskOriginalFailure
} finally {
 if($taskEmulatorOwned){
  try {if($null -ne $taskUser){StopOwnedApp $taskLastProof}}
  catch {$taskCleanupErrors+='Owned secondary app stop was not verified.'}
  try {if($null -ne $taskProcess){if(-not $taskProcess.HasExited){$taskProcess.Kill();$taskProcess.WaitForExit(5000) | Out-Null};$taskProcess.Dispose()}}
  catch {$taskCleanupErrors+='Owned adb handle cleanup was not verified.'}
  try {
   # No owner/profile mutation is allowed before a baseline has been accepted.
   if($null -ne $taskBefore -and $taskBefore.currentUser -eq '0'){
    Device -taskArguments @('am','switch-user','-w','0') | Out-Null
    if((Device -taskArguments @('am','get-current-user')) -ne '0'){throw 'Owner UI not restored.'}
    if($taskUiConfigurationChanged){
     if($taskBefore.font -eq 'null'){Device -taskArguments @('settings','--user','0','delete','system','font_scale') | Out-Null}
     else {Device -taskArguments @('settings','--user','0','put','system','font_scale',$taskBefore.font) | Out-Null}
     if($null -ne $taskBaselineNight){Device -taskArguments @('cmd','uimode','night',$taskBaselineNight) | Out-Null}
    }
   }
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
   $taskAfter=Snapshot;$taskSame=$null -ne $taskBefore -and ($taskBefore | ConvertTo-Json -Compress) -eq ($taskAfter | ConvertTo-Json -Compress)
   $taskAfter | ConvertTo-Json | Set-Content -LiteralPath "$taskRoot/device-after.json" -Encoding utf8
   if($null -ne $taskPrimaryBefore){$taskPrimaryAfter=PrimaryHash;$taskPrimaryAfter | Set-Content -LiteralPath "$taskRoot/primary-room-after.sha256" -Encoding utf8;$taskPrimarySame=$taskPrimaryBefore -eq $taskPrimaryAfter}
  }catch {$taskCleanupErrors+='Device or primary Room preservation could not be verified.'}
  try {
   [ordered]@{passed=($taskPassed -and $taskSame -and $taskPrimarySame -and $taskCleanupErrors.Count -eq 0);phases=$taskProofs;processes=$taskProcesses;cumulativeCasesCounts=@(1,3,4,9);normalVerificationTests=$taskProofs.Count;stopGuardMode='exact-owned-user-package-uid-normal-teardown';liveStopClaim=$false;deviceSettingsUsersAndCurrentUserRestored=$taskSame;primaryRoomFilesUnchanged=$taskPrimarySame;cleanupErrors=$taskCleanupErrors;packetsSeededDirectlyIntoInbox=$true;documentsPickerExercised=$false;physicalPhoneAccessed=$false;completePt07Acceptance=$false} | ConvertTo-Json -Depth 18 | Set-Content -LiteralPath "$taskRoot/verification.json" -Encoding utf8
  }finally {& 'D:/Code/Documents/Career-Portfolio-2026/automation/QAEmulator.ps1' -Action stop -Profile Phone}
 }
}
if(-not $taskPassed -or -not $taskSame -or -not $taskPrimarySame -or $taskCleanupErrors.Count -ne 0){throw 'Native administrative recovery acceptance or cleanup did not pass.'}
