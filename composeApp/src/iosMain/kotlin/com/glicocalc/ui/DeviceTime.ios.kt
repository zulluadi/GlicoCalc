package com.glicocalc.ui

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSDate

actual object DeviceTime {
    actual fun currentHour24(): Int {
        val components = NSCalendar.currentCalendar.components(NSCalendarUnitHour, fromDate = NSDate())
        return components.hour.toInt()
    }
}
