package com.glicocalc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.glicocalc.database.DatabaseDriverFactory
import com.glicocalc.database.GlicoRepository
import com.glicocalc.database.GlicoDatabase
import com.glicocalc.telemetry.NoopTelemetry
import com.glicocalc.ui.MainApp
import com.glicocalc.ui.customAppLocale
import com.glicocalc.ui.customFoodLocale
import com.glicocalc.ui.hasLoadedPersistedAppLocale
import com.glicocalc.ui.hasLoadedPersistedFoodLocale

class MainActivity : ComponentActivity() {
    private var resumeSignal by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Inițializare Bază de Date prin Factory-ul de Android
        val driverFactory = DatabaseDriverFactory(this)
        val driver = driverFactory.createDriver()
        val database = GlicoDatabase(driver)
        val repository = GlicoRepository(database)
        
        // Seed initial data if empty
        repository.seedInitialData()

        // Load persisted language
        customAppLocale = repository.getLanguage()
        customFoodLocale = repository.getFoodLanguage()
        hasLoadedPersistedAppLocale = true
        hasLoadedPersistedFoodLocale = true
        
        setContent {
            MainApp(
                repository = repository,
                telemetry = NoopTelemetry,
                resumeSignal = resumeSignal
            )
        }
    }

    override fun onResume() {
        super.onResume()
        resumeSignal += 1
    }
}
