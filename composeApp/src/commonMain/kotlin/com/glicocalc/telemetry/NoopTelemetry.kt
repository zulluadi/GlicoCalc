package com.glicocalc.telemetry

object NoopTelemetry : Telemetry {
    override fun screenViewed(screenName: String) = Unit

    override fun action(name: String) = Unit

    override fun recordError(error: Throwable, context: String?) = Unit
}
