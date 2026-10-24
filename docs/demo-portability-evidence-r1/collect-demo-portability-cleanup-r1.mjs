import fs from 'node:fs';import path from 'node:path';import {pathToFileURL} from 'node:url';import {spawnSync} from 'node:child_process';import assert from 'node:assert/strict';
const root='D:/Code/Worktrees/Closing-Count-Portfolio',out='D:/Code/Documents/Career-Portfolio-2026/evidence/closing-count-demo-portability-r1';
const priv=await import(pathToFileURL(path.join(root,'demo/private-state.mjs'))),db=await import(pathToFileURL(path.join(root,'service/demo/database.mjs')));
const paths=[['legacy-v1','D:/Code/ToolCache/PortfolioQA/closing-count-demo-r1'],['portable-v2','D:/Code/ToolCache/PortfolioQA/Portable Demo Profile R1/ClosingCountDemo'],['seed-exit-v2','D:/Code/ToolCache/PortfolioQA/Crash Demo Profile R1/ClosingCountDemo'],['locked-seed-exit-v2','D:/Code/ToolCache/PortfolioQA/Locked Crash Demo Profile R1/ClosingCountDemo']];
const profiles=[],runs=[];
for(const [profile,dir] of paths) {
 const state=priv.loadState(dir),info=db.assertContainer(state),run=priv.readJson(path.join(state.dir,'active-run.json'));
 assert.equal(info.State.Running,false);assert.equal(run.status,'stopped');assert.equal(fs.existsSync(path.join(dir,'controller.lock')),false);runs.push(run.run);
 const events=path.join(dir,'server-events.jsonl');const count=fs.existsSync(events)?fs.readFileSync(events,'utf8').trim().split('\n').filter(Boolean).length:0;assert.equal(count,0);
 profiles.push({profile,privateIdentityVersion:state.value.version,ownedContainerStopped:true,runRecordedStopped:true,controllerLockAbsent:true,recordedProductionServerInternalErrorEvents:count,volumeRetained:true});
}
const ps=`$ErrorActionPreference='Stop'; $runs=@(${runs.map(r=>"'"+r+"'").join(',')}); $processes=@(Get-CimInstance Win32_Process -Filter "Name='node.exe'" -ErrorAction Stop | Where-Object { $line=$_.CommandLine; $line -match '[\\/]service[\\/]demo[\\/]server.mjs' -and @($runs | Where-Object {$line -like "*$_*"}).Count -gt 0 }); $listeners=@(Get-NetTCPConnection -State Listen -ErrorAction Stop | Where-Object {$_.LocalPort -in @(4372,55437)}); @{ownedServerProcesses=$processes.Count;demoListeners=$listeners.Count}|ConvertTo-Json -Compress`;
const observed=spawnSync('powershell.exe',['-NoProfile','-NonInteractive','-Command',ps],{encoding:'utf8',timeout:20000});assert.equal(observed.status,0);const os=JSON.parse(observed.stdout);assert.equal(os.ownedServerProcesses,0);assert.equal(os.demoListeners,0);
const result={passed:true,profiles,windowsInventory:os,sourceDataAndVolumesRemoved:false,physicalPhoneAccessed:false,fullCcs08Complete:false};
fs.writeFileSync(path.join(out,'cleanup-proof.json'),JSON.stringify(result,null,2)+'\n');console.log(JSON.stringify({passed:true,stoppedProfiles:profiles.length,...os}));
