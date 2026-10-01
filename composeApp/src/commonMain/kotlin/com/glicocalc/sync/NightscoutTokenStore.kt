package com.glicocalc.sync

import androidx.compose.runtime.Composable

interface NightscoutTokenStore {
    fun read(site: String): String
    fun save(site: String, token: String)
}

@Composable
expect fun rememberNightscoutTokenStore(): NightscoutTokenStore
