package com.glicocalc.logic

import com.glicocalc.models.DishComponent

object CarbCalculator {
    fun calculateTotalCarbs(weightGrams: Double, components: List<DishComponent>): Double {
        if (components.isEmpty()) return 0.0
        val carbsPerGramOfDish = components.sumOf { (it.percentage / 100.0) * (it.foodCarbs / 100.0) }
        return weightGrams * carbsPerGramOfDish
    }

    fun calculateCarbsPercentage(components: List<DishComponent>): Double {
        val carbsPerGram = components.sumOf { (it.percentage / 100.0) * (it.foodCarbs / 100.0) }
        return carbsPerGram * 100.0
    }

    fun calculateMealTotal(items: List<Pair<Double, List<DishComponent>>>): Double {
        return items.sumOf { (weight, components) -> calculateTotalCarbs(weight, components) }
    }
}
