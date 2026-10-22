import {randomUUID} from 'node:crypto';
export function catalogFixture(){
  const ids={ingredientCategory:randomUUID(),menuCategory:randomUUID(),coffee:randomUUID(),milk:randomUUID(),cocoa:randomUUID(),cappuccino:randomUUID(),latte:randomUUID()};
  const catalog={ingredientCategories:[{id:ids.ingredientCategory,name:'Bahan baku',sortOrder:0}],
    menuCategories:[{id:ids.menuCategory,name:'Coffee',sortOrder:0}],
    ingredients:[['coffee','Biji kopi'],['milk','Fresh milk'],['cocoa','Powder cokelat']].map(([key,name],sortOrder)=>({id:ids[key],categoryId:ids.ingredientCategory,name,sortOrder,active:true})),
    menus:[{id:ids.cappuccino,categoryId:ids.menuCategory,name:'Cappuccino',sortOrder:0,active:true,ingredientIds:[ids.coffee,ids.milk,ids.cocoa]},
      {id:ids.latte,categoryId:ids.menuCategory,name:'Latte',sortOrder:1,active:true,ingredientIds:[ids.coffee,ids.milk]}]};
  return {ids,catalog};
}
