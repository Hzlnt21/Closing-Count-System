// Local owner/reviewer payload: closed-list source, isolated demo APK and media.
// Private state, credentials, .git, caches and installed dependencies are omitted.
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {packageRoot,stateDirectory} from './private-state.mjs';
const sha=body=>createHash('sha256').update(body).digest('hex');
try {
 const [flag,value,...rest]=process.argv.slice(2);
 if(flag!=='--out'||!value||rest.length)throw Error('Use --out with a fresh absolute directory');
 const out=stateDirectory(value);if(fs.existsSync(out))throw Error('Output already exists');
 const media='docs/demo-media-r1';
 const apk=path.join(packageRoot,'app/build/outputs/apk/debug/app-debug.apk');
 const info=JSON.parse(fs.readFileSync(path.join(packageRoot,media,'apk-info.json'),'utf8'));
 const video=JSON.parse(fs.readFileSync(path.join(packageRoot,media,'media-info.json'),'utf8'));
 const metadata=JSON.parse(fs.readFileSync(path.join(packageRoot,'app/build/outputs/apk/debug/output-metadata.json'),'utf8'));
 if(!info.passed||info.packageId!=='com.closingcount.app.demo'||info.version!=='0.1.2-demo-local'||info.endpoint!=='http://10.0.2.2:4372'||sha(fs.readFileSync(apk))!==info.sha256)throw Error('Demo APK provenance mismatch');
 if(metadata.applicationId!==info.packageId||metadata.elements[0].versionName!==info.version||!video.passed||!video.realTimeNativeScreenRecording)throw Error('Unverified variant/media');
 const git=args=>{const result=spawnSync('git',args,{cwd:packageRoot});if(result.status!==0)throw Error('Source inventory unavailable');return result.stdout;};
 const baseCommit=git(['rev-parse','HEAD']).toString().trim();if(!/^[a-f0-9]{40}$/.test(baseCommit))throw Error('Invalid source reference');
 const android=git(['ls-files','-z','--','build.gradle.kts','settings.gradle.kts','gradle.properties','gradlew','gradlew.bat','gradle','app/build.gradle.kts','app/proguard-rules.pro','app/src/main','app/src/debug']).toString().split('\0').filter(Boolean);
 if(!android.includes('app/src/main/AndroidManifest.xml')||!android.includes('gradle/wrapper/gradle-wrapper.jar'))throw Error('Incomplete source inventory');
 fs.mkdirSync(out,{recursive:true});
 const operator=spawnSync(process.execPath,[path.join(packageRoot,'demo/build-package.mjs'),'--out',path.join(out,'operator')],{cwd:packageRoot,encoding:'utf8'});
 if(operator.status!==0)throw Error('Operator payload failed');
 const records=[];let sourceMatchesBaseCommit=true;
 function copy(source,target) {
  const file=path.join(packageRoot,source);if(fs.lstatSync(file).isSymbolicLink()||!fs.statSync(file).isFile()||!fs.realpathSync(file).startsWith(packageRoot+path.sep))throw Error('Refusing linked/non-file source');
  const sourceBody=fs.readFileSync(file);if(sourceBody.length>80*1024*1024)throw Error('Unexpected artifact size');
  let body=sourceBody;
  if(target.startsWith('guides/')) {
   const localGuides=new Set(['DEMO_LOCAL.md','DEMO_SCENARIOS.md','DEMO_DISTRIBUTION_R1.md']);
   body=Buffer.from(sourceBody.toString('utf8').replace(/\]\(([^)]+)\)/g,(whole,href)=>{
    if(/^(https?:|#)/.test(href)||localGuides.has(href))return whole;
    const linked=path.posix.normalize(path.posix.join(path.posix.dirname(source),href));
    return '](https://github.com/Hzlnt21/Closing-Count-System/blob/'+baseCommit+'/'+linked+')';
   }));
  }
  if(target==='media/review.html')body=Buffer.from(sourceBody.toString('utf8').replaceAll('../DEMO_SCENARIOS.md','../guides/DEMO_SCENARIOS.md').replaceAll('../DEMO_DISTRIBUTION_R1.md','../guides/DEMO_DISTRIBUTION_R1.md'));
  const destination=path.join(out,target);fs.mkdirSync(path.dirname(destination),{recursive:true});fs.writeFileSync(destination,body);
  records.push({file:target,source,sha256:sha(body),sourceSha256:sha(sourceBody)});
  if(!source.startsWith('app/build/')){const original=spawnSync('git',['show',baseCommit+':'+source],{cwd:packageRoot});if(original.status!==0||sha(original.stdout)!==sha(sourceBody))sourceMatchesBaseCommit=false;}
 }
 for(const source of android)copy(source,'android-source/'+source);
 copy('app/build/outputs/apk/debug/app-debug.apk','android/Closing-Count-0.1.2-demo-local.apk');
 for(const name of ['presentation.mp4','input.png','review.png','report-menus.png','report-ingredients.png','apk-info.json','media-info.json','review.html'])copy(media+'/'+name,'media/'+name);
 for(const name of ['DEMO_LOCAL.md','DEMO_SCENARIOS.md','DEMO_DISTRIBUTION_R1.md'])copy('docs/'+name,'guides/'+name);
 const readme=`# Closing Count — local demo candidate\n\nAndroid ${info.version}, package ${info.packageId}. Fictional data; a local emulator sandbox, not a public recruiter deployment. Physical HP and public HTTPS/APK acceptance remain later gates.\n\n1. Read guides/DEMO_DISTRIBUTION_R1.md. Requires Node24, Docker Desktop Linux engine/postgres:16, Android SDK and a disposable emulator.\n2. In operator/: npm ci --prefix service, then node demo/runtime.mjs init and node demo/runtime.mjs start. Use the same optional --state-dir ABSOLUTE_PRIVATE_DIRECTORY on every runtime command.\n3. In a private terminal, node demo/runtime.mjs access displays generated fictional account passwords. Do not record/share those credentials.\n4. Install android/Closing-Count-0.1.2-demo-local.apk in a disposable emulator using its explicit serial. It uses10.0.2.2:4372. The .demo package is separate from the main application.\n5. Follow guides/DEMO_SCENARIOS.md; watch media/presentation.mp4 for the actual input/review/frozen-report navigation. This video is a read-only presentation, not a recording of all nine domain phases.\n6. From operator/, node demo/runtime.mjs stop retains data. Reset archives the old fixture generation and revokes access without deleting history or Android drafts. Review/export old pending work before switching accounts.\n\nBuild Android source, if needed, from android-source/ with .\\gradlew.bat assembleDebug -PclosingLocalDemo=true. Configure a local JDK/Android SDK; no local.properties, keystore or installed dependencies are shipped. Downloading locked dependencies/Gradle/SDK requires internet on first setup. The APK is debug-signed and is not a final public APK.\n\nbaseCommit=${baseCommit}. sourceMatchesBaseCommit=${sourceMatchesBaseCommit} describes this exact copied source before Git line-ending conversion. SHA256SUMS is authoritative for payload bytes. Source reference: https://github.com/Hzlnt21/Closing-Count-System (existing access rules apply). Never include private runtime state, tokens, .git, databases or operational files.\n`;
 fs.writeFileSync(path.join(out,'README.md'),readme);records.push({file:'README.md',source:null,sha256:sha(Buffer.from(readme))});
 function walk(dir,prefix=''){for(const name of fs.readdirSync(dir)){const file=path.join(dir,name),relative=prefix+name;if(fs.statSync(file).isDirectory())walk(file,relative+'/');else if(!records.some(r=>r.file===relative))records.push({file:relative,source:null,sha256:sha(fs.readFileSync(file))});}}
 walk(path.join(out,'operator'),'operator/');
 const manifest={format:'closing-count-local-reviewer-package',schemaVersion:1,baseCommit,sourceMatchesBaseCommit,guideEvidenceLinksUseSourceReference:true,androidVersion:info.version,packageId:info.packageId,apkSha256:info.sha256,fictionalData:true,physicalAcceptance:false,publicAcceptance:false,files:records.sort((a,b)=>a.file.localeCompare(b.file))};
 const bytes=Buffer.from(JSON.stringify(manifest,null,2)+'\n');fs.writeFileSync(path.join(out,'package-info.json'),bytes);
 fs.writeFileSync(path.join(out,'SHA256SUMS'),[...manifest.files,{file:'package-info.json',sha256:sha(bytes)}].map(r=>r.sha256+'  '+r.file).join('\n')+'\n');
 console.log(JSON.stringify({prepared:true,files:records.length+2,baseCommit,sourceMatchesBaseCommit,apkSha256:info.sha256,privateStateIncluded:false,publicAcceptance:false}));
}catch{console.error('Local reviewer package refused or failed; no runtime secrets read.');process.exitCode=1;}
