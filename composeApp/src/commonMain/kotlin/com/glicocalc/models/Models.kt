package com.glicocalc.models

data class DishComponent(
    val id: Long,
    val dishId: Long,
    val baseFoodId: Long,
    val percentage: Double,
    val foodName: String = "",
    val foodCarbs: Double = 0.0
)

data class DishWithComposition(
    val dish: com.glicocalc.database.Dish,
    val components: List<DishComponent>
)
