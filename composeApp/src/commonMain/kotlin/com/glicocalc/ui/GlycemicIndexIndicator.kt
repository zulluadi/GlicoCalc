package com.glicocalc.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun rememberGlycemicIndexTextResolver(): (String?) -> String? {
    val appLocale = LocalAppLocale.current
    val selectedFoodLocale = customFoodLocale
    val isRomanian = remember(appLocale, selectedFoodLocale) {
        (selectedFoodLocale ?: appLocale).lowercase().startsWith("ro")
    }
    return remember(isRomanian) {
        { level ->
            when (level) {
                "low" -> if (isRomanian) "IG: scăzut" else "GI: Low"
                "medium" -> if (isRomanian) "IG: mediu" else "GI: Medium"
                "high" -> if (isRomanian) "IG: ridicat" else "GI: High"
                else -> null
            }
        }
    }
}

@Composable
fun GlycemicIndexBadge(level: String?, modifier: Modifier = Modifier) {
    val resolveText = rememberGlycemicIndexTextResolver()
    val text = resolveText(level) ?: return
    val colors = when (level) {
        "low" -> Color(0xFFC8E6C9) to Color(0xFF1B5E20)
        "medium" -> Color(0xFFFFE0B2) to Color(0xFF8C3900)
        else -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
    }
    Surface(
        modifier = modifier,
        color = colors.first,
        contentColor = colors.second,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}
