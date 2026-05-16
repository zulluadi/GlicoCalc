package com.glicocalc.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

@Composable
actual fun FamilyQrCode(
    payload: String,
    modifier: Modifier
) {
    val imageBitmap = remember(payload) {
        Image.makeFromEncoded(familyQrPngBytes(payload)).toComposeImageBitmap()
    }
    Image(
        bitmap = imageBitmap,
        contentDescription = Strings.familyQrCode(),
        modifier = modifier
    )
}
