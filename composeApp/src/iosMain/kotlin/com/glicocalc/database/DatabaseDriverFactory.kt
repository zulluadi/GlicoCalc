package com.glicocalc.database

import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.drivers.native.NativeSqliteDriver

class DatabaseDriverFactory {
    fun createDriver(): SqlDriver {
        val driver = NativeSqliteDriver(GlicoDatabase.Schema, "glicocalc.db")
        driver.execute(
            identifier = null,
            sql = """
            CREATE TABLE IF NOT EXISTS FamilyMember (
                email TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL DEFAULT '',
                firebaseUid TEXT,
                isOwner INTEGER NOT NULL DEFAULT 0,
                addedAt INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
            parameters = 0
        )
        return driver
    }
}
