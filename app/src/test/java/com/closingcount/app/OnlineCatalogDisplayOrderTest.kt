package com.closingcount.app

import com.closingcount.app.data.online.*
import org.junit.Assert.assertEquals
import org.junit.Test

class OnlineCatalogDisplayOrderTest {
    private val categories=listOf(CatalogCategory("a","Second category",9),CatalogCategory("z","First category",0))
    @Test fun menuOrderUsesCategoryAndConfiguredPositionWithoutRewritingSnapshot() {
        val original=listOf(
            CatalogMenu("a","a","First UUID, last category",0,true,listOf("ingredient")),
            CatalogMenu("b","z","Later menu",8,false,listOf("ingredient")),
            CatalogMenu("y","z","Tie comes later by ID",0,true,listOf("ingredient")),
            CatalogMenu("x","z","Tie comes first by ID",0,true,listOf("ingredient")))
        val before=original.map{it.copy(ingredientIds=it.ingredientIds.toList())}
        val catalog=OnlineCatalog(emptyList(),emptyList(),categories,original)
        assertEquals(listOf("x","y","b","a"),catalog.menusInDisplayOrder().map{it.id})
        assertEquals(before,catalog.menus)
        assertEquals(listOf("ingredient"),catalog.menusInDisplayOrder().first().ingredientIds)
    }
    @Test fun ingredientOrderUsesItsOwnCategoriesAndKeepsInactiveHistoricalRows() {
        val original=listOf(CatalogIngredient("a","a","Last category",0,true),
            CatalogIngredient("b","z","Later position",3,true),
            CatalogIngredient("z","z","Earlier position, inactive",0,false))
        val before=original.map{it.copy()}
        val catalog=OnlineCatalog(categories,original,emptyList(),emptyList())
        assertEquals(listOf("z","b","a"),catalog.ingredientsInDisplayOrder().map{it.id})
        assertEquals(before,catalog.ingredients)
        assertEquals(false,catalog.ingredientsInDisplayOrder().first().active)
    }
}
