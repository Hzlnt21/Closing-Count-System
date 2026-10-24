// Prepare a closed-list operator payload. No dependencies, private runtime state,
// database files, Android data, keystores or source history enter this payload.
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {packageRoot,stateDirectory} from './private-state.mjs';
const sha=body=>createHash('sha256').update(body).digest('hex');
try {
 const [flag,value,...extra]=process.argv.slice(2);if(flag!=='--out'||!value||extra.length)throw Error('Use --out with a fresh absolute directory');
 const out=stateDirectory(value);if(fs.existsSync(out))throw Error('Package output already exists; refusing overwrite');
 const sources=['demo/runtime.mjs','demo/private-state.mjs','demo/manifest.v1.json','service/package.json','service/package-lock.json',
  ...['database','generation','runtime','server'].map(n=>'service/demo/'+n+'.mjs')];
 for(const dir of ['service/src','service/sql'])for(const name of fs.readdirSync(path.join(packageRoot,dir))) {
  if(name.endsWith(dir==='service/src'?'.mjs':'.sql'))sources.push(dir+'/'+name);
 }
 const head=spawnSync('git',['rev-parse','HEAD'],{cwd:packageRoot,encoding:'utf8'});
 const baseCommit=head.status===0&&/^[a-f0-9]{40}$/.test(head.stdout.trim())?head.stdout.trim():null;
 const version=JSON.parse(fs.readFileSync(path.join(packageRoot,'service/package.json'),'utf8')).version;
 fs.mkdirSync(out,{recursive:true});const files=[];let matches=!!baseCommit;
 for(const file of sources.sort()) {
  const source=path.join(packageRoot,file);if(fs.lstatSync(source).isSymbolicLink()||!fs.statSync(source).isFile())throw Error('Refusing linked/non-file package input');
  const body=fs.readFileSync(source),target=path.join(out,file);fs.mkdirSync(path.dirname(target),{recursive:true});fs.writeFileSync(target,body);
  files.push({file,sha256:sha(body),source:file});
  if(baseCommit){const committed=spawnSync('git',['show',baseCommit+':'+file],{cwd:packageRoot});if(committed.status!==0||sha(committed.stdout)!==sha(body))matches=false;}
 }
 const readme=`# Closing Count local demo candidate\n\nService ${version}. Fictional data; local loopback only. Native demo, media and complete distribution acceptance are still in progress. This operator payload does not contain Android APKs or developer credentials.\n\nRequires Node24, Docker Desktop's local Linux engine and the postgres:16 image. From this extracted directory:\n\n\x60\x60\x60text\nnpm ci --prefix service\ndocker pull postgres:16\nnode demo/runtime.mjs init\nnode demo/runtime.mjs start\nnode demo/runtime.mjs status\nnode demo/runtime.mjs access\nnode demo/runtime.mjs stop\n\x60\x60\x60\n\nThe API uses127.0.0.1:4372 and its separate database uses127.0.0.1:55437. Only one local demo instance can run on those ports at a time. Each new private identity owns distinct Docker resources. Existing version1 identities retain their original resources without data migration/deletion.\n\nPrivate identity, generated passwords and server control files live outside this directory under LOCALAPPDATA/ClosingCountDemo (or XDG state/HOME fallback). Append the same --state-dir ABSOLUTE_PRIVATE_DIRECTORY to every command to choose another private location. Never package or publish that runtime directory. The access command intentionally displays only the fictional accounts' credentials; avoid it during recording.\n\nFour roles: Rani and Dimas employees, Arif supervisor, Dewi administrator. Current business-day baseline3Cappuccino+5Latte=8portions; preceding locked history6+4=10. Asia/Jakarta,04:00 cutoff. Dates/UUIDs/credentials are resolved when the generation is first created and then persist. Recipes count one usage of each listed ingredient per portion.\n\nRepeated init/seed preserves a completed fixture and later edits. Stop/start preserves all data. The reset command creates a new fixture generation, archives its predecessor, deactivates the old accounts/outlet and revokes old sessions. Receipts, audit, catalog/report history and old device drafts are not removed or relabelled. Use access again for new accounts. An interrupted seed is resumed with seed before another reset.\n\nOwnership/marker mismatch, unexpected resources or occupied ports cause refusal. Runtime state records the matched server run; stop uses its private control request, never a guessed PID kill. A different running service version must be stopped before upgrading. Source/API test fixtures are omitted; source acceptance and later physical/public UAT are separate.\n\npackage-info.json and SHA256SUMS identify this exact candidate payload. baseCommit is a reference only when sourceMatchesBaseCommit=false; payload hashes describe the tested in-progress source accurately. This is not a cloud deployment or final recruiter package.\n`;
 fs.writeFileSync(path.join(out,'README.md'),readme);files.push({file:'README.md',sha256:sha(Buffer.from(readme)),source:null});
 const info={format:'closing-count-local-demo-package',schemaVersion:1,serviceVersion:version,baseCommit,sourceMatchesBaseCommit:matches,fictionalData:true,operatorPayloadOnly:true,nativeJourneyComplete:false,fullCcs08Complete:false,files};
 const infoBytes=Buffer.from(JSON.stringify(info,null,2)+'\n');fs.writeFileSync(path.join(out,'package-info.json'),infoBytes);
 fs.writeFileSync(path.join(out,'SHA256SUMS'),[...files,{file:'package-info.json',sha256:sha(infoBytes)}].map(r=>r.sha256+'  '+r.file).join('\n')+'\n');
 console.log(JSON.stringify({prepared:true,files:files.length+2,serviceVersion:version,sourceMatchesBaseCommit:matches,privateStateIncluded:false,fullCcs08Complete:false}));
}catch{console.error('Demo package preparation refused or failed; no runtime secrets read.');process.exitCode=1;}
