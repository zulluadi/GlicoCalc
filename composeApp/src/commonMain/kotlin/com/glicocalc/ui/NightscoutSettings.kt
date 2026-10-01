package com.glicocalc.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.glicocalc.database.GlicoRepository
import com.glicocalc.sync.NightscoutFoodExporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun NightscoutSettings(repository: GlicoRepository, tokenState: MutableState<String>, tokenStore: com.glicocalc.sync.NightscoutTokenStore) {
    var url by remember(repository) { mutableStateOf(repository.getNightscoutUrl()) }
    // Tokens are stored in the platform vault, separately from the database and family sync.
    var token by tokenState
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val resolveFoodName = rememberBaseFoodNameResolver()
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Nightscout", style = MaterialTheme.typography.titleMedium)
        Text("Export active foods to Nightscout. Repeat exports update the same foods. Names use your selected food language. Exported carbs per 100 g are rounded to whole grams for AAPS compatibility. Deleted foods are not removed from Nightscout.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(value = url, onValueChange = { url = it; message = null },
            label = { Text("Nightscout URL") }, placeholder = { Text("https://your-site.example") },
            singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = token, onValueChange = { token = it; message = null },
            label = { Text("Access token") }, visualTransformation = PasswordVisualTransformation(),
            singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
        Text("Create a token in Nightscout Admin with food read, create, and update permissions. The URL and token are saved on this device.", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !busy, onClick = {
                try {
                    url = NightscoutFoodExporter.normalizeUrl(url)
                    tokenStore.save(url, token.trim())
                    repository.saveNightscoutUrl(url)
                    message = "Nightscout configuration saved."
                } catch (e: IllegalArgumentException) { message = e.message
                } catch (_: Exception) { message = "Could not save the Nightscout token. Please retry." }
            }) { Text("Save") }
            Button(enabled = !busy && url.isNotBlank() && token.isNotBlank(), onClick = {
                busy = true
                message = null
                scope.launch {
                    try {
                        val site = NightscoutFoodExporter.normalizeUrl(url)
                        tokenStore.save(site, token.trim())
                        repository.saveNightscoutUrl(site)
                        val count = NightscoutFoodExporter.export(repository, site, token, resolveFoodName)
                        message = "Exported and verified $count foods in Nightscout with whole-gram carbs."
                    } catch (e: CancellationException) { throw e
                    } catch (e: Exception) { message = e.message ?: "Export failed. Please retry."
                    } finally { busy = false }
                }
            }) { Text(if (busy) "Exporting…" else "Export foods") }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
