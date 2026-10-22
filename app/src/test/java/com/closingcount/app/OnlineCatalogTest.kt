package com.closingcount.app

import com.closingcount.app.data.online.*
import org.junit.Assert.*
import org.junit.Test

class OnlineCatalogTest{
    private fun id(n:Int)="00000000-0000-4000-8000-"+n.toString().padStart(12,'0')
    private fun sample()=OnlineCatalog(listOf(CatalogCategory(id(1)," Bahan ",0)),
        listOf(CatalogIngredient(id(3),id(1),"Susu",1,true),CatalogIngredient(id(2),id(1),"Kopi",0,true)),
        listOf(CatalogCategory(id(4),"Minuman",0)),listOf(CatalogMenu(id(5),id(4),"Latte",0,true,listOf(id(3),id(2)))))
    private fun refuses(action:()->Unit){assertThrows(IllegalArgumentException::class.java){action()}}
    @Test fun canonicalOrderTrimsNamesWithoutMutatingSourceOrRecipeMeaning(){
        val original=sample();val normalized=original.validated();assertEquals(" Bahan ",original.ingredientCategories.single().name)
        assertEquals("Bahan",normalized.ingredientCategories.single().name);assertEquals(listOf(id(2),id(3)),normalized.menus.single().ingredientIds)
        assertEquals(normalized,original.copy(ingredients=original.ingredients.reversed(),menus=original.menus.map{it.copy(ingredientIds=it.ingredientIds.reversed())}).validated())
    }
    @Test fun referencedCategoryOrIngredientCannotBeRemoved(){
        val c=sample();refuses{c.copy(ingredientCategories=emptyList()).validated()};refuses{c.copy(menuCategories=emptyList()).validated()}
        refuses{c.copy(ingredients=c.ingredients.take(1)).validated()}
        assertTrue(c.copy(menus=emptyList()).validated().menus.isEmpty())
    }
    @Test fun activeMenuRequiresActiveIngredientsButInactiveRecipeCanRetainThem(){
        val c=sample().let{it.copy(ingredients=it.ingredients.map{r->r.copy(active=false)})}
        refuses{c.validated()};assertFalse(c.copy(menus=c.menus.map{it.copy(active=false)}).validated().menus.single().active)
    }
    @Test fun recipeMustHaveDistinctKnownIngredientsWithinLimit(){
        val c=sample();for(ids in listOf(emptyList(),listOf(id(2),id(2)),listOf(id(99)),List(101){id(it+100)}))
            refuses{c.copy(menus=c.menus.map{it.copy(ingredientIds=ids)}).validated()}
    }
    @Test fun duplicateIdentityAndInvalidNamesOrOrdersAreRejected(){
        val c=sample();refuses{c.copy(ingredients=c.ingredients+c.ingredients[0]).validated()}
        for(n in listOf(" ","x".repeat(121)))refuses{c.copy(menus=c.menus.map{it.copy(name=n)}).validated()}
        for(n in listOf(-1,1000001))refuses{c.copy(menus=c.menus.map{it.copy(sortOrder=n)}).validated()}
        refuses{c.copy(menuCategories=listOf(CatalogCategory("bad","Menu",0))).validated()}
    }
    @Test fun collectionLimitsAreEnforcedAndEmptyCatalogIsRepresentable(){
        assertEquals(OnlineCatalog.empty(),OnlineCatalog.empty().validated())
        val c=sample();refuses{c.copy(ingredientCategories=List(101){CatalogCategory(id(it+100),"Category",0)}).validated()}
        refuses{c.copy(ingredients=List(1001){CatalogIngredient(id(it+100),id(1),"Ingredient",0,true)}).validated()}
        refuses{c.copy(menus=List(501){CatalogMenu(id(it+100),id(4),"Menu",0,true,listOf(id(2)))}).validated()}
    }
    @Test fun renamesAndRecipeChangesKeepHistoricalValueObjectsIndependent(){
        val old=sample().validated();val newer=old.copy(menus=old.menus.map{it.copy(name="Latte Baru",ingredientIds=listOf(id(2)))}).validated()
        assertEquals("Latte",old.menus.single().name);assertEquals(2,old.menus.single().ingredientIds.size)
        assertEquals("Latte Baru",newer.menus.single().name);assertEquals(old.menus.single().id,newer.menus.single().id)
    }
}
