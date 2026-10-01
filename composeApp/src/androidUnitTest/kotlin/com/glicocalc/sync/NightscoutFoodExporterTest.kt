package com.glicocalc.sync

import com.glicocalc.database.*
import com.google.gson.Gson
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class NightscoutFoodExporterTest {
    private data class AapsFood(val carbs: Int)

    @Test fun aapsParserRejectsFractionalCarbs() {
        assertFails { Gson().fromJson("{\"carbs\":42.6}", AapsFood::class.java) }
    }

    @Test fun rejectsInsecureAndCredentialBearingUrls() {
        for (url in listOf("http://ns.example", "https://user:secret@ns.example", "https://ns.example?token=secret")) {
            assertFailsWith<IllegalArgumentException> { NightscoutFoodExporter.normalizeUrl(url) }
        }
        assertEquals("https://ns.example", NightscoutFoodExporter.normalizeUrl(" https://ns.example/api/v1/ "))
    }

    @Test fun exportsRomanianNamesAndUpdatesWithoutDuplicates() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        GlicoDatabase.Schema.create(driver)
        val db = GlicoDatabase(driver)
        val repository = GlicoRepository(db)
        db.glicoDatabaseQueries.insertBaseFood("White Bread", 49.0, "default-bread", "default", 0, 0, 0, 0, null, null)
        repository.insertBaseFood("My bread", 42.6)
        repository.insertBaseFood("Deleted food", 12.0)
        repository.deleteBaseFood(repository.getAllBaseFoodsIncludingDeleted().last().id)
        val remote = mutableMapOf<String, JsonObject>()
        val methods = mutableListOf<HttpMethod>()
        val engine = MockEngine { request ->
            assertEquals("session-token", request.url.parameters["token"])
            if (request.method == HttpMethod.Get) {
                respond(JsonArray(remote.values.toList()).toString(), headers = headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                methods += request.method
                val food = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
                // Use AAPS's Gson + Int contract, not just our own JSON reader.
                val aapsFood = Gson().fromJson(food.toString(), AapsFood::class.java)
                assertEquals(food.getValue("carbs").jsonPrimitive.int, aapsFood.carbs)
                remote[food.getValue("_id").jsonPrimitive.content] = food
                respond("[]", headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        fun client() = HttpClient(engine)
        val ro: (String) -> String = { DefaultFoodNames.romanianName(it) ?: it }
        assertEquals(2, NightscoutFoodExporter.export(repository, "https://ns.example", "session-token", ro, ::client))
        assertEquals(setOf("Pâine Albă", "My bread"), remote.values.map { it.getValue("name").jsonPrimitive.content }.toSet())
        assertTrue(remote.values.all { it.getValue("portion").jsonPrimitive.int == 100 && it.getValue("unit").jsonPrimitive.content == "g" })
        assertTrue(remote.values.all {
            it.getValue("category").jsonPrimitive.content == "" &&
                it.getValue("subcategory").jsonPrimitive.content == ""
        })
        val categorizedId = remote.keys.first()
        remote[categorizedId] = JsonObject(remote.getValue(categorizedId) + mapOf(
            "category" to JsonPrimitive("Cereale"), "subcategory" to JsonPrimitive("Pâine")
        ))
        assertEquals(43, remote.values.first { it.getValue("name").jsonPrimitive.content == "My bread" }.getValue("carbs").jsonPrimitive.int)
        assertEquals(42.6, repository.getAllBaseFoodsIncludingDeleted().first { it.name == "My bread" }.carbsPer100g)
        val ids = remote.keys.toSet()
        NightscoutFoodExporter.export(repository, "https://ns.example", "session-token", { it }, ::client)
        assertEquals(ids, remote.keys)
        assertEquals("Cereale", remote.getValue(categorizedId).getValue("category").jsonPrimitive.content)
        assertEquals("Pâine", remote.getValue(categorizedId).getValue("subcategory").jsonPrimitive.content)
        assertEquals(listOf(HttpMethod.Put, HttpMethod.Put, HttpMethod.Put, HttpMethod.Put), methods)
        assertTrue(remote.values.any { it.getValue("name").jsonPrimitive.content == "White Bread" })
        // Nightscout must not clear the independent family sync queue.
        assertTrue(repository.getBaseFoodsNeedingSync().isNotEmpty())
        driver.close()
    }

    @Test fun exportsGiBandsAndUpdatesExistingGi() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        GlicoDatabase.Schema.create(driver)
        val repository = GlicoRepository(GlicoDatabase(driver))
        for (level in listOf("low", "medium", "high", "unspecified", null)) {
            repository.insertBaseFood(level ?: "Unknown", 10.0, glycemicIndexLevel = level)
        }
        val remote = mutableMapOf<String, JsonObject>()
        val engine = MockEngine { request ->
            if (request.method == HttpMethod.Get) {
                respond(JsonArray(remote.values.toList()).toString())
            } else {
                val record = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
                remote[record.getValue("_id").jsonPrimitive.content] = record
                respond("[]")
            }
        }
        fun client() = HttpClient(engine)
        suspend fun export() = NightscoutFoodExporter.export(repository, "https://ns.example", "token", { it }, ::client)
        export()
        val byName = remote.values.associateBy { it.getValue("name").jsonPrimitive.content }
        assertEquals(1, byName.getValue("low").getValue("gi").jsonPrimitive.int)
        assertEquals(2, byName.getValue("medium").getValue("gi").jsonPrimitive.int)
        assertEquals(3, byName.getValue("high").getValue("gi").jsonPrimitive.int)
        assertFalse(byName.getValue("unspecified").containsKey("gi"))
        assertFalse(byName.getValue("Unknown").containsKey("gi"))
        val low = repository.getAllBaseFoodsIncludingDeleted().first { it.name == "low" }
        repository.updateBaseFood(low.id, low.name, low.carbsPer100g, glycemicIndexLevel = "high")
        val unspecifiedId = remote.entries.first { it.value.getValue("name").jsonPrimitive.content == "unspecified" }.key
        remote[unspecifiedId] = JsonObject(remote.getValue(unspecifiedId) + ("gi" to JsonPrimitive(2)))
        val ids = remote.keys.toSet()
        export()
        assertEquals(ids, remote.keys)
        assertEquals(3, remote.values.first { it.getValue("name").jsonPrimitive.content == "low" }.getValue("gi").jsonPrimitive.int)
        assertEquals(2, remote.getValue(unspecifiedId).getValue("gi").jsonPrimitive.int)
        driver.close()
    }


    @Test fun detectsServerKeepingOldFractionalCarbs() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        GlicoDatabase.Schema.create(driver)
        val repository = GlicoRepository(GlicoDatabase(driver))
        repository.insertBaseFood("Food", 42.6)
        val food = repository.getAllBaseFoodsIncludingDeleted().single()
        val id = repository.nightscoutFoodId("https://ns.example", food)
        val engine = MockEngine { request ->
            if (request.method == HttpMethod.Get) {
                respond("[{\"_id\":\"$id\",\"carbs\":42.6,\"category\":\"\",\"subcategory\":\"\"}]")
            } else respond("[]") // Successful HTTP status alone must not count as success.
        }
        val failure = assertFailsWith<NightscoutException> {
            NightscoutFoodExporter.export(repository, "https://ns.example", "token", { it }) { HttpClient(engine) }
        }
        assertTrue(failure.message.orEmpty().contains("did not retain"))
        driver.close()
    }

}
