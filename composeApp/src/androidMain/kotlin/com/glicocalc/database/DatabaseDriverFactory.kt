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
                    if (oldVersion < 4) {
                        db.execSQL(
                            """
                            CREATE TABLE FamilyMember (
                                email TEXT NOT NULL PRIMARY KEY,
                                name TEXT NOT NULL DEFAULT '',
                                firebaseUid TEXT,
                                isOwner INTEGER NOT NULL DEFAULT 0,
                                addedAt INTEGER NOT NULL DEFAULT 0
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
        ensureBaseFoodSyncColumns(driver)
        ensureDishSyncColumns(driver)
        ensureSettingSyncColumns(driver)
        ensureFamilyMemberTable(driver)
        return driver
    }

    private fun ensureBaseFoodSyncColumns(driver: SqlDriver) {
        safeExecute(driver, "ALTER TABLE BaseFood ADD COLUMN remoteKey TEXT")
        safeExecute(driver, "ALTER TABLE BaseFood ADD COLUMN source TEXT NOT NULL DEFAULT 'default'")
        safeExecute(driver, "ALTER TABLE BaseFood ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
        safeExecute(driver, "ALTER TABLE BaseFood ADD COLUMN needsSync INTEGER NOT NULL DEFAULT 0")
        safeExecute(driver, "ALTER TABLE BaseFood ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        driver.execute(
            identifier = null,
            sql = "CREATE UNIQUE INDEX IF NOT EXISTS idx_basefood_remote_key ON BaseFood(remoteKey)",
            parameters = 0
        )
    }

    private fun ensureDishSyncColumns(driver: SqlDriver) {
        safeExecute(driver, "ALTER TABLE Dish ADD COLUMN remoteKey TEXT")
        safeExecute(driver, "ALTER TABLE Dish ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
        safeExecute(driver, "ALTER TABLE Dish ADD COLUMN needsSync INTEGER NOT NULL DEFAULT 0")
        safeExecute(driver, "ALTER TABLE Dish ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        safeExecute(driver, "ALTER TABLE Dish ADD COLUMN totalCookedWeight REAL")
        driver.execute(
            identifier = null,
            sql = "CREATE UNIQUE INDEX IF NOT EXISTS idx_dish_remote_key ON Dish(remoteKey)",
            parameters = 0
        )
        safeExecute(driver, "ALTER TABLE DishComponent ADD COLUMN weightGrams REAL NOT NULL DEFAULT 0")
    }

    private fun ensureSettingSyncColumns(driver: SqlDriver) {
        safeExecute(driver, "ALTER TABLE Setting ADD COLUMN needsSync INTEGER NOT NULL DEFAULT 0")
        safeExecute(driver, "ALTER TABLE Setting ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
    }

    private fun ensureFamilyMemberTable(driver: SqlDriver) {
        safeExecute(
            driver,
            """
            CREATE TABLE IF NOT EXISTS FamilyMember (
                email TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL DEFAULT '',
                firebaseUid TEXT,
                isOwner INTEGER NOT NULL DEFAULT 0,
                addedAt INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }

    private fun safeExecute(driver: SqlDriver, sql: String) {
        try {
            driver.execute(identifier = null, sql = sql, parameters = 0)
        } catch (_: Exception) {
            // Column already exists on upgraded installs.
        }
    }
}
