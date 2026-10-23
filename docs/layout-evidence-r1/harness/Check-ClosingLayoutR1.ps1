param([ValidateSet('Phone','Tablet')][string]$Profile='Phone',[string]$Case='phone-large-dark',[int]$WidthDp=360,[int]$HeightDp=800,[double]$FontScale=2,[bool]$Dark=$true)
$ErrorActionPreference='Stop'
if($Case -notmatch '^[a-z0-9-]{1,40}$' -or $WidthDp -lt 320 -or $WidthDp -gt 1400 -or $HeightDp -lt 320 -or $HeightDp -gt 1400 -or $FontScale -lt 1 -or $FontScale -gt 2){throw 'Invalid owned layout case'}
$taskAdb='D:/Code/ToolCache/AndroidSdk/platform-tools/adb.exe';$taskSerial=if($Profile -eq 'Phone'){'emulator-5580'}else{'emulator-5582'}
$taskAvd="PortfolioQA_${Profile}_API36"
if(@(& $taskAdb -s $taskSerial emu avd name) -notcontains $taskAvd){throw 'Refusing another device'}
$taskFixture=Get-Content 'D:/Code/ToolCache/PortfolioQA/ccs-http-r1-fixture.json' -Raw | ConvertFrom-Json
if($taskFixture.origin -ne 'http://10.0.2.2:4371' -or $taskFixture.outlet -notmatch '^[a-f0-9-]{36}$' -or $taskFixture.admin -notmatch '^qah[a-z0-9]+\.admin$' -or $taskFixture.password -notmatch '^[A-Za-z0-9 !.-]{12,64}$'){throw 'Unexpected owned fixture'}
$taskRoot="D:/Code/Documents/Career-Portfolio-2026/evidence/closing-count-layout-r1/$Case"
New-Item -ItemType Directory -Force -Path $taskRoot | Out-Null
function Shell([string[]]$taskArguments){$taskOutput=@(& $taskAdb -s $taskSerial shell @taskArguments);if($LASTEXITCODE -ne 0){throw 'Owned emulator command failed'};return ($taskOutput -join "`n").Trim()}
$taskBaseline=[ordered]@{size=(Shell -taskArguments @('wm','size'));density=(Shell -taskArguments @('wm','density'));font=(Shell -taskArguments @('settings','get','system','font_scale'));night=(Shell -taskArguments @('cmd','uimode','night'))}
$taskBaseline | ConvertTo-Json | Set-Content "$taskRoot/device-baseline.json" -Encoding utf8
$taskPassed=$false;$taskRestored=$false
try{
 $null=Shell -taskArguments @('am','force-stop','com.closingcount.app')
 $taskDensity=if($Profile -eq 'Phone'){420}else{320}
 $taskWidth=[int]($WidthDp*$taskDensity/160);$taskHeight=[int]($HeightDp*$taskDensity/160)
 $null=Shell -taskArguments @('wm','size',"${taskWidth}x${taskHeight}");$null=Shell -taskArguments @('wm','density',"$taskDensity")
 $null=Shell -taskArguments @('settings','put','system','font_scale',$FontScale.ToString([Globalization.CultureInfo]::InvariantCulture))
 $null=Shell -taskArguments @('cmd','uimode','night',$(if($Dark){'yes'}else{'no'}))
 $taskApks=@('D:/Code/Worktrees/Closing-Count-Portfolio/app/build/outputs/apk/debug/app-debug.apk','D:/Code/Worktrees/Closing-Count-Portfolio/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')
 foreach($taskApk in $taskApks){& $taskAdb -s $taskSerial install -r $taskApk;if($LASTEXITCODE -ne 0){throw 'QA APK install failed'}}
 $taskApks | ForEach-Object {Get-FileHash -LiteralPath $_ -Algorithm SHA256} | Select-Object Path,Hash | ConvertTo-Json | Set-Content "$taskRoot/apk-inputs.json" -Encoding utf8
 $taskDark=if($Dark){'true'}else{'false'}
 $taskCommand="am instrument -w -r -e class com.closingcount.app.OnlineLayoutInstrumentedTest -e ccsLayoutQa true -e ccsLayoutCase $Case -e ccsWidthDp $WidthDp -e ccsFontScale $($FontScale.ToString([Globalization.CultureInfo]::InvariantCulture)) -e ccsDark $taskDark -e ccsQaOrigin $($taskFixture.origin) -e ccsQaOutlet $($taskFixture.outlet) -e ccsQaAdmin $($taskFixture.admin) -e ccsQaPassword '$($taskFixture.password)' com.closingcount.app.test/androidx.test.runner.AndroidJUnitRunner"
 & $taskAdb -s $taskSerial shell $taskCommand *> "$taskRoot/instrumentation.log"
 $taskExit=$LASTEXITCODE;$taskText=Get-Content "$taskRoot/instrumentation.log" -Raw
 $taskPassed=$taskExit -eq 0 -and $taskText.Contains('OK (1 test)') -and $taskText -notmatch 'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=|Process crashed|AssumptionViolated|STATUS_CODE: -3'
 Get-Content "$taskRoot/instrumentation.log" -Tail 32
 & $taskAdb -s $taskSerial pull "/sdcard/Android/data/com.closingcount.app/files/layout-$Case.json" "$taskRoot/layout.json"
 if($taskPassed -and $LASTEXITCODE -ne 0){throw 'Missing layout proof'}
 foreach($taskScene in @('login','overview','input','settings-review','team-form','catalog-form','unsaved','review','history','account')){
  $taskDeviceFile="/sdcard/Android/data/com.closingcount.app/files/layout-$Case-$taskScene.png"
  & $taskAdb -s $taskSerial shell test -f $taskDeviceFile
  if($LASTEXITCODE -eq 0){& $taskAdb -s $taskSerial pull $taskDeviceFile "$taskRoot/$taskScene.png";if($LASTEXITCODE -ne 0){throw 'Screenshot retrieval failed'}}
 }
}finally{
 $null=Shell -taskArguments @('am','force-stop','com.closingcount.app')
 if($taskBaseline.size -match 'Override size: (\d+x\d+)'){$null=Shell -taskArguments @('wm','size',$Matches[1])}else{$null=Shell -taskArguments @('wm','size','reset')}
 if($taskBaseline.density -match 'Override density: (\d+)'){$null=Shell -taskArguments @('wm','density',$Matches[1])}else{$null=Shell -taskArguments @('wm','density','reset')}
 if($taskBaseline.font -match '^\d+(\.\d+)?$'){$null=Shell -taskArguments @('settings','put','system','font_scale',$taskBaseline.font)}else{$null=Shell -taskArguments @('settings','delete','system','font_scale')}
 if($taskBaseline.night -match '(auto|yes|no|custom)'){$null=Shell -taskArguments @('cmd','uimode','night',$Matches[1])}else{throw 'Cannot restore original night mode'}
 $taskAfter=[ordered]@{size=(Shell -taskArguments @('wm','size'));density=(Shell -taskArguments @('wm','density'));font=(Shell -taskArguments @('settings','get','system','font_scale'));night=(Shell -taskArguments @('cmd','uimode','night'))}
 $taskRestored=($taskAfter | ConvertTo-Json -Compress) -eq ($taskBaseline | ConvertTo-Json -Compress)
 $taskAfter | ConvertTo-Json | Set-Content "$taskRoot/device-restored.json" -Encoding utf8
 [ordered]@{passed=$taskPassed;case=$Case;profile=$Profile;widthDp=$WidthDp;heightDp=$HeightDp;fontScale=$FontScale;dark=$Dark;emulator=$taskAvd;outlet=$taskFixture.outlet;deviceSettingsRestored=$taskRestored;physicalPhoneAccessed=$false;publicAcceptance=$false} | ConvertTo-Json | Set-Content "$taskRoot/verification.json" -Encoding utf8
}
if(-not $taskPassed -or -not $taskRestored){throw 'Layout acceptance did not pass or emulator configuration was not restored'}
