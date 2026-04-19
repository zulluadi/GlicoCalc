package com.glicocalc.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import com.squareup.sqldelight.android.AndroidSqliteDriver
import com.squareup.sqldelight.db.SqlDriver

class DatabaseDriverFactory(private val context: Context) {
    fun createDriver(): SqlDriver {
        val driver = AndroidSqliteDriver(
            schema = GlicoDatabase.Schema,
            context = context,
            name = "glicocalc.db",
            callback = object : AndroidSqliteDriver.Callback(GlicoDatabase.Schema) {
                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) {
                    super.onUpgrade(db, oldVersion, newVersion)
                    if (oldVersion < 2) {
                        db.execSQL(
                            """
                            CREATE TABLE MealType (
                                id INTEGER PRIMARY KEY AUTOINCREMENT,
                                name TEXT NOT NULL,
                                targetCarbs REAL NOT NULL,
                                hourOfDay INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
                    }
                    if (oldVersion < 3) {
                        db.execSQL(
                            """
                            CREATE TABLE Setting (
                                key TEXT PRIMARY KEY,
                                value TEXT
                            )
                            """.trimIndent()
                        )
                    }
                }
            }
        )
        driver.execute(
            identifier = null,
            sql = """
                CREATE TABLE IF NOT EXISTS MealType (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    targetCarbs REAL NOT NULL,
                    hourOfDay INTEGER NOT NULL
                )
            """.trimIndent(),
            parameters = 0
        )
        driver.execute(
            identifier = null,
            sql = """
                CREATE TABLE IF NOT EXISTS Setting (
                    key TEXT PRIMARY KEY,
                    content TEXT
                )
            """.trimIndent(),
            parameters = 0
        )
        return driver
    }
}
