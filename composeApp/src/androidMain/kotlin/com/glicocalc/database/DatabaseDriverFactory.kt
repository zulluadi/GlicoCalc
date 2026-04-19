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
                    database: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) {
                    super.onUpgrade(database, oldVersion, newVersion)
                    if (oldVersion < 2) {
                        database.execSQL(
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
        return driver
    }
}
