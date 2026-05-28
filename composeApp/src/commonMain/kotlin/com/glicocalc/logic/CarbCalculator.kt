package com.glicocalc.logic

import com.glicocalc.models.DishComponent

object CarbCalculator {
    fun calculateTotalCarbs(servingWeightGrams: Double, components: List<DishComponent>, totalCookedWeight: Double?): Double {
        if (components.isEmpty() || totalCookedWeight == null || totalCookedWeight <= 0.0) return 0.0
        val totalRawCarbs = components.sumOf { it.weightGrams * it.foodCarbs / 100.0 }
        val carbsPer100g = (totalRawCarbs / totalCookedWeight) * 100.0
        return servingWeightGrams * carbsPer100g / 100.0
    }

    fun calculatePortionCarbs(portions: Double, components: List<DishComponent>, totalPortions: Double?): Double {
        if (components.isEmpty() || totalPortions == null || totalPortions <= 0.0) return 0.0
        val totalRawCarbs = components.sumOf { it.weightGrams * it.foodCarbs / 100.0 }
        return portions * totalRawCarbs / totalPortions
    }

    fun calculateCarbsPercentage(components: List<DishComponent>, totalCookedWeight: Double?): Double {
        if (components.isEmpty() || totalCookedWeight == null || totalCookedWeight <= 0.0) return 0.0
        val totalRawCarbs = components.sumOf { it.weightGrams * it.foodCarbs / 100.0 }
        return (totalRawCarbs / totalCookedWeight) * 100.0
    }

    fun calculateCarbsPerPortion(components: List<DishComponent>, totalPortions: Double?): Double {
        if (components.isEmpty() || totalPortions == null || totalPortions <= 0.0) return 0.0
        val totalRawCarbs = components.sumOf { it.weightGrams * it.foodCarbs / 100.0 }
        return totalRawCarbs / totalPortions
    }

    fun calculateMealTotal(items: List<Triple<Double, List<DishComponent>, Double?>>): Double {
        return items.sumOf { (weight, components, totalCookedWeight) -> calculateTotalCarbs(weight, components, totalCookedWeight) }
    }
}
