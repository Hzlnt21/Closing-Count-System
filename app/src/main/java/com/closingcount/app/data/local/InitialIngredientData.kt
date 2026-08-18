package com.closingcount.app.data.local

data class InitialIngredientCategory(
    val name: String,
    val ingredients: List<String>,
)

object InitialIngredientData {
    val categories = listOf(
        InitialIngredientCategory(
            name = "Bahan Baku",
            ingredients = listOf(
                "Fresh Milk",
                "Evaporasi",
                "Soda",
                "Yakult",
                "CHI",
                "SKM",
                "Sunquick Mangga",
                "Jungle Orange",
                "Hydrococo",
                "Blend 08",
                "Whipecream",
            ),
        ),
        InitialIngredientCategory(
            name = "Powder",
            ingredients = listOf(
                "Matcha",
                "Coklat",
                "Alpukat",
                "Max Creamer",
                "Teh Dandang",
                "Teh Gopek",
                "Teh Tongtji",
                "Milo",
            ),
        ),
        InitialIngredientCategory(
            name = "Syrup",
            ingredients = listOf(
                "Salted Caramel",
                "Lychee",
                "Lemon",
                "Strawberry",
                "Pandan",
                "Pappermint",
                "Sakura",
                "Vanilla",
                "Raspberry",
                "Gula Aren",
            ),
        ),
        InitialIngredientCategory(
            name = "Buah",
            ingredients = listOf(
                "Strawberry",
                "Leci Kaleng",
                "Lemon",
            ),
        ),
        InitialIngredientCategory(
            name = "Lain-lain",
            ingredients = listOf(
                "Chocolatos",
                "Regal Biscuit",
                "Ice Cream Vanilla",
                "Air Mineral",
                "Simple Syrup",
                "Butter",
                "Selai Blueberry",
                "Selai Strawberry",
            ),
        ),
    )
}

