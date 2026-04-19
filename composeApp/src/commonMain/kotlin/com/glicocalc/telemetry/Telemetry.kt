package com.glicocalc.telemetry

interface Telemetry {
    fun screenViewed(screenName: String)
    fun action(name: String)
    fun recordError(error: Throwable, context: String? = null)
}
