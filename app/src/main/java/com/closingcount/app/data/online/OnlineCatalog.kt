package com.closingcount.app.data.online

import org.json.JSONArray
import org.json.JSONObject

data class CatalogCategory(val id:String,val name:String,val sortOrder:Int)
data class CatalogIngredient(val id:String,val categoryId:String,val name:String,val sortOrder:Int,val active:Boolean)
data class CatalogMenu(val id:String,val categoryId:String,val name:String,val sortOrder:Int,val active:Boolean,val ingredientIds:List<String>)
data class OnlineCatalog(val ingredientCategories:List<CatalogCategory>,val ingredients:List<CatalogIngredient>,
    val menuCategories:List<CatalogCategory>,val menus:List<CatalogMenu>){
    /** Presentation order is separate from the UUID-canonical snapshot payload. */
    fun menusInDisplayOrder():List<CatalogMenu>{
        val categories=menuCategories.associate{it.id to it.sortOrder}
        return menus.sortedWith(compareBy({categories.getValue(it.categoryId)},{it.sortOrder},{it.id}))
    }
    fun ingredientsInDisplayOrder():List<CatalogIngredient>{
        val categories=ingredientCategories.associate{it.id to it.sortOrder}
        return ingredients.sortedWith(compareBy({categories.getValue(it.categoryId)},{it.sortOrder},{it.id}))
    }
    /** Match the server's bounded, whole-catalog validation. Never rewrite UUIDs or recipe meaning. */
    fun validated():OnlineCatalog{
        fun name(value:String)=value.trim().also{require(it.length in 1..120){"Nama harus berisi 1 sampai 120 karakter."}}
        fun order(value:Int)=value.also{require(it in 0..1000000){"Urutan harus bilangan bulat 0 sampai 1000000."}}
        fun <T> ids(rows:List<T>,limit:Int,id:(T)->String){require(rows.size<=limit){"Batas jumlah data katalog terlampaui."};rows.forEach{OnlineApi.uuid(id(it))};require(rows.map(id).distinct().size==rows.size){"ID katalog tidak boleh berulang."}}
        ids(ingredientCategories,100){it.id};ids(menuCategories,100){it.id};ids(ingredients,1000){it.id};ids(menus,500){it.id}
        val ic=ingredientCategories.map{it.copy(name=name(it.name),sortOrder=order(it.sortOrder))}.sortedBy{it.id}
        val mc=menuCategories.map{it.copy(name=name(it.name),sortOrder=order(it.sortOrder))}.sortedBy{it.id}
        val i=ingredients.map{row->require(ic.any{it.id==row.categoryId}){"Kategori bahan tidak ditemukan."};row.copy(name=name(row.name),sortOrder=order(row.sortOrder))}.sortedBy{it.id}
        val byId=i.associateBy{it.id}
        val m=menus.map{row->
            require(mc.any{it.id==row.categoryId}){"Kategori menu tidak ditemukan."}
            require(row.ingredientIds.size in 1..100&&row.ingredientIds.distinct().size==row.ingredientIds.size){"Pilih 1 sampai 100 bahan yang berbeda untuk resep."}
            row.ingredientIds.forEach{id->require(byId[id]!=null&&(!row.active||byId.getValue(id).active)){"Menu aktif harus memakai bahan aktif yang terdaftar."}}
            row.copy(name=name(row.name),sortOrder=order(row.sortOrder),ingredientIds=row.ingredientIds.sorted())
        }.sortedBy{it.id}
        return OnlineCatalog(ic,i,mc,m)
    }
    fun json():JSONObject{
        val c=validated()
        fun category(r:CatalogCategory)=JSONObject().put("id",r.id).put("name",r.name).put("sortOrder",r.sortOrder)
        return JSONObject().put("ingredientCategories",JSONArray(c.ingredientCategories.map(::category)))
            .put("menuCategories",JSONArray(c.menuCategories.map(::category)))
            .put("ingredients",JSONArray(c.ingredients.map{JSONObject().put("id",it.id).put("categoryId",it.categoryId).put("name",it.name).put("sortOrder",it.sortOrder).put("active",it.active)}))
            .put("menus",JSONArray(c.menus.map{JSONObject().put("id",it.id).put("categoryId",it.categoryId).put("name",it.name).put("sortOrder",it.sortOrder).put("active",it.active).put("ingredientIds",JSONArray(it.ingredientIds))}))
    }
    companion object{
        fun empty()=OnlineCatalog(emptyList(),emptyList(),emptyList(),emptyList())
        fun parse(value:JSONObject):OnlineCatalog{
            fun exact(row:JSONObject,keys:Set<String>){require(row.keys().asSequence().toSet()==keys)}
            fun number(row:JSONObject,key:String):Int{val n=row.get(key);require(n is Int||n is Long);return (n as Number).toLong().also{require(it in 0..1000000)}.toInt()}
            fun text(row:JSONObject,key:String):String{val s=row.get(key);require(s is String);return s}
            fun boolean(row:JSONObject):Boolean{val b=row.get("active");require(b is Boolean);return b}
            fun <T> rows(key:String,limit:Int,read:(JSONObject)->T):List<T>{val a=value.getJSONArray(key);require(a.length()<=limit);return (0 until a.length()).map{read(a.getJSONObject(it))}}
            fun category(row:JSONObject):CatalogCategory{exact(row,setOf("id","name","sortOrder"));return CatalogCategory(text(row,"id"),text(row,"name"),number(row,"sortOrder"))}
            exact(value,setOf("ingredientCategories","ingredients","menuCategories","menus"))
            return OnlineCatalog(rows("ingredientCategories",100,::category),rows("ingredients",1000){row->
                exact(row,setOf("id","categoryId","name","sortOrder","active"));CatalogIngredient(text(row,"id"),text(row,"categoryId"),text(row,"name"),number(row,"sortOrder"),boolean(row))
            },rows("menuCategories",100,::category),rows("menus",500){row->
                exact(row,setOf("id","categoryId","name","sortOrder","active","ingredientIds"));val ids=row.getJSONArray("ingredientIds");require(ids.length() in 1..100)
                CatalogMenu(text(row,"id"),text(row,"categoryId"),text(row,"name"),number(row,"sortOrder"),boolean(row),(0 until ids.length()).map{val id=ids.get(it);require(id is String);id})
            }).validated()
        }
    }
}
data class OnlineCatalogVersion(val versionId:String?,val revision:Long,val catalog:OnlineCatalog)
