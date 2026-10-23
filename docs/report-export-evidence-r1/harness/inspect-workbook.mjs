import fs from 'node:fs/promises';
import crypto from 'node:crypto';
import {FileBlob,SpreadsheetFile} from '@oai/artifact-tool';
const [input,label='jvm-sample']=process.argv.slice(2);
if(!input||!/^[a-z0-9-]{1,50}$/.test(label))throw Error('Expected owned workbook path and inspection label');
const out='D:/Code/Documents/Career-Portfolio-2026/evidence/closing-count-report-export-r1/inspection-'+label;
await fs.mkdir(out,{recursive:true});
const before=crypto.createHash('sha256').update(await fs.readFile(input)).digest('hex');
const workbook=await SpreadsheetFile.importXlsx(await FileBlob.load(input));
const overview=await workbook.inspect({kind:'sheet',include:'id,name',maxChars:2000});
await fs.writeFile(out+'/sheets.ndjson',overview.ndjson);
for(const [name,last] of [['Pemakaian Bahan','C'],['Penjualan Menu','D']]){
 const report=await workbook.inspect({kind:'table',sheetId:name,range:`A1:${last}20`,include:'values,formulas',tableMaxRows:20,tableMaxCols:4,maxChars:5000});
 await fs.writeFile(out+'/'+name.replaceAll(' ','-')+'.ndjson',report.ndjson);
 const preview=await workbook.render({sheetName:name,range:`A1:${last}20`,scale:1.5,format:'png'});
 await fs.writeFile(out+'/'+name.replaceAll(' ','-')+'.png',new Uint8Array(await preview.arrayBuffer()));
}
const after=crypto.createHash('sha256').update(await fs.readFile(input)).digest('hex');
if(before!==after)throw Error('Read-only inspection changed source bytes');
await fs.writeFile(out+'/inspection.json',JSON.stringify({imported:true,sheetsRendered:2,sourceSha256:before,sourceUnchanged:true,engine:'artifact-tool read-only import/inspect/render',visualReviewPending:true},null,2));
process.stdout.write(JSON.stringify({imported:true,sheetsRendered:2,sourceUnchanged:true,out})+'\n');
