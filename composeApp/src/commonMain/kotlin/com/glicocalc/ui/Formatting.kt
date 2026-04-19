package com.glicocalc.ui

fun formatDecimal(value: Double): String {
    val rounded = kotlin.math.round(value * 10) / 10
    return if (rounded % 1.0 == 0.0) {
        rounded.toInt().toString()
    } else {
        rounded.toString()
    }
}

fun formatHour(hourOfDay: Int): String = hourOfDay.toString().padStart(2, '0') + ":00"
