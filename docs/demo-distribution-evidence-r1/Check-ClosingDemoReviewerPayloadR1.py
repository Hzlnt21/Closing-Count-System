from pathlib import Path
from html.parser import HTMLParser
from urllib.parse import unquote,urlsplit
import argparse,hashlib,json,os,re,zipfile
parser=argparse.ArgumentParser();parser.add_argument('directory');parser.add_argument('--report',required=True);parser.add_argument('--zip')
args=parser.parse_args();root=Path(args.directory).resolve();report=Path(args.report);assert not report.exists()
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
sha=lambda b:hashlib.sha256(b).hexdigest()
info=read(root/'package-info.json');assert info['format']=='closing-count-local-reviewer-package'
assert info['packageId']=='com.closingcount.app.demo' and info['androidVersion']=='0.1.2-demo-local'
files={r['file']:r for r in info['files']};assert len(files)==len(info['files'])
for name,row in files.items():
    file=(root/name).resolve();assert file.is_relative_to(root) and file.is_file() and not (root/name).is_symlink()
    assert not any(part in ['node_modules','.git','.gradle','build'] for part in Path(name).parts)
    assert not name.endswith(('.db','.private.json','.private.log','.keystore')) and not name.endswith('local.properties')
    assert sha(file.read_bytes())==row['sha256']
actual={p.relative_to(root).as_posix() for p in root.rglob('*') if p.is_file()}
assert actual==set(files)|{'package-info.json','SHA256SUMS'},'Pristine payload contains unexpected files'
for line in (root/'SHA256SUMS').read_text(encoding='utf8').splitlines():
    digest,name=line.split('  ',1);assert sha((root/name).read_bytes())==digest
private=Path('D:/Code/ToolCache/PortfolioQA');values=[]
qa=read(Path(os.environ['LOCALAPPDATA'])/'PortfolioQA/secrets/closing-count-local-r1.json')
values += [qa[k] for k in ['instance','adminPassword','appPassword']]
dirs=[private/name for name in ['closing-count-demo-r1','Portable Demo Profile R1/ClosingCountDemo','Crash Demo Profile R1/ClosingCountDemo','Locked Crash Demo Profile R1/ClosingCountDemo']]+list(private.glob('Native Demo R*/ClosingCountDemo'))
for directory in dirs:
    identity=read(directory/'instance.private.json');values += [identity[k] for k in ['instance','ownerPassword','appPassword']]
    for file in directory.glob('generation-*.private.json'):values += [a['password'] for a in read(file)['actors'].values()]
for name in actual:
    body=(root/name).read_bytes();assert not any(v.encode() in body for v in values),name
apk=root/'android/Closing-Count-0.1.2-demo-local.apk';assert sha(apk.read_bytes())==info['apkSha256']
with zipfile.ZipFile(apk) as z:
    assert z.testzip() is None
    for name in z.namelist():assert not any(v.encode() in z.read(name) for v in values),name
links=[]
class Links(HTMLParser):
    def handle_starttag(self,tag,attrs):
        links.extend(v for k,v in attrs if k in ['href','src'] and v)
html=root/'media/review.html';Links().feed(html.read_text(encoding='utf8'))
checks=[(html.parent,link) for link in links]
for file in (root/'guides').glob('*.md'):checks += [(file.parent,link) for link in re.findall(r'\]\(([^)]+)\)',file.read_text(encoding='utf8'))]
for parent,link in checks:
    parsed=urlsplit(link)
    if parsed.scheme or not parsed.path:continue
    target=(parent/unquote(parsed.path)).resolve();assert target.is_relative_to(root) and target.is_file(),link
tested=Path('D:/Code/ToolCache/PortfolioQA/Closing Count Reviewer R1')
same=True
for name,row in files.items():
    if name.startswith(('android-source/','operator/')):same &= (root/name).read_bytes()==(tested/name).read_bytes()
assert same,'Final payload differs from tested extracted Android/operator source'
archive=None
if args.zip:
    target=Path(args.zip).resolve();assert target.parent==root.parent and not target.exists()
    with zipfile.ZipFile(target,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for name in sorted(actual):z.write(root/name,name)
    with zipfile.ZipFile(target) as z:
        assert z.testzip() is None and set(z.namelist())==actual
        for name in actual:assert z.read(name)==(root/name).read_bytes()
    archive=dict(path=str(target),sha256=sha(target.read_bytes()),bytes=target.stat().st_size,entries=len(actual),allEntryBytesMatched=True)
proof=dict(passed=True,baseCommit=info['baseCommit'],sourceMatchesBaseCommit=info['sourceMatchesBaseCommit'],files=len(actual),allPayloadHashesMatched=True,knownPrivateValuesAbsent=True,privateProfilesChecked=len(dirs),apkContentsScanned=True,installedDependenciesAndBuildOutputsAbsent=True,localGuideAndGalleryTargetsExist=True,sameAndroidAndOperatorSourceAsExtractedTests=True,apkSha256=info['apkSha256'],archive=archive,browserPlaybackVerified=False,physicalAcceptance=False,publicAcceptance=False)
report.write_text(json.dumps(proof,indent=2)+'\n',encoding='utf8');print(json.dumps(proof))
