package com.glicocalc.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Composable
actual fun rememberNightscoutTokenStore(): NightscoutTokenStore {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidNightscoutTokenStore(context) }
}

private class AndroidNightscoutTokenStore(private val context: Context) : NightscoutTokenStore {
    private val alias = "glicocalc.nightscout.tokens"
    private fun file(site: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(site.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(context.noBackupFilesDir, "nightscout-$hash")
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    override fun read(site: String): String {
        val file = file(site)
        val bytes = try { android.util.AtomicFile(file).readFully() }
            catch (_: java.io.FileNotFoundException) { return "" }
        val parts = bytes.decodeToString().split(':')
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        cipher.updateAAD(site.toByteArray())
        return cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)).decodeToString()
    }

    override fun save(site: String, token: String) {
        val file = file(site)
        if (token.isBlank()) {
            check(!file.exists() || file.delete())
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(site.toByteArray())
        val encrypted = cipher.doFinal(token.toByteArray())
        val value = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP)
        val atomicFile = android.util.AtomicFile(file)
        val output = atomicFile.startWrite()
        try {
            output.write(value.toByteArray())
            atomicFile.finishWrite(output)
        } catch (e: Exception) {
            atomicFile.failWrite(output)
            throw e
        }
    }
}
