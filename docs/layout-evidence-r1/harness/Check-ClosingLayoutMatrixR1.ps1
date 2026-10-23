param([string[]]$Cases=@(),[switch]$SkipVerified)
$ErrorActionPreference='Stop'
$taskRoot='D:/Code/Documents/Career-Portfolio-2026/evidence/closing-count-layout-r1'
$taskAutomation='D:/Code/Documents/Career-Portfolio-2026/automation'
$taskControl='D:/Code/ToolCache/PortfolioQA/ccs-http-r1-fixture.json'
$taskMatrix=@(
 @{Code='phone-large-dark';Profile='Phone';Width=360;Height=800;Font=2.0;Dark=$true},
 @{Code='phone-small-light';Profile='Phone';Width=360;Height=800;Font=1.0;Dark=$false},
 @{Code='phone-landscape-light';Profile='Phone';Width=800;Height=360;Font=1.3;Dark=$false},
 @{Code='tablet-light';Profile='Tablet';Width=800;Height=1280;Font=1.0;Dark=$false},
 @{Code='tablet-dark-large';Profile='Tablet';Width=800;Height=1280;Font=2.0;Dark=$true},
 @{Code='tablet-landscape-dark';Profile='Tablet';Width=1280;Height=800;Font=1.3;Dark=$true}
)
foreach($taskCode in $Cases){if($taskCode -notin $taskMatrix.Code){throw 'Unknown layout case'}}
if($Cases.Count){$taskMatrix=@($taskMatrix | Where-Object {$_.Code -in $Cases})}
$taskHashes=@('D:/Code/Worktrees/Closing-Count-Portfolio/app/build/outputs/apk/debug/app-debug.apk','D:/Code/Worktrees/Closing-Count-Portfolio/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk') | ForEach-Object {(Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash}
$taskResults=[Collections.Generic.List[object]]::new();$taskProfile='';$taskFixtureProcess=$null
function Stop-Fixture{
 if($null -ne $script:taskFixtureProcess){
  New-Item -ItemType File -Force -Path "$taskControl.stop" | Out-Null
  if(-not $script:taskFixtureProcess.WaitForExit(15000)){throw 'Owned fixture did not stop; inspect its existing process'}
  if($script:taskFixtureProcess.ExitCode -ne 0){throw 'Owned fixture reported failure'}
  $script:taskFixtureProcess=$null
 }
}
try{
 foreach($taskCase in $taskMatrix){
  $taskCaseDir=Join-Path $taskRoot $taskCase.Code
  if($SkipVerified -and (Test-Path -LiteralPath "$taskCaseDir/verification.json")){
   $taskOld=Get-Content "$taskCaseDir/verification.json" -Raw | ConvertFrom-Json
   $taskOldHashes=Get-Content "$taskCaseDir/apk-inputs.json" -Raw | ConvertFrom-Json
   if($taskOld.passed -and $taskOld.deviceSettingsRestored -and $taskOld.profile -eq $taskCase.Profile -and $taskOld.widthDp -eq $taskCase.Width -and $taskOld.heightDp -eq $taskCase.Height -and $taskOld.fontScale -eq $taskCase.Font -and $taskOld.dark -eq $taskCase.Dark -and $taskOldHashes.Count -eq 2 -and $taskOldHashes[0].Hash -eq $taskHashes[0] -and $taskOldHashes[1].Hash -eq $taskHashes[1]){
    $taskResults.Add([ordered]@{case=$taskCase.Code;passed=$true;reusedIdenticalApks=$true});Write-Output "Verified existing identical-APK case: $($taskCase.Code)";continue
   }
  }
  if($taskProfile -ne $taskCase.Profile){
   if($taskProfile){& "$taskAutomation/QAEmulator.ps1" -Action stop -Profile $taskProfile}
   & "$taskAutomation/QAEmulator.ps1" -Action start -Profile $taskCase.Profile -MemoryMb 1536 -Renderer host
   if($LASTEXITCODE -ne 0){throw 'Owned emulator did not start'}
   $taskProfile=$taskCase.Profile
  }
  if(Get-NetTCPConnection -LocalPort 4371 -State Listen -ErrorAction SilentlyContinue){throw 'Fixture port already owned by another live process; refusing to replace it'}
  New-Item -ItemType Directory -Force -Path $taskCaseDir | Out-Null
  if(Test-Path -LiteralPath "$taskControl.stop"){Remove-Item -LiteralPath "$taskControl.stop"}
  $taskStarted=[DateTime]::UtcNow
  $script:taskFixtureProcess=Start-Process -FilePath node -ArgumentList @("$taskAutomation/closing-count/serve-layout-fixture-r1.mjs",$taskCase.Code) -WindowStyle Hidden -PassThru -RedirectStandardOutput "$taskCaseDir/fixture-launcher.log" -RedirectStandardError "$taskCaseDir/fixture-launcher.stderr.log"
  $null=$script:taskFixtureProcess.Handle;$taskReady=$false
  for($taskAttempt=0;$taskAttempt -lt 100;$taskAttempt++){
   $script:taskFixtureProcess.Refresh();if($script:taskFixtureProcess.HasExited){throw 'Owned fixture exited before readiness'}
   if((Test-Path -LiteralPath $taskControl) -and (Get-Item -LiteralPath $taskControl).LastWriteTimeUtc -ge $taskStarted){
    try{$taskHealth=Invoke-RestMethod 'http://127.0.0.1:4371/api/health' -TimeoutSec 2;if($taskHealth.data.status -eq 'ok'){$taskReady=$true;break}}catch{}
   }
   Start-Sleep -Milliseconds 200
  }
  if(-not $taskReady){throw 'Owned fixture readiness timed out'}
  try{
   Write-Output "Running layout case: $($taskCase.Code)"
   & "$taskAutomation/Check-ClosingLayoutR1.ps1" -Profile $taskCase.Profile -Case $taskCase.Code -WidthDp $taskCase.Width -HeightDp $taskCase.Height -FontScale $taskCase.Font -Dark $taskCase.Dark
   $taskVerified=Get-Content "$taskCaseDir/verification.json" -Raw | ConvertFrom-Json
   if(-not $taskVerified.passed -or -not $taskVerified.deviceSettingsRestored){throw 'Layout case failed'}
   $taskProbeMatches=$false
   if(Test-Path -LiteralPath "$taskRoot/validator-probe-apk-inputs.json"){
    $taskProbeApks=Get-Content "$taskRoot/validator-probe-apk-inputs.json" -Raw | ConvertFrom-Json
    $taskProbeMatches=$taskProbeApks.Count -eq 2 -and $taskProbeApks[0].Hash -eq $taskHashes[0] -and $taskProbeApks[1].Hash -eq $taskHashes[1]
   }
   if(-not $taskProbeMatches){
    $taskAdb='D:/Code/ToolCache/AndroidSdk/platform-tools/adb.exe';$taskSerial=if($taskCase.Profile -eq 'Phone'){'emulator-5580'}else{'emulator-5582'}
    & $taskAdb -s $taskSerial shell am instrument -w -r -e class com.closingcount.app.OnlineLayoutProbeInstrumentedTest com.closingcount.app.test/androidx.test.runner.AndroidJUnitRunner *> "$taskRoot/validator-probe.log"
    $taskProbeText=Get-Content "$taskRoot/validator-probe.log" -Raw
    if($LASTEXITCODE -ne 0 -or $taskProbeText -notmatch 'OK \(1 test\)' -or $taskProbeText -match 'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=|Process crashed|AssumptionViolated|STATUS_CODE: -3'){throw 'Validator control probe failed'}
    Copy-Item -LiteralPath "$taskCaseDir/apk-inputs.json" -Destination "$taskRoot/validator-probe-apk-inputs.json"
   }
   $taskResults.Add([ordered]@{case=$taskCase.Code;passed=$true;reusedIdenticalApks=$false})
   Write-Output "Verified layout case: $($taskCase.Code)"
  }finally{Stop-Fixture}
 }
 $taskResults | ConvertTo-Json | Set-Content "$taskRoot/matrix-run.json" -Encoding utf8
}finally{
 Stop-Fixture
 if($taskProfile){& "$taskAutomation/QAEmulator.ps1" -Action stop -Profile $taskProfile}
}
