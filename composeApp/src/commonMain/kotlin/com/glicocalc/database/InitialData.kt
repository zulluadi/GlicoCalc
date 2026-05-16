package com.glicocalc.database

import kotlin.math.absoluteValue
import kotlin.random.Random

data class InitialFood(val idSuffix: Int, val name: String, val carbs: Double)
data class InitialMealType(val name: String, val hourOfDay: Long, val targetCarbs: Double)
data class SeedFood(val remoteKey: String, val name: String, val carbs: Double)

object InitialData {
    val mealTypes = listOf(
        InitialMealType("Breakfast", 7, 50.0),
        InitialMealType("Snack 1", 10, 25.0),
        InitialMealType("Lunch", 13, 75.0),
        InitialMealType("Snack 2", 16, 25.0),
        InitialMealType("Dinner", 19, 50.0),
        InitialMealType("Snack 3", 22, 25.0)
    )

    val foods = listOf(
        // Pâine și Cereale (Bread & Cereals)
        InitialFood(1, "Pâine Albă", 49.0),
        InitialFood(2, "Pâine Integrală", 41.0),
        InitialFood(3, "Pâine de Secară", 45.0),
        InitialFood(7, "Covrig", 55.0),
        InitialFood(13, "Mămăligă", 20.0),
        InitialFood(9, "Orez fiert", 28.0),
        InitialFood(405, "Orez crud", 80.0),
        InitialFood(11, "Paste fierte", 25.0),
        InitialFood(407, "Paste crude", 75.0),
        InitialFood(16, "Griș", 72.0),
        InitialFood(15, "Fulgi de ovăz", 60.0),
        InitialFood(159, "Pesmet", 70.0),
        InitialFood(152, "Făină de grâu", 72.0),
        InitialFood(154, "Mălai", 75.0),

        // Legume (Vegetables)
        InitialFood(26, "Cartofi fierți", 17.0),
        InitialFood(27, "Cartofi prăjiți (acasă)", 30.0),
        InitialFood(353, "Cartofi pai (fast-food)", 35.0),
        InitialFood(29, "Cartofi copți", 21.0),
        InitialFood(32, "Morcov", 7.0),
        InitialFood(20, "Mazăre", 14.0),
        InitialFood(23, "Fasole boabe fiartă", 15.0),
        InitialFood(24, "Fasole verde", 5.0),
        InitialFood(49, "Roșii", 3.0),
        InitialFood(52, "Castraveți", 2.0),
        InitialFood(57, "Ardei gras", 5.0),
        InitialFood(60, "Ceapă", 7.0),
        InitialFood(43, "Varză", 4.0),
        InitialFood(39, "Broccoli", 4.0),
        InitialFood(41, "Conopidă", 4.0),
        InitialFood(54, "Dovlecei", 3.0),
        InitialFood(56, "Vinete", 5.0),
        InitialFood(68, "Ciuperci", 3.0),
        InitialFood(63, "Spanac", 3.0),
        InitialFood(37, "Porumb", 18.0),
        InitialFood(31, "Sfeclă roșie", 10.0),
        InitialFood(61, "Usturoi", 30.0),
        InitialFood(71, "Avocado", 9.0),
        InitialFood(73, "Măsline", 5.0),

        // Fructe (Fruits)
        InitialFood(88, "Mere", 12.0),
        InitialFood(95, "Banane", 20.0),
        InitialFood(98, "Portocale", 9.0),
        InitialFood(100, "Mandarine", 8.0),
        InitialFood(106, "Pere", 12.0),
        InitialFood(110, "Struguri", 18.0),
        InitialFood(114, "Căpșuni", 7.0),
        InitialFood(116, "Afine", 14.0),
        InitialFood(118, "Zmeură", 10.0),
        InitialFood(125, "Piersici / Nectarine", 10.0),
        InitialFood(127, "Caise", 9.0),
        InitialFood(130, "Prune", 10.0),
        InitialFood(132, "Pepene roșu", 7.0),
        InitialFood(133, "Pepene galben", 8.0),
        InitialFood(136, "Kiwi", 12.0),
        InitialFood(137, "Ananas", 12.0),
        InitialFood(102, "Lămâie", 3.0),
        InitialFood(141, "Curmale / Smochine (uscate)", 65.0),
        InitialFood(113, "Stafide", 75.0),

        // Lactate și Ouă (Dairy & Eggs)
        InitialFood(162, "Lapte", 5.0),
        InitialFood(169, "Iaurt natur", 4.5),
        InitialFood(175, "Brânză de vaci", 4.0),
        InitialFood(186, "Cașcaval / Telemea", 1.5),
        InitialFood(168, "Smântână", 3.5),
        InitialFood(76, "Unt", 0.1),
        InitialFood(208, "Ou", 0.7),

        // Carne și Pește (Meat & Fish)
        InitialFood(221, "Carne (Porc, Vită, Pasăre)", 0.0),
        InitialFood(236, "Pește", 0.0),
        InitialFood(360, "Mezeluri (Salam, Șuncă)", 1.5),
        InitialFood(358, "Crenvurști", 5.0),
        InitialFood(337, "Pate de ficat", 5.0),

        // Dulciuri și Gustări (Sweets & Snacks)
        InitialFood(255, "Zahăr", 100.0),
        InitialFood(260, "Miere", 80.0),
        InitialFood(1002, "Ciocolată cu lapte", 55.0),
        InitialFood(1003, "Ciocolată neagră", 35.0),
        InitialFood(266, "Biscuiți Populari / Simpli", 75.0),
        InitialFood(269, "Biscuiți Digestivi", 65.0),
        InitialFood(268, "Biscuiți cu ciocolată", 65.0),
        InitialFood(1010, "Biscuiți cu ovăz", 60.0),
        InitialFood(1005, "Înghețată", 25.0),
        InitialFood(1004, "Napolitane", 65.0),
        InitialFood(1006, "Popcorn", 55.0),
        InitialFood(1007, "Chipsuri", 50.0),
        InitialFood(1008, "Pufuleți", 60.0),
        InitialFood(147, "Nuci / Alune / Migdale", 15.0),
        InitialFood(1013, "Semințe de chia", 7.7),
        InitialFood(304, "Clătite (simple)", 30.0),

        // Mâncăruri Gătite (Cooked Meals / Fast Food)
        InitialFood(301, "Pizza (blat subțire)", 30.0),
        InitialFood(1011, "Pizza (blat clasic)", 33.0),
        InitialFood(1012, "Pizza (blat pufos)", 40.0),
        InitialFood(297, "Hamburger", 25.0),
        InitialFood(278, "Sarmale", 8.0),
        InitialFood(282, "Ciorbă de legume", 6.0),
        InitialFood(283, "Supă de pui", 3.0),
        InitialFood(1009, "Sushi", 35.0),
        InitialFood(319, "Hummus", 15.0),
        InitialFood(314, "Salată de vinete", 7.0),

        // Băuturi (Drinks)
        InitialFood(375, "Sucuri carbogazoase", 10.0),
        InitialFood(381, "Suc de fructe", 11.0),
        InitialFood(387, "Bere", 5.0),
        InitialFood(390, "Vin", 2.5),
        InitialFood(370, "Cafea / Ceai (fără zahăr)", 0.0)
    )

    val seededFoods: List<SeedFood> = foods.map { food ->
        SeedFood(
            remoteKey = defaultFoodRemoteKey(food.idSuffix),
            name = food.name,
            carbs = food.carbs
        )
    }

    fun defaultFoodByIndex(index: Int): SeedFood? = seededFoods.getOrNull(index)

    fun defaultFoodByRemoteKey(remoteKey: String): SeedFood? = seededFoods.firstOrNull { it.remoteKey == remoteKey }
}

private fun defaultFoodRemoteKey(idSuffix: Int): String {
    return "default-${idSuffix.toString().padStart(4, '0')}"
}

internal fun generateCustomFoodRemoteKey(): String {
    return generateCustomRemoteKey("custom")
}

internal fun generateCustomDishRemoteKey(): String {
    return generateCustomRemoteKey("dish")
}

private fun generateCustomRemoteKey(prefix: String): String {
    val randomSuffix = Random.nextLong().absoluteValue.toString(16)
    return "$prefix-${PlatformTime.currentTimeMillis()}-$randomSuffix"
}
