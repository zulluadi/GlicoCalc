package com.glicocalc.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
fun rememberMealTypeNameResolver(): (String) -> String {
    val currentLocale = LocalAppLocale.current
    val languageCode = remember(currentLocale) { currentLocale.lowercase().substringBefore('-') }
    return remember(languageCode) {
        val translations = localizedMealTypeNames[languageCode]
        { name -> translations?.get(name) ?: name }
    }
}

private val localizedMealTypeNames = mapOf(
    "ro" to mapOf(
        "Breakfast" to "Mic dejun",
        "Snack 1" to "Gustare 1",
        "Lunch" to "Prânz",
        "Snack 2" to "Gustare 2",
        "Dinner" to "Cină",
        "Snack 3" to "Gustare 3"
    ),
    "es" to mapOf(
        "Breakfast" to "Desayuno",
        "Snack 1" to "Tentempié 1",
        "Lunch" to "Almuerzo",
        "Snack 2" to "Tentempié 2",
        "Dinner" to "Cena",
        "Snack 3" to "Tentempié 3"
    ),
    "fr" to mapOf(
        "Breakfast" to "Petit-déjeuner",
        "Snack 1" to "Collation 1",
        "Lunch" to "Déjeuner",
        "Snack 2" to "Collation 2",
        "Dinner" to "Dîner",
        "Snack 3" to "Collation 3"
    ),
    "de" to mapOf(
        "Breakfast" to "Frühstück",
        "Snack 1" to "Snack 1",
        "Lunch" to "Mittagessen",
        "Snack 2" to "Snack 2",
        "Dinner" to "Abendessen",
        "Snack 3" to "Snack 3"
    ),
    "it" to mapOf(
        "Breakfast" to "Colazione",
        "Snack 1" to "Spuntino 1",
        "Lunch" to "Pranzo",
        "Snack 2" to "Spuntino 2",
        "Dinner" to "Cena",
        "Snack 3" to "Spuntino 3"
    ),
    "pt" to mapOf(
        "Breakfast" to "Pequeno-almoço",
        "Snack 1" to "Lanche 1",
        "Lunch" to "Almoço",
        "Snack 2" to "Lanche 2",
        "Dinner" to "Jantar",
        "Snack 3" to "Lanche 3"
    ),
    "nl" to mapOf(
        "Breakfast" to "Ontbijt",
        "Snack 1" to "Tussendoortje 1",
        "Lunch" to "Lunch",
        "Snack 2" to "Tussendoortje 2",
        "Dinner" to "Avondeten",
        "Snack 3" to "Tussendoortje 3"
    ),
    "pl" to mapOf(
        "Breakfast" to "Śniadanie",
        "Snack 1" to "Przekąska 1",
        "Lunch" to "Obiad",
        "Snack 2" to "Przekąska 2",
        "Dinner" to "Kolacja",
        "Snack 3" to "Przekąska 3"
    ),
    "cs" to mapOf(
        "Breakfast" to "Snídaně",
        "Snack 1" to "Svačina 1",
        "Lunch" to "Oběd",
        "Snack 2" to "Svačina 2",
        "Dinner" to "Večeře",
        "Snack 3" to "Svačina 3"
    ),
    "sk" to mapOf(
        "Breakfast" to "Raňajky",
        "Snack 1" to "Desiata 1",
        "Lunch" to "Obed",
        "Snack 2" to "Desiata 2",
        "Dinner" to "Večera",
        "Snack 3" to "Desiata 3"
    ),
    "hu" to mapOf(
        "Breakfast" to "Reggeli",
        "Snack 1" to "Uzsonna 1",
        "Lunch" to "Ebéd",
        "Snack 2" to "Uzsonna 2",
        "Dinner" to "Vacsora",
        "Snack 3" to "Uzsonna 3"
    ),
    "bg" to mapOf(
        "Breakfast" to "Закуска",
        "Snack 1" to "Междинно 1",
        "Lunch" to "Обяд",
        "Snack 2" to "Междинно 2",
        "Dinner" to "Вечеря",
        "Snack 3" to "Междинно 3"
    ),
    "el" to mapOf(
        "Breakfast" to "Πρωινό",
        "Snack 1" to "Σνακ 1",
        "Lunch" to "Μεσημεριανό",
        "Snack 2" to "Σνακ 2",
        "Dinner" to "Βραδινό",
        "Snack 3" to "Σνακ 3"
    ),
    "tr" to mapOf(
        "Breakfast" to "Kahvaltı",
        "Snack 1" to "Ara öğün 1",
        "Lunch" to "Öğle yemeği",
        "Snack 2" to "Ara öğün 2",
        "Dinner" to "Akşam yemeği",
        "Snack 3" to "Ara öğün 3"
    ),
    "uk" to mapOf(
        "Breakfast" to "Сніданок",
        "Snack 1" to "Перекус 1",
        "Lunch" to "Обід",
        "Snack 2" to "Перекус 2",
        "Dinner" to "Вечеря",
        "Snack 3" to "Перекус 3"
    ),
    "ru" to mapOf(
        "Breakfast" to "Завтрак",
        "Snack 1" to "Перекус 1",
        "Lunch" to "Обед",
        "Snack 2" to "Перекус 2",
        "Dinner" to "Ужин",
        "Snack 3" to "Перекус 3"
    ),
    "sr" to mapOf(
        "Breakfast" to "Doručak",
        "Snack 1" to "Užina 1",
        "Lunch" to "Ručak",
        "Snack 2" to "Užina 2",
        "Dinner" to "Večera",
        "Snack 3" to "Užina 3"
    ),
    "hr" to mapOf(
        "Breakfast" to "Doručak",
        "Snack 1" to "Užina 1",
        "Lunch" to "Ručak",
        "Snack 2" to "Užina 2",
        "Dinner" to "Večera",
        "Snack 3" to "Užina 3"
    ),
    "sl" to mapOf(
        "Breakfast" to "Zajtrk",
        "Snack 1" to "Malica 1",
        "Lunch" to "Kosilo",
        "Snack 2" to "Malica 2",
        "Dinner" to "Večerja",
        "Snack 3" to "Malica 3"
    ),
    "da" to mapOf(
        "Breakfast" to "Morgenmad",
        "Snack 1" to "Mellemmåltid 1",
        "Lunch" to "Frokost",
        "Snack 2" to "Mellemmåltid 2",
        "Dinner" to "Aftensmad",
        "Snack 3" to "Mellemmåltid 3"
    ),
    "sv" to mapOf(
        "Breakfast" to "Frukost",
        "Snack 1" to "Mellanmål 1",
        "Lunch" to "Lunch",
        "Snack 2" to "Mellanmål 2",
        "Dinner" to "Middag",
        "Snack 3" to "Mellanmål 3"
    ),
    "no" to mapOf(
        "Breakfast" to "Frokost",
        "Snack 1" to "Mellommåltid 1",
        "Lunch" to "Lunsj",
        "Snack 2" to "Mellommåltid 2",
        "Dinner" to "Middag",
        "Snack 3" to "Mellommåltid 3"
    ),
    "fi" to mapOf(
        "Breakfast" to "Aamiainen",
        "Snack 1" to "Välipala 1",
        "Lunch" to "Lounas",
        "Snack 2" to "Välipala 2",
        "Dinner" to "Päivällinen",
        "Snack 3" to "Välipala 3"
    ),
    "et" to mapOf(
        "Breakfast" to "Hommikusöök",
        "Snack 1" to "Vahepala 1",
        "Lunch" to "Lõunasöök",
        "Snack 2" to "Vahepala 2",
        "Dinner" to "Õhtusöök",
        "Snack 3" to "Vahepala 3"
    ),
    "lv" to mapOf(
        "Breakfast" to "Brokastis",
        "Snack 1" to "Uzkoda 1",
        "Lunch" to "Pusdienas",
        "Snack 2" to "Uzkoda 2",
        "Dinner" to "Vakariņas",
        "Snack 3" to "Uzkoda 3"
    ),
    "lt" to mapOf(
        "Breakfast" to "Pusryčiai",
        "Snack 1" to "Užkandis 1",
        "Lunch" to "Pietūs",
        "Snack 2" to "Užkandis 2",
        "Dinner" to "Vakarienė",
        "Snack 3" to "Užkandis 3"
    ),
    "ar" to mapOf(
        "Breakfast" to "الفطور",
        "Snack 1" to "وجبة خفيفة 1",
        "Lunch" to "الغداء",
        "Snack 2" to "وجبة خفيفة 2",
        "Dinner" to "العشاء",
        "Snack 3" to "وجبة خفيفة 3"
    ),
    "he" to mapOf(
        "Breakfast" to "ארוחת בוקר",
        "Snack 1" to "נשנוש 1",
        "Lunch" to "ארוחת צהריים",
        "Snack 2" to "נשנוש 2",
        "Dinner" to "ארוחת ערב",
        "Snack 3" to "נשנוש 3"
    ),
    "zh" to mapOf(
        "Breakfast" to "早餐",
        "Snack 1" to "加餐 1",
        "Lunch" to "午餐",
        "Snack 2" to "加餐 2",
        "Dinner" to "晚餐",
        "Snack 3" to "加餐 3"
    )
)
