package com.glicocalc.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun FoodEditorDialog(
    initialName: String = "",
    initialCarbs: String = "",
    onDismiss: () -> Unit,
    onConfirm: (name: String, carbs: Double) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var carbsText by remember { mutableStateOf(initialCarbs) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialName.isEmpty()) "Adaugă Aliment" else "Editează Aliment") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nume Aliment") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = carbsText,
                    onValueChange = { if (it.all { char -> char.isDigit() || char == '.' }) carbsText = it },
                    label = { Text("Glucide per 100g") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    suffix = { Text("g") }
                )
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && carbsText.toDoubleOrNull() != null,
                onClick = {
                    onConfirm(name, carbsText.toDouble())
                    onDismiss()
                }
            ) {
                Text("Salvează")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Anulează")
            }
        }
    )
}
