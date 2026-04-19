package com.glicocalc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.glicocalc.database.DatabaseDriverFactory
import com.glicocalc.database.GlicoRepository
import com.glicocalc.database.GlicoDatabase
import com.glicocalc.telemetry.NoopTelemetry
import com.glicocalc.ui.MainApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Inițializare Bază de Date prin Factory-ul de Android
        val driverFactory = DatabaseDriverFactory(this)
        val driver = driverFactory.createDriver()
        val database = GlicoDatabase(driver)
        val repository = GlicoRepository(database)
        
        // Seed initial data if empty
        repository.seedInitialData()
        
        setContent {
            MainApp(
                repository = repository,
                telemetry = NoopTelemetry
            )
        }
    }
}
