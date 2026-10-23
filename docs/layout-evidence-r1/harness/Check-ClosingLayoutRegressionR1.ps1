$ErrorActionPreference='Stop'
$taskAutomation='D:/Code/Documents/Career-Portfolio-2026/automation'
$taskRoot='D:/Code/Documents/Career-Portfolio-2026/evidence/closing-count-layout-r1'
$taskControl='D:/Code/ToolCache/PortfolioQA/ccs-http-r1-fixture.json'
$taskFixtureProcess=$null
try{
 & "$taskAutomation/QAEmulator.ps1" -Action start -Profile Phone -MemoryMb 1536 -Renderer host
 if($LASTEXITCODE -ne 0){throw 'Owned emulator did not start'}
 foreach($taskMode in @('native','settings-native','catalog-native')){
  if(Get-NetTCPConnection -LocalPort 4371 -State Listen -ErrorAction SilentlyContinue){throw 'Fixture port already in use; refusing to replace it'}
  $taskCase="regression-$taskMode";$taskDir="$taskRoot/$taskCase";New-Item -ItemType Directory -Force -Path $taskDir | Out-Null
  if(Test-Path -LiteralPath "$taskControl.stop"){Remove-Item -LiteralPath "$taskControl.stop"}
  $taskStarted=[DateTime]::UtcNow
  $taskFixtureProcess=Start-Process -FilePath node -ArgumentList @("$taskAutomation/closing-count/serve-layout-fixture-r1.mjs",$taskCase) -WindowStyle Hidden -PassThru -RedirectStandardOutput "$taskDir/fixture-launcher.log" -RedirectStandardError "$taskDir/fixture-launcher.stderr.log"
  $null=$taskFixtureProcess.Handle
  try{
   $taskReady=$false
   for($taskAttempt=0;$taskAttempt -lt 100;$taskAttempt++){
    $taskFixtureProcess.Refresh();if($taskFixtureProcess.HasExited){throw 'Owned fixture exited before readiness'}
    if((Test-Path -LiteralPath $taskControl) -and (Get-Item -LiteralPath $taskControl).LastWriteTimeUtc -ge $taskStarted){
     try{$taskHealth=Invoke-RestMethod 'http://127.0.0.1:4371/api/health' -TimeoutSec 2;if($taskHealth.data.status -eq 'ok'){$taskReady=$true;break}}catch{}
    }
    Start-Sleep -Milliseconds 200
   }
   if(-not $taskReady){throw 'Owned fixture readiness timed out'}
   if($taskMode -eq 'native'){& "$taskAutomation/Check-ClosingNativeR1.ps1" -EvidenceDirectory $taskDir}
   elseif($taskMode -eq 'settings-native'){& "$taskAutomation/Check-ClosingSettingsR1.ps1" -Mode Native -EvidenceDirectory $taskDir}
   else{& "$taskAutomation/Check-ClosingCatalogNativeR1.ps1" -Mode Native -EvidenceDirectory $taskDir}
  }finally{
   New-Item -ItemType File -Force -Path "$taskControl.stop" | Out-Null
   if(-not $taskFixtureProcess.WaitForExit(15000)){throw 'Owned fixture did not stop'}
   if($taskFixtureProcess.ExitCode -ne 0){throw 'Owned fixture failed'}
   $taskFixtureProcess=$null
  }
 }
}finally{
 if($null -ne $taskFixtureProcess -and -not $taskFixtureProcess.HasExited){New-Item -ItemType File -Force -Path "$taskControl.stop" | Out-Null;$null=$taskFixtureProcess.WaitForExit(15000)}
 & "$taskAutomation/QAEmulator.ps1" -Action stop -Profile Phone
}
