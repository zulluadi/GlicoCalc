package com.glicocalc.ui

import java.util.Calendar

actual object DeviceTime {
    actual fun currentHour24(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
}
