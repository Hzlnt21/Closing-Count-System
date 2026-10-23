from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
root=Path('D:/Code/Documents/Career-Portfolio-2026/evidence/closing-count-report-export-r1')
font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',16)
def sheet(items,name,cols,width):
    tiles=[]
    for label,path in items:
        im=Image.open(path).convert('RGB');im.thumbnail((width,width*3))
        tile=Image.new('RGB',(width,im.height+32),'#ffffff')
        ImageDraw.Draw(tile).text((6,6),label,fill='#142e43',font=font)
        tile.paste(im,(0,32));tiles.append(tile)
    rows=(len(tiles)+cols-1)//cols
    heights=[max(t.height for t in tiles[row*cols:(row+1)*cols]) for row in range(rows)]
    canvas=Image.new('RGB',(cols*width,sum(heights)),'#dce8f0');y=0
    for row,height in enumerate(heights):
        for col,tile in enumerate(tiles[row*cols:(row+1)*cols]):canvas.paste(tile,(col*width,y))
        y+=height
    canvas.save(root/name)
items=[]
for code in ['light-stable-final','dark-stable-final']:
    for scene in ['frozen-four','picker','saved','expired-access']:
        items.append((code.replace('-stable-final','')+' / '+scene,root/code/f'report-export-{code}-{scene}.png'))
sheet(items,'native-contact-sheet.png',4,300)
items=[(label+' / '+tab,root/f'inspection-{label}-release'/f'{tab}.png') for label in ['http','light','dark'] for tab in ['Pemakaian-Bahan','Penjualan-Menu']]
sheet(items,'workbook-contact-sheet.png',2,700)
items=[(scene,root/'history-native'/f'{scene}.png') for scene in ['server-list','detail','frozen-report','audit-reopen','current-six','expired-session','session-recovered']]
sheet(items,'history-contact-sheet.png',4,300)
print('Three QA contact sheets generated; original images preserved.')
