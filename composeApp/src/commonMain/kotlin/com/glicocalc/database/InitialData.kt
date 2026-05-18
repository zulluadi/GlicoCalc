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
        InitialFood(4, "Pâine cu Semințe", 43.0),
        InitialFood(5, "Pâine Toast", 52.0),
        InitialFood(7, "Covrig", 55.0),
        InitialFood(13, "Mămăligă", 20.0),
        InitialFood(9, "Orez Alb (fiert)", 28.0),
        InitialFood(10, "Orez Brun (fiert)", 23.0),
        InitialFood(405, "Orez Alb (crud)", 80.0),
        InitialFood(406, "Orez Brun (crud)", 76.0),
        InitialFood(11, "Paste fierte", 25.0),
        InitialFood(407, "Paste crude", 75.0),
        InitialFood(17, "Cuscus (fiert)", 23.0),
        InitialFood(409, "Cuscus (uscat)", 77.0),
        InitialFood(19, "Hrișcă (fiartă)", 19.0),
        InitialFood(411, "Hrișcă (crudă)", 72.0),
        InitialFood(21, "Linte (fiartă)", 20.0),
        InitialFood(412, "Linte (crudă)", 63.0),
        InitialFood(16, "Griș", 72.0),
        InitialFood(15, "Fulgi de ovăz", 60.0),
        InitialFood(159, "Pesmet", 70.0),
        InitialFood(152, "Făină de grâu", 72.0),
        InitialFood(153, "Făină Integrală", 65.0),
        InitialFood(154, "Mălai", 75.0),
        InitialFood(156, "Făină de Secară", 72.0),
        InitialFood(157, "Făină de Mei", 75.0),
        InitialFood(6, "Baghetă Franceză", 47.0),
        InitialFood(8, "Chifle", 48.0),
        InitialFood(273, "Lipie", 50.0),
        InitialFood(14, "Mămăligă (moale)", 15.0),
        InitialFood(18, "Quinoa (fiartă)", 21.0),
        InitialFood(410, "Quinoa (crudă)", 64.0),
        InitialFood(155, "Făină de Orez", 78.0),
        InitialFood(158, "Amidon de Porumb", 87.0),
        InitialFood(160, "Pané (pesmet/ou)", 68.0),

        // Legume (Vegetables)
        InitialFood(26, "Cartofi fierți", 17.0),
        InitialFood(27, "Cartofi prăjiți (acasă)", 30.0),
        InitialFood(353, "Cartofi pai (fast-food)", 35.0),
        InitialFood(29, "Cartofi copți", 21.0),
        InitialFood(32, "Morcov (crud)", 7.0),
        InitialFood(33, "Morcov (fiert)", 8.0),
        InitialFood(20, "Mazăre (conservă/fiartă)", 14.0),
        InitialFood(81, "Mazăre verde (crudă)", 14.0),
        InitialFood(23, "Fasole boabe fiartă", 15.0),
        InitialFood(24, "Fasole verde", 5.0),
        InitialFood(25, "Soia (fiartă)", 9.0),
        InitialFood(415, "Soia (crudă)", 30.0),
        InitialFood(49, "Roșii", 3.0),
        InitialFood(52, "Castraveți", 2.0),
        InitialFood(322, "Castraveți murați", 2.0),
        InitialFood(53, "Castraveți Murați", 2.0),
        InitialFood(323, "Gogonele", 4.0),
        InitialFood(57, "Ardei gras", 5.0),
        InitialFood(58, "Ardei Iute", 8.0),
        InitialFood(324, "Ardei iuți murați", 5.0),
        InitialFood(59, "Gogoșari", 6.0),
        InitialFood(60, "Ceapă", 7.0),
        InitialFood(43, "Varză (crudă)", 4.0),
        InitialFood(45, "Varză (fiartă)", 5.0),
        InitialFood(46, "Varză de Bruxelles", 6.0),
        InitialFood(39, "Broccoli (crud)", 4.0),
        InitialFood(40, "Broccoli (fiert)", 5.0),
        InitialFood(41, "Conopidă (crudă)", 4.0),
        InitialFood(42, "Conopidă (fiartă/prăjită)", 5.0),
        InitialFood(47, "Gulii (crude)", 5.0),
        InitialFood(357, "Gulii (fierte)", 5.0),
        InitialFood(34, "Păstârnac (fiert)", 10.0),
        InitialFood(356, "Păstârnac (crud)", 10.0),
        InitialFood(54, "Dovlecei", 3.0),
        InitialFood(56, "Vinete", 5.0),
        InitialFood(68, "Ciuperci", 3.0),
        InitialFood(69, "Ciuperci Champignon", 3.0),
        InitialFood(70, "Ciuperci (conservă)", 4.0),
        InitialFood(325, "Ciuperci murate", 4.0),
        InitialFood(63, "Spanac", 3.0),
        InitialFood(64, "Mangold", 3.0),
        InitialFood(65, "Urzici", 3.0),
        InitialFood(66, "Lobodă", 4.0),
        InitialFood(67, "Ștevie", 5.0),
        InitialFood(37, "Porumb (conservă)", 18.0),
        InitialFood(38, "Porumb fiert", 17.0),
        InitialFood(354, "Porumb dulce (crud)", 19.0),
        InitialFood(31, "Sfeclă roșie (fiartă/conservă)", 10.0),
        InitialFood(355, "Sfeclă roșie (crudă)", 10.0),
        InitialFood(61, "Usturoi", 30.0),
        InitialFood(71, "Avocado", 9.0),
        InitialFood(73, "Măsline (verzi/negre)", 5.0),
        InitialFood(74, "Măsline Uscate", 20.0),
        InitialFood(22, "Năut (fiert)", 27.0),
        InitialFood(28, "Piure de Cartofi", 14.0),
        InitialFood(30, "Cartofi dulci", 20.0),
        InitialFood(55, "Dovleac", 6.0),
        InitialFood(62, "Praz", 10.0),
        InitialFood(35, "Țelină", 6.0),
        InitialFood(413, "Năut (crud)", 61.0),
        InitialFood(414, "Fasole Boabe (crudă)", 60.0),
        InitialFood(416, "Cartofi cruzi", 17.0),
        InitialFood(417, "Cartofi dulci cruzi", 20.0),

        // Fructe (Fruits)
        InitialFood(88, "Mere", 12.0),
        InitialFood(95, "Banane", 20.0),
        InitialFood(98, "Portocale", 9.0),
        InitialFood(99, "Portocală Sanguinello", 10.0),
        InitialFood(100, "Mandarine", 8.0),
        InitialFood(101, "Clementină", 10.0),
        InitialFood(106, "Pere", 12.0),
        InitialFood(110, "Struguri", 18.0),
        InitialFood(114, "Căpșuni", 7.0),
        InitialFood(115, "Căpșuni (conservă)", 8.0),
        InitialFood(116, "Afine", 14.0),
        InitialFood(118, "Zmeură", 10.0),
        InitialFood(125, "Piersici / Nectarine", 10.0),
        InitialFood(129, "Piersici uscate", 65.0),
        InitialFood(127, "Caise", 9.0),
        InitialFood(128, "Caise uscate", 62.0),
        InitialFood(130, "Prune", 10.0),
        InitialFood(132, "Pepene roșu", 7.0),
        InitialFood(133, "Pepene galben", 8.0),
        InitialFood(136, "Kiwi", 12.0),
        InitialFood(137, "Ananas", 12.0),
        InitialFood(102, "Lămâie", 3.0),
        InitialFood(141, "Curmale / Smochine (uscate)", 65.0),
        InitialFood(142, "Curmale uscate", 75.0),
        InitialFood(144, "Smochine uscate", 60.0),
        InitialFood(113, "Stafide", 75.0),
        InitialFood(104, "Grepfrut", 6.0),
        InitialFood(117, "Mure", 10.0),
        InitialFood(119, "Coacăze negre", 15.0),
        InitialFood(120, "Coacăze roșii", 12.0),
        InitialFood(121, "Coacăze albe", 13.0),
        InitialFood(123, "Lonicera", 12.0),
        InitialFood(124, "Măceșe", 25.0),
        InitialFood(134, "Rodie", 14.0),
        InitialFood(138, "Mango", 14.0),
        InitialFood(139, "Papaya", 10.0),
        InitialFood(140, "Fructul Passion", 13.0),
        InitialFood(145, "Smochine proaspete", 12.0),
        InitialFood(146, "Smochine verzi", 10.0),

        // Lactate și Ouă (Dairy & Eggs)
        InitialFood(162, "Lapte", 5.0),
        InitialFood(166, "Lapte condensat", 12.0),
        InitialFood(169, "Iaurt natur", 4.5),
        InitialFood(170, "Iaurt Grecesc", 4.0),
        InitialFood(171, "Iaurt cu Fructe", 12.0),
        InitialFood(172, "Iaurt de băut", 11.0),
        InitialFood(175, "Brânză de vaci", 4.0),
        InitialFood(186, "Cașcaval / Telemea", 1.5),
        InitialFood(177, "Brânză de Burduf", 1.5),
        InitialFood(178, "Brânză de Burduf (afumată)", 1.0),
        InitialFood(179, "Brânză de Secărcău", 1.5),
        InitialFood(180, "Brânză de Năsal", 2.0),
        InitialFood(181, "Brânză de Coțofen", 1.5),
        InitialFood(182, "Brânză de Măgura", 1.8),
        InitialFood(183, "Brânză de Cărbune", 2.0),
        InitialFood(168, "Smântână", 3.5),
        InitialFood(76, "Unt", 0.1),
        InitialFood(208, "Ou", 0.7),
        InitialFood(173, "Kefir / Lapte bătut", 4.5),
        InitialFood(167, "Lapte praf degresat", 52.0),

        // Carne și Pește (Meat & Fish)
        InitialFood(221, "Carne (Porc, Vită, Pasăre)", 0.0),
        InitialFood(236, "Pește", 0.0),
        InitialFood(360, "Mezeluri (Salam, Șuncă)", 1.5),
        InitialFood(358, "Crenvurști", 5.0),
        InitialFood(337, "Pate de ficat", 5.0),

        // Dulciuri și Gustări (Sweets & Snacks)
        InitialFood(255, "Zahăr", 100.0),
        InitialFood(256, "Zahăr pudră", 100.0),
        InitialFood(257, "Zahăr brun", 98.0),
        InitialFood(258, "Fructoză", 100.0),
        InitialFood(259, "Glucoză", 100.0),
        InitialFood(260, "Miere", 80.0),
        InitialFood(265, "Sirop de arțar", 60.0),
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
        InitialFood(250, "Compot de fructe", 18.0),
        InitialFood(251, "Gem / Dulceață de fructe", 65.0),
        InitialFood(270, "Grisine", 75.0),
        InitialFood(131, "Caju", 30.0),
        InitialFood(275, "Plăcintă cu brânză", 30.0),
        InitialFood(276, "Plăcintă cu mere", 35.0),
        InitialFood(277, "Plăcintă dobrogeană", 28.0),
        InitialFood(307, "Cozonac", 50.0),
        InitialFood(308, "Chec", 45.0),

        // Mâncăruri Gătite (Cooked Meals / Fast Food)
        InitialFood(301, "Pizza (blat subțire)", 30.0),
        InitialFood(1011, "Pizza (blat clasic)", 33.0),
        InitialFood(1012, "Pizza (blat pufos)", 40.0),
        InitialFood(297, "Hamburger", 25.0),
        InitialFood(303, "Sandwich", 35.0),
        InitialFood(278, "Sarmale", 8.0),
        InitialFood(282, "Ciorbă de legume", 6.0),
        InitialFood(280, "Ciorbă de burtă", 5.0),
        InitialFood(285, "Borș", 4.0),
        InitialFood(286, "Ciorbă de perișoare", 5.0),
        InitialFood(283, "Supă de pui", 3.0),
        InitialFood(1009, "Sushi", 35.0),
        InitialFood(319, "Hummus", 15.0),
        InitialFood(314, "Salată de vinete", 7.0),
        InitialFood(287, "Zacuscă de pește", 4.0),
        InitialFood(288, "Zacuscă de legume", 8.0),
        InitialFood(279, "Mâncare de cartofi", 15.0),
        InitialFood(327, "Ghiveci", 12.0),
        InitialFood(419, "Musaca", 18.0),
        InitialFood(281, "Ciorbă de fasole", 12.0),
        InitialFood(316, "Iahnie de fasole", 10.0),
        InitialFood(290, "Mămăligă cu brânză", 12.0),
        InitialFood(342, "Chiftele", 8.0),
        InitialFood(343, "Pârjoale", 10.0),
        InitialFood(418, "Ostropel", 6.0),
        InitialFood(420, "Tochitură", 8.0),
        InitialFood(344, "Șnițel", 8.0),

        // Băuturi (Drinks)
        InitialFood(375, "Sucuri carbogazoase", 10.0),
        InitialFood(381, "Suc de fructe", 11.0),
        InitialFood(387, "Bere", 5.0),
        InitialFood(390, "Vin", 2.5),
        InitialFood(370, "Cafea / Ceai (fără zahăr)", 0.0),
        InitialFood(372, "Cappuccino", 2.0),
        InitialFood(373, "Latte macchiato", 2.5)
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
