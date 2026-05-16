package com.glicocalc.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import qrcode.QRCode

const val FAMILY_QR_PREFIX = "glicocalc-family:"

fun familyQrPayload(familyId: String): String {
    return familyId
}

fun familyIdFromQrPayload(payload: String): String? {
    val trimmedPayload = payload.trim()
    return when {
        trimmedPayload.startsWith(FAMILY_QR_PREFIX) -> trimmedPayload.removePrefix(FAMILY_QR_PREFIX).takeIf { it.isNotBlank() }
        trimmedPayload.isNotBlank() -> trimmedPayload
        else -> null
    }
}

fun familyQrPngBytes(payload: String): ByteArray {
    return QRCode.ofSquares()
        .withSize(8)
        .build(payload)
        .renderToBytes()
}

@Composable
expect fun FamilyQrCode(
    payload: String,
    modifier: Modifier = Modifier
)
