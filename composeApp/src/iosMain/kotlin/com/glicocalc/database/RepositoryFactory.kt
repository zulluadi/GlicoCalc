package com.glicocalc.database

import com.glicocalc.database.DatabaseDriverFactory
import com.glicocalc.database.GlicoDatabase
import com.glicocalc.database.GlicoRepository

object RepositoryFactory {
    fun create(): GlicoRepository {
        val driver = DatabaseDriverFactory().createDriver()
        return GlicoRepository(GlicoDatabase(driver), driver).also {
            it.migrateSchemaIfNeeded()
            it.seedInitialData()
            it.prepareBaseFoodCatalog()
        }
    }
}
