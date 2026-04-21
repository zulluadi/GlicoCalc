package com.glicocalc.database

import platform.Foundation.NSDate

actual object PlatformTime {
    actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()
}
