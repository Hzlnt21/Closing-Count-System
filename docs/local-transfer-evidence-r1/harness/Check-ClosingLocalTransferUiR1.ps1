param([ValidateSet('Engine','Legacy','Native')][string]$Mode='Native',[string]$Case='phone-light',[double]$FontScale=1,[bool]$Dark=$false)
$ErrorActionPreference='Stop'
if($Case -notmatch '^[a-z0-9-]{1,40}$' -or $FontScale -lt 1 -or $FontScale -gt 2){throw 'Invalid owned QA configuration'}
$taskAdb='D:/Code/ToolCache/AndroidSdk/platform-tools/adb.exe';$taskSerial='emulator-5580'
if(@(& $taskAdb -s $taskSerial emu avd name) -notcontains 'PortfolioQA_Phone_API36'){throw 'Refusing another device'}
$taskRoot="D:/Code/Documents/Career-Portfolio-2026/evidence/closing-count-local-transfer-ui-r1/$Case"
New-Item -ItemType Directory -Force -Path $taskRoot | Out-Null
function Shell([string[]]$taskArguments){$taskOutput=@(& $taskAdb -s $taskSerial shell @taskArguments);if($LASTEXITCODE -ne 0){throw 'Owned emulator command failed'};return ($taskOutput -join "`n").Trim()}
$taskBaseline=[ordered]@{size=(Shell -taskArguments @('wm','size'));density=(Shell -taskArguments @('wm','density'));font=(Shell -taskArguments @('settings','get','system','font_scale'));night=(Shell -taskArguments @('cmd','uimode','night'))}
$taskBaseline | ConvertTo-Json | Set-Content "$taskRoot/device-baseline.json" -Encoding utf8
$taskPassed=$false;$taskRestored=$false
try{
 $null=Shell -taskArguments @('am','force-stop','com.closingcount.app')
 $null=Shell -taskArguments @('wm','size','945x2100');$null=Shell -taskArguments @('wm','density','420')
 $null=Shell -taskArguments @('settings','put','system','font_scale',$FontScale.ToString([Globalization.CultureInfo]::InvariantCulture))
 $null=Shell -taskArguments @('cmd','uimode','night',$(if($Dark){'yes'}else{'no'}))
 $taskApks=@('D:/Code/Worktrees/Closing-Count-Portfolio/app/build/outputs/apk/debug/app-debug.apk','D:/Code/Worktrees/Closing-Count-Portfolio/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')
 foreach($taskApk in $taskApks){& $taskAdb -s $taskSerial install -r $taskApk;if($LASTEXITCODE -ne 0){throw 'QA install failed'}}
 $taskApks | ForEach-Object {Get-FileHash -LiteralPath $_ -Algorithm SHA256 | Select-Object Path,Hash} | ConvertTo-Json | Set-Content "$taskRoot/apk-inputs.json" -Encoding utf8
 $taskClass=if($Mode -eq 'Engine'){'LocalBackupTransferInstrumentedTest'}elseif($Mode -eq 'Legacy'){'BackupInstrumentedTest'}else{'LocalTransferNativeInstrumentedTest'}
 $taskExpected=if($Mode -eq 'Engine'){'OK (4 tests)'}elseif($Mode -eq 'Legacy'){'OK (10 tests)'}else{'OK (1 test)'}
 & $taskAdb -s $taskSerial shell am instrument -w -r -e class "com.closingcount.app.$taskClass" -e ccsLocalTransferQa true -e ccsTransferCase $Case com.closingcount.app.test/androidx.test.runner.AndroidJUnitRunner *> "$taskRoot/instrumentation.log"
 $taskExit=$LASTEXITCODE;$taskText=Get-Content "$taskRoot/instrumentation.log" -Raw
 $taskJUnitPassed=$taskExit -eq 0 -and $taskText.Contains($taskExpected) -and $taskText -notmatch 'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=|Process crashed|AssumptionViolated|STATUS_CODE: -3'
 Get-Content "$taskRoot/instrumentation.log" -Tail 35
 if($Mode -eq 'Native'){
  & $taskAdb -s $taskSerial pull "/sdcard/Android/data/com.closingcount.app/files/local-transfer-$Case.json" "$taskRoot/native-proof.json"
  if($taskJUnitPassed -and $LASTEXITCODE -ne 0){throw 'Missing native proof'}
  foreach($taskScene in @('home','picker','invalid','review','restored','backup-written') + $(if(-not $taskJUnitPassed){@('failure')}else{@()})){
   $taskDeviceFile="/sdcard/Android/data/com.closingcount.app/files/local-transfer-$Case-$taskScene.png"
   & $taskAdb -s $taskSerial shell test -f $taskDeviceFile
   if($LASTEXITCODE -eq 0){& $taskAdb -s $taskSerial pull $taskDeviceFile "$taskRoot/$taskScene.png";if($LASTEXITCODE -ne 0){throw 'Missing screenshot'}}
  }
  & $taskAdb -s $taskSerial shell test -f "/sdcard/Android/data/com.closingcount.app/files/local-transfer-$Case-failure.txt"
  if(-not $taskJUnitPassed -and $LASTEXITCODE -eq 0){& $taskAdb -s $taskSerial pull "/sdcard/Android/data/com.closingcount.app/files/local-transfer-$Case-failure.txt" "$taskRoot/failure.txt"}
  if($taskJUnitPassed){
   $taskProof=Get-Content "$taskRoot/native-proof.json" -Raw | ConvertFrom-Json
   if(-not $taskProof.passed -or -not $taskProof.originalLocalDataRestored -or $taskProof.onlineBeforeSha256 -ne $taskProof.onlineAfterSha256 -or [Math]::Abs($taskProof.fontScale-$FontScale) -gt 0.03 -or [Math]::Abs($taskProof.widthDp-360) -gt 8 -or [Math]::Abs($taskProof.heightDp-800) -gt 8 -or $taskProof.dark -ne $Dark){throw 'Native proof/configuration did not match'}
   if($taskProof.exportFilename -notmatch '^ccs-qa-export-[a-f0-9-]+\.json$'){throw 'Unexpected dummy export filename'}
   & $taskAdb -s $taskSerial pull "/sdcard/Download/$($taskProof.exportFilename)" "$taskRoot/exported-backup.json"
   if($LASTEXITCODE -ne 0 -or (Get-FileHash -LiteralPath "$taskRoot/exported-backup.json" -Algorithm SHA256).Hash.ToLowerInvariant() -ne $taskProof.exportSha256){throw 'Export fingerprint mismatch'}
  }
 }
 $taskPassed=$taskJUnitPassed
}finally{
 $null=Shell -taskArguments @('am','force-stop','com.closingcount.app')
 if($taskBaseline.size -match 'Override size: (\d+x\d+)'){$null=Shell -taskArguments @('wm','size',$Matches[1])}else{$null=Shell -taskArguments @('wm','size','reset')}
 if($taskBaseline.density -match 'Override density: (\d+)'){$null=Shell -taskArguments @('wm','density',$Matches[1])}else{$null=Shell -taskArguments @('wm','density','reset')}
 if($taskBaseline.font -match '^\d+(\.\d+)?$'){$null=Shell -taskArguments @('settings','put','system','font_scale',$taskBaseline.font)}else{$null=Shell -taskArguments @('settings','delete','system','font_scale')}
 if($taskBaseline.night -match '(auto|yes|no|custom)'){$null=Shell -taskArguments @('cmd','uimode','night',$Matches[1])}else{throw 'Cannot restore original night mode'}
 $taskAfter=[ordered]@{size=(Shell -taskArguments @('wm','size'));density=(Shell -taskArguments @('wm','density'));font=(Shell -taskArguments @('settings','get','system','font_scale'));night=(Shell -taskArguments @('cmd','uimode','night'))}
 $taskRestored=($taskAfter | ConvertTo-Json -Compress) -eq ($taskBaseline | ConvertTo-Json -Compress)
 $taskAfter | ConvertTo-Json | Set-Content "$taskRoot/device-restored.json" -Encoding utf8
 [ordered]@{passed=$taskPassed;case=$Case;mode=$Mode;fontScale=$FontScale;dark=$Dark;deviceSettingsRestored=$taskRestored;physicalPhoneAccessed=$false;serverTransfer=$false} | ConvertTo-Json | Set-Content "$taskRoot/verification.json" -Encoding utf8
}
if(-not $taskPassed -or -not $taskRestored){throw 'Local transfer acceptance failed or device settings not restored'}
