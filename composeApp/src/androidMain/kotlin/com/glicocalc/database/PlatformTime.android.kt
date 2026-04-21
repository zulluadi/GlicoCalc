package com.glicocalc.database

actual object PlatformTime {
    actual fun currentTimeMillis(): Long = System.currentTimeMillis()
}
