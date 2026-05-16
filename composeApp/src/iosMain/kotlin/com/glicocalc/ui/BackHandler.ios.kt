package com.glicocalc.ui

import androidx.compose.runtime.Composable

@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    // No-op on iOS as there is no system back button
}
