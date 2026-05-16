package com.glicocalc.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap

@Composable
actual fun FamilyQrCode(
    payload: String,
    modifier: Modifier
) {
    val imageBitmap = remember(payload) {
        val bytes = familyQrPngBytes(payload)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size).asImageBitmap()
    }
    Image(
        bitmap = imageBitmap,
        contentDescription = Strings.familyQrCode(),
        modifier = modifier
    )
}
