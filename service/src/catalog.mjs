import {exactObject,invalid,isUuid} from './errors.mjs';
const max=Number.MAX_SAFE_INTEGER;
export function revision(value,min=0){if(!Number.isSafeInteger(value)||value<min)throw invalid();return value;}
export function uuid(value){if(!isUuid(value))throw invalid();return value.toLowerCase();}
function name(value){if(typeof value!=='string'||!value.trim()||value.trim().length>120)throw invalid();return value.trim();}
function sortOrder(value){if(!Number.isInteger(value)||value<0||value>1000000)throw invalid();return value;}
function active(value){if(typeof value!=='boolean')throw invalid();return value;}
function rows(value,limit,convert){
  if(!Array.isArray(value)||value.length>limit)throw invalid();
  const normalized=value.map(convert).sort((a,b)=>a.id.localeCompare(b.id));
  if(new Set(normalized.map(r=>r.id)).size!==normalized.length)throw invalid('ID katalog tidak boleh berulang.');
  return normalized;
}
function category(row){exactObject(row,['id','name','sortOrder']);return {id:uuid(row.id),name:name(row.name),sortOrder:sortOrder(row.sortOrder)};}
export function normalizeCatalog(value){
  exactObject(value,['ingredientCategories','ingredients','menuCategories','menus']);
  const ingredientCategories=rows(value.ingredientCategories,100,category),menuCategories=rows(value.menuCategories,100,category);
  const ingredients=rows(value.ingredients,1000,row=>{
    exactObject(row,['id','categoryId','name','sortOrder','active']);
    return {id:uuid(row.id),categoryId:uuid(row.categoryId),name:name(row.name),sortOrder:sortOrder(row.sortOrder),active:active(row.active)};
  });
  const menus=rows(value.menus,500,row=>{
    exactObject(row,['id','categoryId','name','sortOrder','active','ingredientIds']);
    if(!Array.isArray(row.ingredientIds)||row.ingredientIds.length<1||row.ingredientIds.length>100)throw invalid();
    const ingredientIds=row.ingredientIds.map(uuid).sort();
    if(new Set(ingredientIds).size!==ingredientIds.length)throw invalid('Bahan resep tidak boleh berulang.');
    return {id:uuid(row.id),categoryId:uuid(row.categoryId),name:name(row.name),sortOrder:sortOrder(row.sortOrder),active:active(row.active),ingredientIds};
  });
  const ingredientCategoryIds=new Set(ingredientCategories.map(r=>r.id)),menuCategoryIds=new Set(menuCategories.map(r=>r.id));
  const ingredientById=new Map(ingredients.map(r=>[r.id,r]));
  for(const row of ingredients)if(!ingredientCategoryIds.has(row.categoryId))throw invalid('Kategori bahan tidak ditemukan.');
  for(const row of menus){
    if(!menuCategoryIds.has(row.categoryId))throw invalid('Kategori menu tidak ditemukan.');
    for(const id of row.ingredientIds)if(!ingredientById.has(id)||(row.active&&!ingredientById.get(id).active))throw invalid('Resep menu aktif memerlukan bahan aktif yang terdaftar.');
  }
  return {ingredientCategories,ingredients,menuCategories,menus};
}
export const emptyCatalog=()=>({ingredientCategories:[],ingredients:[],menuCategories:[],menus:[]});
export function closingSnapshot(catalog){
  const normalized=normalizeCatalog(catalog),menus=normalized.menus.filter(row=>row.active);
  const used=new Set(menus.flatMap(row=>row.ingredientIds));
  const ingredients=normalized.ingredients.filter(row=>used.has(row.id));
  const ingredientCategoryIds=new Set(ingredients.map(row=>row.categoryId)),menuCategoryIds=new Set(menus.map(row=>row.categoryId));
  return {ingredientCategories:normalized.ingredientCategories.filter(row=>ingredientCategoryIds.has(row.id)),ingredients,
    menuCategories:normalized.menuCategories.filter(row=>menuCategoryIds.has(row.id)),menus};
}
export function ingredientTotals(snapshot,entries){
  const quantities=new Map(entries.map(row=>[row.menuId,row.quantity])),totals=new Map();
  if(entries.length!==snapshot.menus.length||quantities.size!==entries.length||snapshot.menus.some(row=>!quantities.has(row.id)))throw new Error('Stored entries do not match frozen menus');
  for(const menu of snapshot.menus){
    const quantity=quantities.get(menu.id)??0;
    if(!Number.isInteger(quantity)||quantity<0||quantity>9999)throw new Error('Invalid stored closing quantity');
    for(const id of menu.ingredientIds)totals.set(id,(totals.get(id)??0n)+BigInt(quantity));
  }
  return snapshot.ingredients.map(row=>{
    const total=totals.get(row.id)??0n;if(total>BigInt(max))throw new Error('Ingredient total exceeds supported export range');
    return {...row,total:Number(total)};
  }).filter(row=>row.total>0).sort((a,b)=>a.sortOrder-b.sortOrder||a.id.localeCompare(b.id));
}
export function businessDateInput(value){
  if(typeof value!=='string'||!/^\d{4}-\d{2}-\d{2}$/.test(value)||value.startsWith('0000'))throw invalid();
  const date=new Date(value+'T12:00:00Z');
  if(!Number.isFinite(date.getTime())||date.toISOString().slice(0,10)!==value)throw invalid('Tanggal bisnis tidak valid.');
  return value;
}
