package com.glicocalc.logic

/**
 * Utilitate pentru normalizarea textului prin eliminarea diacriticelor.
 * Util de exemplu pentru căutare: "sarmale" va găsi "sărmăluțe".
 */
fun String.removeDiacritics(): String {
    val diacriticsMap = mapOf(
        'ă' to 'a', 'Ă' to 'A',
        'â' to 'a', 'Â' to 'A',
        'î' to 'i', 'Î' to 'I',
        'ș' to 's', 'Ș' to 'S',
        'ț' to 't', 'Ț' to 'T'
    )
    
    return this.map { diacriticsMap[it] ?: it }.joinToString("")
}

/** Matches every query fragment anywhere in the name, regardless of order. */
fun matchesSearchQuery(name: String, query: String): Boolean {
    val normalizedName = name.removeDiacritics()
    return query.removeDiacritics().split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .all { normalizedName.contains(it, ignoreCase = true) }
}
