package com.glicocalc.database

import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.drivers.native.NativeSqliteDriver

class DatabaseDriverFactory {
    fun createDriver(): SqlDriver {
        return NativeSqliteDriver(GlicoDatabase.Schema, "glicocalc.db")
    }
}
