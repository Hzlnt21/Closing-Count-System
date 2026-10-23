"""Read-only cross-check of an actual Android export against its frozen server report."""
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile

p = argparse.ArgumentParser()
p.add_argument('--workbook', type=Path, required=True)
p.add_argument('--source', type=Path, required=True)
p.add_argument('--context', type=Path)
p.add_argument('--output', type=Path, required=True)
a = p.parse_args()
before = hashlib.sha256(a.workbook.read_bytes()).hexdigest()
report = json.loads(a.source.read_text(encoding='utf-8-sig'))
closing = report['closing']
context = json.loads(a.context.read_text(encoding='utf-8-sig')) if a.context else None
ns = {'s': 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
catalog = closing['catalog']
categories = {c['id']: c for c in catalog['menuCategories'] + catalog['ingredientCategories']}
entries = {e['menuId']: e for e in closing['entries']}
totals = {e['id']: e['total'] for e in closing['ingredientTotals']}
order = lambda item: (categories[item['categoryId']]['sortOrder'], item['sortOrder'], item['id'])
menus = sorted(catalog['menus'], key=order)
ingredients = sorted([i for i in catalog['ingredients'] if totals.get(i['id'], 0) > 0], key=order)

with zipfile.ZipFile(a.workbook) as z:
    assert z.testzip() is None
    assert len(z.namelist()) == 7 and len(set(z.namelist())) == 7
    assert not any('external' in n.lower() or 'vba' in n.lower() for n in z.namelist())
    for name in ['_rels/.rels', 'xl/_rels/workbook.xml.rels']:
        for relationship in ET.fromstring(z.read(name)):
            assert relationship.get('TargetMode') != 'External'
            assert '://' not in relationship.get('Target', '')
    workbook = ET.fromstring(z.read('xl/workbook.xml'))
    assert [s.get('name') for s in workbook.find('s:sheets', ns)] == ['Pemakaian Bahan', 'Penjualan Menu']
    sheets = [ET.fromstring(z.read(f'xl/worksheets/sheet{i}.xml')) for i in [1, 2]]
    maps = [{c.get('r'): c for c in s.findall('.//s:c', ns)} for s in sheets]
    def text(cells, address):
        c = cells[address]
        assert c.get('t') == 'inlineStr'
        return ''.join(c.find('s:is', ns).itertext())
    def number(cells, address):
        c = cells[address]
        assert c.get('t') is None
        return int(c.find('s:v', ns).text)
    for sheet, cells in zip(sheets, maps):
        assert sheet.find('s:sheetViews/s:sheetView', ns).get('showGridLines') == '0'
        assert sheet.find('s:sheetViews/s:sheetView/s:pane', ns).get('state') == 'frozen'
        assert sheet.find('s:pageSetup', ns).get('fitToWidth') == '1'
        assert text(cells, 'B5') == f"{report['generation']} / {report['closingRevision']}"
        actor = report['reviewedBy']
        assert text(cells, 'B6') == f"{actor['displayName']} ({actor['username']})"
        date = dt.date.fromisoformat(closing['businessDate'])
        if date.year >= 1900:
            serial = (date - dt.date(1899, 12, 31)).days + int(date >= dt.date(1900, 3, 1))
            assert number(cells, 'B4') == serial and cells['B4'].get('s') == '5'
        else:
            assert text(cells, 'B4') == closing['businessDate']
        values = [text(cells, addr) for addr, c in cells.items() if c.get('t') == 'inlineStr']
        assert report['id'] in values and report['closingId'] in values and report['outletId'] in values
        if context:
            assert text(cells, 'B3') == context['outletName']
            assert any(context['exportedBy'] in value for value in values)
        assert not sheet.findall('.//s:c[@t="e"]', ns)
    for row, ingredient in enumerate(ingredients, 10):
        cells = maps[0]
        assert text(cells, f'A{row}') == categories[ingredient['categoryId']]['name']
        assert text(cells, f'B{row}') == ingredient['name']
        assert number(cells, f'C{row}') == totals[ingredient['id']]
    for row, menu in enumerate(menus, 10):
        cells = maps[1]
        entry = entries[menu['id']]
        assert text(cells, f'A{row}') == categories[menu['categoryId']]['name']
        assert text(cells, f'B{row}') == menu['name']
        assert number(cells, f'C{row}') == entry['quantity']
        actor = entry.get('updatedBy')
        expected = f"{actor['displayName']} ({actor['username']})" if actor else ('Belum diubah' if entry['quantity'] == 0 and entry['entryRevision'] == 0 else 'Tidak tercatat')
        assert text(cells, f'D{row}') == expected
    assert not sheets[0].findall('.//s:f', ns)
    formulas = sheets[1].findall('.//s:f', ns)
    assert len(formulas) == 1
    assert formulas[0].text == (f'SUM(C10:C{9+len(menus)})' if menus else '0')
    assert number(maps[1], f'C{10+len(menus)}') == sum(e['quantity'] for e in entries.values())
    assert len([c for c in maps[0].values() if c.get('s') == '3']) == len(ingredients)
    assert len([c for c in maps[1].values() if c.get('s') == '3']) == len(menus)

assert hashlib.sha256(a.workbook.read_bytes()).hexdigest() == before
proof = {'passed': True, 'sourceUnchanged': True, 'workbookSha256': before,
         'reportId': report['id'], 'closingId': report['closingId'], 'generation': report['generation'],
         'menuRowsCompared': len(menus), 'ingredientRowsCompared': len(ingredients),
         'quantities': [e['quantity'] for e in entries.values()], 'totalPortions': sum(e['quantity'] for e in entries.values()),
         'checks': ['every data cell vs frozen catalog/entries/totals', 'literal actor attribution', 'numeric quantities/date', 'cached total/formula', 'report identity', 'no external links/macros', 'outlet context when provided', 'freeze/print structure'],
         'physicalPhoneAcceptance': False, 'publicAcceptance': False}
a.output.write_text(json.dumps(proof, indent=2), encoding='utf-8')
print(json.dumps(proof))
