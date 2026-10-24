"""Independent read-only oracle over collected native/PG/SAF artifacts."""
from pathlib import Path
import hashlib,json,sqlite3,xml.etree.ElementTree as ET,zipfile,re,sys
attempt=sys.argv[1] if len(sys.argv)==2 else 'r1';assert re.fullmatch(r'r[1-9]',attempt)
out=Path('D:/Code/Documents/Career-Portfolio-2026/evidence')/('closing-count-demo-native-'+attempt)
load=lambda name:json.loads((out/name).read_text(encoding='utf8'))
verification=load('verification.json');assert verification['passed'] and len(verification['phases'])==9
server=load('server-snapshot.json');old,new=server['identities'];ids=old['ids'];nextids=new['ids']
assert [(g['number'],g['status']) for g in server['generations']]==[(1,'ARCHIVED'),(2,'READY')]
def closing(outlet,date):return next(c for c in server['closings'] if c['outlet_id']==outlet and c['date']==date)
first=load('rani-prime/rani-prime-proof.json');closing_id=first['closing']
current=next(c for c in server['closings'] if c['id']==closing_id)
assert (current['state'],int(current['generation']),int(current['revision']))==('DRAFT',2,10)
def quantities(key):return {r['menu_id']:r['quantity'] for r in server['entries'] if r['closing_id']==key}
assert quantities(closing_id)=={ids['cappuccino']:9,ids['latte']:6}
newclosing=next(c for c in server['closings'] if c['outlet_id']==new['outlet'] and c['state']=='DRAFT')
assert quantities(newclosing['id'])=={nextids['cappuccino']:3,nextids['latte']:5}
report=load('supervisor/supervisor-frozen-report.json')
assert (int(report['generation']),int(report['closingRevision']))==(1,8)
assert {e['menuId']:e['quantity'] for e in report['closing']['entries']}=={ids['cappuccino']:8,ids['latte']:6}
assert {e['id']:e['total'] for e in report['closing']['ingredientTotals']}=={ids['coffee']:14,ids['milk']:14,ids['cocoa']:8}
assert report['reviewedBy']['id']==old['actors']['arif']['id']
stored=next(r for r in server['reports'] if r['closing_id']==closing_id)
# JSON object equality is intentional: PostgreSQL jsonb canonicalizes key order.
assert stored['snapshot']==report
catalogs=[c for c in server['catalogs'] if c['outlet_id']==old['outlet']]
assert [int(c['revision']) for c in catalogs]==[1,2]
fresh=next(m for m in catalogs[1]['snapshot']['menus'] if m['id']==ids['cappuccino'])
assert fresh['name']=='Cappuccino Baru' and ids['cocoa'] not in fresh['ingredientIds']
frozen=next(m for m in current['snapshot']['menus'] if m['id']==ids['cappuccino'])
assert frozen['name']=='Cappuccino' and ids['cocoa'] in frozen['ingredientIds']
def db(phase):
    connection=sqlite3.connect((out/phase/'room/closing_count.db').as_uri()+'?mode=ro',uri=True)
    connection.row_factory=sqlite3.Row
    assert connection.execute('PRAGMA integrity_check').fetchone()[0]=='ok'
    return connection
def queue(phase):
    with db(phase) as c:return [dict(r) for r in c.execute('SELECT * FROM online_operations')]
rani=load('rani-offline/rani-offline-proof.json')['offlineOperation']
dimas=load('dimas-offline/dimas-offline-proof.json')['offlineOperation']
assert (int(rani['quantity']),int(rani['expectedRevision']),int(rani['generation']))==(8,2,1)
assert (int(dimas['quantity']),int(dimas['expectedRevision']),int(dimas['generation']))==(9,2,1)
receipt_ids={r['operation_id'] for r in server['receipts']}
assert rani['operationId'] not in receipt_ids and dimas['operationId'] not in receipt_ids
after=queue('after-reset');assert len(after)==1
assert after[0]['operationId']==dimas['operationId'] and after[0]['scope']==dimas['scope'] and after[0]['quantity']==9
assert after[0]['status']=='AUTH_REQUIRED'
backup=load('after-reset/after-reset-pending-backup.json')
assert backup['format']=='closing-count-online-pending-backup'
assert backup['account']['id']==old['actors']['dimas']['id'] and backup['outlet']['id']==old['outlet']
assert backup['origin']=='http://10.0.2.2:4372'
assert backup['closings'][0]['operations'][0]['operationId']==dimas['operationId']
assert not any(k in json.dumps(backup).lower() for k in ['password','accesstoken','refreshtoken','authorization'])
with zipfile.ZipFile(out/'supervisor/supervisor-report.xlsx') as z:
    assert z.testzip() is None
    ns={'m':'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
    def cells(index):
        root=ET.fromstring(z.read(f'xl/worksheets/sheet{index}.xml'))
        return {c.attrib['r']:''.join(c.itertext()) for c in root.findall('.//m:c',ns)}
    ingredient,menu=cells(1),cells(2)
    menurows={menu['B'+str(r)]:menu['C'+str(r)] for r in (10,11)}
    assert menurows=={'Cappuccino':'8','Latte':'6'}
    assert menu['C12'].endswith('14') and menu['B5']=='1 / 8'
    ingredientrows={ingredient['B'+str(r)]:ingredient['C'+str(r)] for r in (10,11,12)}
    assert ingredientrows=={'Biji kopi':'14','Fresh milk':'14','Powder cokelat':'8'}
actions=[r['action'] for r in server['audits'] if r['closing_id']==closing_id]
assert actions==['CREATE']+['ENTRY_UPDATED']*6+['SUBMIT','LOCK','REOPEN','ENTRY_UPDATED']
assert (out/'owner-before.sha256').read_bytes()==(out/'owner-after.sha256').read_bytes()
result=dict(passed=True,nativePhases=9,twoIsolatedInstallations=True,serverRevision=10,serverGeneration=2,
    lockedReportQuantities=[8,6],currentQuantities=[9,6],freshResetQuantities=[3,5],
    staleDraftPreserved=True,oldIdsNotReplayed=True,realSafWorkbookAndBackupChecked=True,
    physicalPhoneAccessed=False,publicAcceptance=False,completeCcs08=False)
(out/'independent-oracle.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
print(json.dumps(result))
