package com.glicocalc.sync

import com.glicocalc.database.GlicoRepository
import com.glicocalc.database.GlycemicIndexLevel
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

object NightscoutFoodExporter {
    fun normalizeUrl(input: String): String {
        val url = Url(input.trim().trimEnd('/').removeSuffix("/api/v1"))
        require(url.protocol == URLProtocol.HTTPS && url.host.isNotBlank() &&
            url.user.isNullOrEmpty() && url.password.isNullOrEmpty() && url.parameters.isEmpty() && url.fragment.isEmpty()) {
            "Enter an HTTPS Nightscout site URL without credentials or query parameters."
        }
        return url.toString().trimEnd('/')
    }

    suspend fun export(repository: GlicoRepository, site: String, token: String, resolveFoodName: (String) -> String,
        createClient: () -> HttpClient = ::newClient
    ): Int {
        require(token.isNotBlank()) { "Enter a Nightscout access token with food read and write permissions." }
        val base = normalizeUrl(site)
        val foods = repository.foodsForNightscout()
        require(foods.isNotEmpty()) { "There are no active foods to export." }
        // Persist IDs before sending so retries after a lost response remain idempotent.
        val records = foods.map { it to repository.nightscoutFoodId(base, it) }
        val client = createClient()
        var exported = 0
        try {
            val response = client.get("$base/api/v1/food") { parameter("token", token.trim()) }
            checkResponse(response.status)
            val existing = Json.parseToJsonElement(response.bodyAsText()).jsonArray
                .map { it.jsonObject }.associateBy { it["_id"]?.jsonPrimitive?.content }
            for ((food, id) in records) {
                val previous = existing[id]
                val payload = buildJsonObject {
                    previous?.forEach { (key, value) -> put(key, value) }
                    put("_id", id)
                    put("type", "food")
                    put("name", resolveFoodName(food.name))
                    put("portion", 100)
                    put("unit", "g")
                    put("carbs", food.carbsPer100g)
                    // Nightscout stores GI bands as 1/2/3, rather than numeric GI scores.
                    // It has no unspecified band; do not invent one or overwrite a remote value.
                    when (food.glycemicIndexLevel) {
                        GlycemicIndexLevel.LOW.value -> put("gi", 1)
                        GlycemicIndexLevel.MEDIUM.value -> put("gi", 2)
                        GlycemicIndexLevel.HIGH.value -> put("gi", 3)
                    }
                }
                val result = client.request("$base/api/v1/food") {
                    method = if (previous == null) HttpMethod.Post else HttpMethod.Put
                    parameter("token", token.trim())
                    contentType(ContentType.Application.Json)
                    setBody(payload.toString())
                }
                checkResponse(result.status)
                exported++
            }
            return exported
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Never surface request URLs, response bodies, or credentials in the UI.
            val reason = if (e is NightscoutException) e.message else "Check the connection, site URL, and access token."
            throw NightscoutException("Export stopped after $exported of ${foods.size} foods. $reason You can retry safely.")
        } finally {
            client.close()
        }
    }

    private fun newClient() = HttpClient {
        followRedirects = false
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
        }
    }

    private fun checkResponse(status: HttpStatusCode) {
        if (status.value !in 200..299) throw NightscoutException(when (status.value) {
            401, 403 -> "Nightscout denied access. Check the token's food read and write permissions."
            404 -> "The Nightscout food API was not found."
            else -> "Nightscout returned HTTP ${status.value}."
        })
    }
}

class NightscoutException(message: String) : Exception(message)
