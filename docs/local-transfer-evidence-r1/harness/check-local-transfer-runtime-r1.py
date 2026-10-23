from pathlib import Path
import json,hashlib,re
root=Path('D:/Code/Documents/Career-Portfolio-2026/evidence/closing-count-local-transfer-ui-r1')
source=Path('D:/Code/Worktrees/Closing-Count-Portfolio')
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(b):return hashlib.sha256(b).hexdigest()
apks=[source/'app/build/outputs/apk/debug/app-debug.apk',source/'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']
hashes=[sha(p.read_bytes()).upper() for p in apks]
dates=['2024-02-29','2026-01-01','2026-10-23']
expected={
 'metadata':[],
 'ingredientCategories':[dict(id=1,name='Bahan QA',sortOrder=1,isActive=True)],
 'ingredients':[dict(id=51,categoryId=1,name='Susu QA',sortOrder=1,isActive=True),dict(id=52,categoryId=1,name='Kopi QA',sortOrder=2,isActive=True)],
 'menuCategories':[dict(id=1,name='Menu QA',sortOrder=1,isActive=True)],
 'menus':[dict(id=41,categoryId=1,name='Menu dari backup',sortOrder=1,isActive=True)],
 'menuIngredients':[dict(menuId=41,ingredientId=51)],
 'closings':[dict(id=101+i,date=date,updatedAt=123) for i,date in enumerate(dates)],
 'closingMenuEntries':[dict(closingId=i,menuId=41,menuName='Menu dari backup',menuSortOrder=1,menuCategoryId=1,menuCategoryName='Menu QA',menuCategorySortOrder=1,quantity=2) for i in range(101,104)],
 'closingIngredientResults':[dict(closingId=i,ingredientId=51,ingredientName='Susu QA',ingredientCategoryId=1,ingredientCategoryName='Bahan QA',ingredientCategorySortOrder=1,ingredientSortOrder=1,total=2) for i in range(101,104)],
 'closingMenuRecipes':[dict(closingId=i,menuId=41,ingredientId=51,ingredientName='Susu QA',ingredientSortOrder=1,ingredientCategoryId=1,ingredientCategoryName='Bahan QA',ingredientCategorySortOrder=1) for i in range(101,104)],
}
steps=['five-destinations-and-history-home-navigation','system-picker-cancel-keeps-local-data','invalid-provider-file-rejected','review-cancel-keeps-local-data','activity-recreation-keeps-reviewed-identity','applies-original-reviewed-content-online-tables-unchanged','system-create-document-backup-roundtrip']
proofs=[]
for code,mode,count,font,dark in [('engine','Engine',4,1,False),('legacy','Legacy',10,1,False),('phone-light','Native',1,1,False),('phone-dark-large','Native',1,2,True)]:
 directory=root/code;v=read(directory/'verification.json')
 assert v['passed'] and v['deviceSettingsRestored'] and v['mode']==mode and v['case']==code
 assert read(directory/'device-baseline.json')==read(directory/'device-restored.json')
 assert [r['Hash'] for r in read(directory/'apk-inputs.json')]==hashes
 b=(directory/'instrumentation.log').read_bytes();log=b.decode('utf-16' if b[:2] in [b'\xff\xfe',b'\xfe\xff'] else 'utf-8-sig')
 assert f'OK ({count} test'+('s' if count!=1 else '')+')' in log
 assert not re.search(r'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=|Process crashed|AssumptionViolated|STATUS_CODE: -3',log)
 item=dict(case=code,passed=True,tests=count,deviceSettingsRestored=True)
 if mode=='Native':
  native=read(directory/'native-proof.json');assert native['passed'] and native['originalLocalDataRestored']
  assert native['case']==code and abs(native['fontScale']-font)<0.03 and native['dark']==dark
  assert abs(native['widthDp']-360)<=8 and abs(native['heightDp']-800)<=8
  assert native['steps']==steps
  assert [c['scene'] for c in native['layouts']]==['home','review']
  for c in native['layouts']:assert c['measuredStaticTexts']>0 and c['overflow']==[]
  assert native['onlineBeforeSha256']==native['onlineAfterSha256'] and re.fullmatch('[a-f0-9]{64}',native['onlineBeforeSha256'])
  data=(directory/'exported-backup.json').read_bytes();assert sha(data)==native['exportSha256']
  backup=json.loads(data);assert backup['format']=='closing-count-system-backup' and backup['schemaVersion']==1
  assert isinstance(backup['createdAt'],int) and backup['createdAt']>123 and backup['data']==expected
  screenshots=[]
  for scene in ['home','picker','invalid','review','restored','backup-written']:
   image=(directory/(scene+'.png')).read_bytes();assert image.startswith(b'\x89PNG\r\n\x1a\n')
   screenshots.append(dict(scene=scene,sha256=sha(image)))
  item.update(nativeSteps=7,paintedTextChecks=2,exportRoundTrip=True,originalLocalDataRestored=True,onlineTableDigestsUnchanged=True,screenshots=screenshots)
 proofs.append(item)
result=dict(passed=True,apkSha256=hashes,groups=proofs,androidTests=16,nativeJourneys=2,nativeSteps=14,paintedTextChecks=4,scope='Owned emulator DocumentsUI/local MediaStore/Room only; no physical storage provider or online server transfer')
(root/'runtime-proof.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
print(json.dumps(dict(passed=True,androidTests=16,nativeJourneys=2,exactJsonExports=2,allCurrentApksMatch=True)))
