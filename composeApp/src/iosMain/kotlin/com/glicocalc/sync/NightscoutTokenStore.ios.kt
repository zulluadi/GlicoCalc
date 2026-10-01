@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.glicocalc.sync

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.*
import platform.CoreFoundation.*
import platform.Foundation.*
import platform.Security.*

@Composable
actual fun rememberNightscoutTokenStore(): NightscoutTokenStore = remember { IosNightscoutTokenStore() }

private class IosNightscoutTokenStore : NightscoutTokenStore {
    private fun foundation(value: CFTypeRef?): Any = CFBridgingRelease(CFRetain(value))!!
    private fun key(value: CFStringRef?): String = foundation(value).toString()

    private fun query(site: String): NSMutableDictionary = NSMutableDictionary().apply {
        setValue(foundation(kSecClassGenericPassword), key(kSecClass))
        setValue("com.glicocalc.nightscout", key(kSecAttrService))
        setValue(site, key(kSecAttrAccount))
    }

    private fun <T> withDictionary(dictionary: NSDictionary, block: (CFDictionaryRef) -> T): T {
        val ref = CFBridgingRetain(dictionary)!!
        try { return block(ref.reinterpret()) } finally { CFRelease(ref) }
    }

    override fun read(site: String): String = memScoped {
        val query = query(site).apply {
            setValue(foundation(kCFBooleanTrue), key(kSecReturnData))
            setValue(foundation(kSecMatchLimitOne), key(kSecMatchLimit))
        }
        val result = alloc<CFTypeRefVar>()
        val status = withDictionary(query) { SecItemCopyMatching(it, result.ptr) }
        if (status == errSecItemNotFound) return@memScoped ""
        check(status == errSecSuccess) { "Unable to read saved Nightscout token." }
        val data = CFBridgingRelease(result.value) as NSData
        NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString().orEmpty()
    }

    override fun save(site: String, token: String) {
        val query = query(site)
        if (token.isBlank()) {
            val status = withDictionary(query) { SecItemDelete(it) }
            check(status == errSecSuccess || status == errSecItemNotFound)
            return
        }
        val bytes = token.encodeToByteArray()
        val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        val attributes = NSMutableDictionary().apply {
            setValue(data, key(kSecValueData))
            setValue(foundation(kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly), key(kSecAttrAccessible))
        }
        val status = withDictionary(query) { q -> withDictionary(attributes) { SecItemUpdate(q, it) } }
        if (status == errSecItemNotFound) {
            query.setValue(data, key(kSecValueData))
            query.setValue(foundation(kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly), key(kSecAttrAccessible))
            check(withDictionary(query) { SecItemAdd(it, null) } == errSecSuccess)
        } else check(status == errSecSuccess)
    }
}
