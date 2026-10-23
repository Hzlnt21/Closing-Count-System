$ErrorActionPreference='Stop'
$taskProbeRoot='D:/Code/Documents/Career-Portfolio-2026/evidence/closing-count-native-restart-r5'
$taskProbeShell='C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe'
function ReadProbeLog([string]$taskPath){
 $taskLogStream=$null;$taskLogReader=$null
 try {
  $taskLogStream=[IO.File]::Open($taskPath,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::ReadWrite)
  $taskLogReader=[IO.StreamReader]::new($taskLogStream,[Text.Encoding]::UTF8,$true)
  return $taskLogReader.ReadToEnd()
 }catch [IO.IOException] {return ''}
 finally {if($null -ne $taskLogReader){$taskLogReader.Dispose()}elseif($null -ne $taskLogStream){$taskLogStream.Dispose()}}
}
$taskProbeCases=@()
foreach($taskExpected in @(0,7)){
 $taskProbeId=[Guid]::NewGuid().ToString('N')
 $taskProbeOut="$taskProbeRoot/process-handle-probe-$taskExpected-$taskProbeId.stdout.log"
 $taskProbeErr="$taskProbeRoot/process-handle-probe-$taskExpected-$taskProbeId.stderr.log"
 $taskChildCode="Write-Output 'probe-ready'; Start-Sleep -Milliseconds 1500; Write-Output 'probe-complete'; exit $taskExpected"
 $taskEncoded=[Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($taskChildCode))
 $taskChild=$null
 try {
  $taskChild=Start-Process -FilePath $taskProbeShell -ArgumentList "-NoProfile -NonInteractive -EncodedCommand $taskEncoded" -WindowStyle Hidden -PassThru -RedirectStandardOutput $taskProbeOut -RedirectStandardError $taskProbeErr
  $taskNativeHandle=$taskChild.Handle
  if($taskNativeHandle -eq [IntPtr]::Zero){throw 'Local probe process handle was not captured.'}
  $taskReadyLive=$false;$taskProbeUntil=[DateTime]::UtcNow.AddSeconds(10)
  do {
   $taskProbeText=ReadProbeLog $taskProbeOut
   if($taskProbeText.Contains('probe-ready') -and -not $taskChild.HasExited){$taskReadyLive=$true;break}
   if($taskChild.HasExited){break}
   Start-Sleep -Milliseconds 50
  }while([DateTime]::UtcNow -lt $taskProbeUntil)
  if(-not $taskReadyLive){throw 'Local probe ready line was not observed while its hidden child was live.'}
  if(-not $taskChild.WaitForExit(10000)){throw 'Local probe did not exit on its original process handle.'}
  $taskChild.Refresh();$taskActual=$taskChild.ExitCode
  if($null -eq $taskActual -or $taskActual -ne $taskExpected){throw 'Local probe exit code was null or incorrect.'}
  if(-not (ReadProbeLog $taskProbeOut).Contains('probe-complete')){throw 'Local probe output did not complete.'}
  $taskProbeCases+=[ordered]@{expectedExitCode=$taskExpected;observedExitCode=[int]$taskActual;processId=$taskChild.Id;nativeHandleCaptured=$true;readyObservedWhileLive=$taskReadyLive;sharedReadWorked=$true;stdout=[IO.Path]::GetFileName($taskProbeOut);stderr=[IO.Path]::GetFileName($taskProbeErr)}
 } finally {
  if($null -ne $taskChild){if(-not $taskChild.HasExited){$taskChild.Kill();$taskChild.WaitForExit(5000) | Out-Null};$taskChild.Dispose()}
 }
}
[ordered]@{passed=($taskProbeCases.Count -eq 2);cases=$taskProbeCases;emulatorAccessed=$false;buildRun=$false;databaseAccessed=$false;physicalPhoneAccessed=$false} | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath "$taskProbeRoot/process-handle-local-probe.json" -Encoding utf8
Write-Output 'Local hidden-process probe passed: actual exit codes 0 and 7, shared live stdout reads, immediate native-handle capture.'
