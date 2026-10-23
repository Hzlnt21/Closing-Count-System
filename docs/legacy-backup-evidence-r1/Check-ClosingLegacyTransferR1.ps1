$ErrorActionPreference='Stop'
$taskAutomation='D:/Code/Documents/Career-Portfolio-2026/automation'
$taskRoot='D:/Code/Documents/Career-Portfolio-2026/evidence/closing-count-legacy-transfer-r1'
$taskAdb='D:/Code/ToolCache/AndroidSdk/platform-tools/adb.exe';$taskSerial='emulator-5580'
try{
 & "$taskAutomation/QAEmulator.ps1" -Action start -Profile Phone -MemoryMb 1536 -Renderer host
 if($LASTEXITCODE -ne 0 -or @(& $taskAdb -s $taskSerial emu avd name) -notcontains 'PortfolioQA_Phone_API36'){throw 'Refusing another device'}
 $taskApks=@('D:/Code/Worktrees/Closing-Count-Portfolio/app/build/outputs/apk/debug/app-debug.apk','D:/Code/Worktrees/Closing-Count-Portfolio/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')
 foreach($taskApk in $taskApks){& $taskAdb -s $taskSerial install -r $taskApk;if($LASTEXITCODE -ne 0){throw 'Owned QA installation failed'}}
 $taskApks | ForEach-Object {Get-FileHash -LiteralPath $_ -Algorithm SHA256 | Select-Object Path,Hash} | ConvertTo-Json | Set-Content "$taskRoot/apk-inputs.json" -Encoding utf8
 & $taskAdb -s $taskSerial shell am instrument -w -r -e class com.closingcount.app.BackupInstrumentedTest com.closingcount.app.test/androidx.test.runner.AndroidJUnitRunner *> "$taskRoot/instrumentation.log"
 $taskExit=$LASTEXITCODE;$taskText=Get-Content "$taskRoot/instrumentation.log" -Raw
 $taskPassed=$taskExit -eq 0 -and $taskText -match 'OK \(10 tests\)' -and $taskText -notmatch 'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=|Process crashed|AssumptionViolated|STATUS_CODE: -3'
 [ordered]@{passed=$taskPassed;tests=10;scope='Legacy codec and isolated in-memory Room transactions; not native file picker/server transfer';emulator='PortfolioQA_Phone_API36';physicalPhoneAccessed=$false;operationalDatabaseAccessed=$false;onlineServerImportOrExport=$false} | ConvertTo-Json | Set-Content "$taskRoot/verification.json" -Encoding utf8
 Get-Content "$taskRoot/instrumentation.log" -Tail 35
 if(-not $taskPassed){throw 'Legacy transfer acceptance failed; inspect existing evidence'}
}finally{& "$taskAutomation/QAEmulator.ps1" -Action stop -Profile Phone}
