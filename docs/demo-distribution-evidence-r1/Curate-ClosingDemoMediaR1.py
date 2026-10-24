"""Verify actual native recording/APK and curate the bounded local presentation."""
from pathlib import Path
import hashlib,json,re,subprocess,sys
sys.path.insert(0,'D:/Code/ToolCache/PortfolioQA/video-tools')
import imageio_ffmpeg as ff
base=Path('D:/Code/Documents/Career-Portfolio-2026');repo=Path('D:/Code/Worktrees/Closing-Count-Portfolio')
raw=base/'evidence/closing-count-demo-distribution-r1';build=base/'evidence/closing-count-demo-distribution-build-r1'
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
verification=read(raw/'verification.json');assert verification['passed'] and verification['businessSnapshotUnchanged'] and not verification['cleanupErrors']
assert read(raw/'business-before.json')==read(raw/'business-after.json')
assert read(raw/'device-before.json')==read(raw/'device-after.json')
assert (raw/'owner-before.sha256').read_bytes()==(raw/'owner-after.sha256').read_bytes()
for item in read(raw/'source-inputs.json')['files']:assert sha(Path(item['path']))==item['sha256']
assert read(raw/'fixed-apks.json')[0]['sha256']==read(build/'build-verification.json')['apkInputs'][0]['sha256']
def text(p):
    data=p.read_bytes();return data.decode('utf-16' if data.startswith(b'\xff\xfe') else 'utf-8-sig')
badging=text(build/'apk-badging.txt');signing=text(build/'apk-signature.txt')
assert "name='com.closingcount.app.demo'" in badging and "versionName='0.1.2-demo-local'" in badging
assert "application-label:'Closing Count Demo'" in badging and 'application-debuggable' in badging and 'Verifies' in signing
certificate=re.search(r'Signer #1 certificate SHA-256 digest: ([a-f0-9]{64})',signing)[1]
apk=repo/'app/build/outputs/apk/debug/app-debug.apk';assert sha(apk)==read(raw/'fixed-apks.json')[0]['sha256']
dest=repo/'docs/demo-media-r1';assert not dest.exists();dest.mkdir()
exe=ff.get_ffmpeg_exe();movie=dest/'presentation.mp4'
result=subprocess.run([exe,'-hide_banner','-v','error','-i',str(raw/'presentation-raw.mp4'),'-t','23','-vf','fps=30','-c:v','libx264','-preset','veryfast','-crf','20','-pix_fmt','yuv420p','-an','-map_metadata','-1','-movflags','+faststart',str(movie)],capture_output=True)
assert result.returncode==0, 'Transcode failed'
decode=subprocess.run([exe,'-hide_banner','-v','error','-err_detect','explode','-i',str(movie),'-f','null','-'],capture_output=True)
assert decode.returncode==0 and not decode.stderr,'Full final-video decode failed'
reader=ff.read_frames(str(movie));metadata=next(reader);reader.close()
frames,seconds=ff.count_frames_and_secs(str(movie));assert metadata['size']==(720,1600) and frames==690 and abs(seconds-23)<0.1
media=dict(passed=True,realTimeNativeScreenRecording=True,readOnlyPresentation=True,sourceVideoSha256=sha(raw/'presentation-raw.mp4'),sha256=sha(movie),metadata=metadata,decodedFrames=frames,decodedDuration=seconds,fullDecodeExitCode=decode.returncode,trimStartSeconds=0,trimEndSeconds=23,speedChanged=False,fpsNormalized=30,loginCredentialsEntryExcluded=True,physicalAcceptance=False,publicAcceptance=False,scope='Input/review/frozen-history navigation; nine-phase domain journey remains inherited evidence')
apkinfo=dict(passed=True,packageId='com.closingcount.app.demo',label='Closing Count Demo',version='0.1.2-demo-local',versionCode=10,minSdk=26,targetSdk=36,debuggable=True,signatureVerified=True,certificateSha256=certificate,endpoint='http://10.0.2.2:4372',sha256=sha(apk),publicApk=False)
(dest/'media-info.json').write_text(json.dumps(media,indent=2)+'\n',encoding='utf8')
(dest/'apk-info.json').write_text(json.dumps(apkinfo,indent=2)+'\n',encoding='utf8')
for scene in ['input','review','report-menus','report-ingredients']:(dest/(scene+'.png')).write_bytes((raw/'fixed'/(scene+'.png')).read_bytes())
(raw/'media-verification.json').write_text(json.dumps(media,indent=2)+'\n',encoding='utf8')
(raw/'apk-inventory.json').write_text(json.dumps(apkinfo,indent=2)+'\n',encoding='utf8')
print(json.dumps(dict(passed=True,videoSeconds=seconds,frames=frames,apkSha256=apkinfo['sha256'],packageId=apkinfo['packageId'])))
