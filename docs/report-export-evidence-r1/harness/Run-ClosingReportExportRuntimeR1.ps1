param([ValidateSet('http-final','light-final','dark-final','history-http','history-native','light-stable-final')][string]$StartAt='http-final',[switch]$StableScreenshotsOnly)
$ErrorActionPreference='Stop'
$taskProgram='D:/Code/Documents/Career-Portfolio-2026'
$taskRoot="$taskProgram/evidence/closing-count-report-export-r1"
$taskMarker='D:/Code/ToolCache/PortfolioQA/ccs-http-r1-fixture.json.stop'
$taskAdb='D:/Code/ToolCache/AndroidSdk/platform-tools/adb.exe'
$taskSerial='emulator-5580'
$taskFixtureProcess=$null
$taskFixtureDir=$null
function Stop-OwnedFixture {
 if($null -eq $script:taskFixtureProcess){return}
 Set-Content -LiteralPath $taskMarker -Value '' -NoNewline
 $taskDeadline=[DateTime]::UtcNow.AddSeconds(15)
 while((Get-NetTCPConnection -State Listen -LocalPort 4371 -ErrorAction SilentlyContinue) -and [DateTime]::UtcNow -lt $taskDeadline){Start-Sleep -Milliseconds 250}
 if(Get-NetTCPConnection -State Listen -LocalPort 4371 -ErrorAction SilentlyContinue){throw 'Owned fixture did not stop gracefully.'}
 if(-not $script:taskFixtureProcess.WaitForExit(15000)){throw 'Owned fixture wrapper has not completed.'}
 if($script:taskFixtureProcess.ExitCode -ne 0){throw 'Owned fixture wrapper failed.'}
 Move-Item -LiteralPath $taskMarker -Destination (Join-Path $script:taskFixtureDir 'completed-stop-marker.txt')
 $script:taskFixtureProcess=$null
}
function Start-OwnedFixture([string]$taskRun) {
 if(Get-NetTCPConnection -State Listen -LocalPort 4371 -ErrorAction SilentlyContinue){throw 'Refusing an existing fixture listener.'}
 $script:taskFixtureDir=Join-Path $taskRoot $taskRun
 if(Test-Path -LiteralPath $script:taskFixtureDir){throw 'Preserve prior evidence; choose a fresh run name.'}
 New-Item -ItemType Directory -Path $script:taskFixtureDir | Out-Null
 $script:taskFixtureProcess=Start-Process -FilePath 'C:/Program Files/nodejs/node.exe' -ArgumentList @("$taskProgram/automation/closing-count/serve-report-export-fixture-r1.mjs",$taskRun) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $script:taskFixtureDir 'fixture.stdout.log') -RedirectStandardError (Join-Path $script:taskFixtureDir 'fixture.stderr.log')
 $null=$script:taskFixtureProcess.Handle
 $taskDeadline=[DateTime]::UtcNow.AddSeconds(15)
 while(-not(Get-NetTCPConnection -State Listen -LocalPort 4371 -ErrorAction SilentlyContinue) -and [DateTime]::UtcNow -lt $taskDeadline){if($script:taskFixtureProcess.HasExited){throw 'Owned fixture exited before ready.'};Start-Sleep -Milliseconds 250}
 if(-not(Get-NetTCPConnection -State Listen -LocalPort 4371 -ErrorAction SilentlyContinue)){throw 'Owned fixture was not ready.'}
}
function Device([string[]]$taskArguments) {
 $taskResult=@(& $taskAdb -s $taskSerial shell @taskArguments)
 if($LASTEXITCODE -ne 0){throw 'Owned emulator command failed.'}
 return ($taskResult -join "`n").Trim()
}
& "$taskProgram/automation/QAEmulator.ps1" -Action start -Profile Phone -Renderer host -MemoryMb 1536
if($LASTEXITCODE -ne 0){throw 'Owned emulator failed to start.'}
if(@(& $taskAdb -s $taskSerial emu avd name) -notcontains 'PortfolioQA_Phone_API36'){throw 'Refusing another device.'}
$taskBaseline=[ordered]@{size=(Device -taskArguments @('wm','size'));density=(Device -taskArguments @('wm','density'));font=(Device -taskArguments @('settings','get','system','font_scale'));night=(Device -taskArguments @('cmd','uimode','night'))}
$taskBaseline | ConvertTo-Json | Set-Content -LiteralPath "$taskRoot/runtime-device-baseline.json" -Encoding utf8
try {
 Device -taskArguments @('wm','size','945x2100') | Out-Null
 Device -taskArguments @('wm','density','420') | Out-Null
 $taskRuns=if($StableScreenshotsOnly){@('light-stable-final','dark-stable-final')}else{@('http-final','light-stable-final','dark-stable-final','history-http','history-native')}
 if([array]::IndexOf($taskRuns,$StartAt) -lt 0){throw 'StartAt does not belong to this run set.'}
 foreach($taskRun in $taskRuns[([array]::IndexOf($taskRuns,$StartAt))..($taskRuns.Length-1)]) {
  Start-OwnedFixture $taskRun
  try {
   $taskFont=if($taskRun -like 'dark-*'){'2.0'}else{'1.0'}
   $taskNight=if($taskRun -like 'dark-*'){'yes'}else{'no'}
   Device -taskArguments @('settings','put','system','font_scale',$taskFont) | Out-Null
   Device -taskArguments @('cmd','uimode','night',$taskNight) | Out-Null
   if($taskRun -like 'history-*') {
    $taskMode=if($taskRun -eq 'history-http'){'Http'}else{'Native'}
    & "$taskProgram/automation/Check-ClosingHistoryR1.ps1" -Mode $taskMode -EvidenceDirectory $script:taskFixtureDir
   } else {
    $taskMode=if($taskRun -eq 'http-final'){'Http'}else{'Native'}
    & "$taskProgram/automation/Check-ClosingReportExportR1.ps1" -Mode $taskMode -Case $taskRun -EvidenceDirectory $script:taskFixtureDir
   }
  } finally { Stop-OwnedFixture }
 }
} finally {
 if($null -ne $script:taskFixtureProcess){Stop-OwnedFixture}
 $taskSize=if($taskBaseline.size -match 'Override size: (\d+x\d+)'){$Matches[1]}else{'reset'}
 $taskDensity=if($taskBaseline.density -match 'Override density: (\d+)'){$Matches[1]}else{'reset'}
 Device -taskArguments @('wm','size',$taskSize) | Out-Null
 Device -taskArguments @('wm','density',$taskDensity) | Out-Null
 if($taskBaseline.font -eq 'null'){Device -taskArguments @('settings','delete','system','font_scale') | Out-Null}else{Device -taskArguments @('settings','put','system','font_scale',$taskBaseline.font) | Out-Null}
 $taskNight=($taskBaseline.night -replace '^Night mode: ','').Trim()
 if($taskNight -notin @('auto','yes','no','custom')){throw 'Unknown original night mode.'}
 Device -taskArguments @('cmd','uimode','night',$taskNight) | Out-Null
 $taskRestored=[ordered]@{size=(Device -taskArguments @('wm','size'));density=(Device -taskArguments @('wm','density'));font=(Device -taskArguments @('settings','get','system','font_scale'));night=(Device -taskArguments @('cmd','uimode','night'))}
 $taskRestored | ConvertTo-Json | Set-Content -LiteralPath "$taskRoot/runtime-device-restored.json" -Encoding utf8
 if(($taskBaseline | ConvertTo-Json -Compress) -ne ($taskRestored | ConvertTo-Json -Compress)){throw 'Emulator settings not restored exactly.'}
 & "$taskProgram/automation/QAEmulator.ps1" -Action stop -Profile Phone
}
