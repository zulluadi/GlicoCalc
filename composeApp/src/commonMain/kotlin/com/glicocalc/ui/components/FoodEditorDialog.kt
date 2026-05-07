package com.glicocalc.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.glicocalc.ui.Strings

@Composable
fun FoodEditorDialog(
    initialName: String = "",
    initialCarbs: String = "",
    initialIsPacked: Boolean = false,
    initialPackWeight: String = "",
    initialPackCount: String = "",
    onDismiss: () -> Unit,
    onConfirm: (name: String, carbs: Double, isPacked: Boolean, packWeight: Double?, packCount: Int?) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var carbsText by remember { mutableStateOf(initialCarbs) }
    var isPacked by remember { mutableStateOf(initialIsPacked) }
    var packWeightText by remember { mutableStateOf(initialPackWeight) }
    var packCountText by remember { mutableStateOf(initialPackCount) }

    val isValid = name.isNotBlank() && carbsText.toDoubleOrNull() != null &&
        (!isPacked || (packWeightText.toDoubleOrNull() != null && (packCountText.toIntOrNull() ?: 0) > 0))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialName.isEmpty()) Strings.addFoodTitle() else Strings.editFoodTitle()) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(Strings.foodName()) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = carbsText,
                    onValueChange = { if (it.all { char -> char.isDigit() || char == '.' }) carbsText = it },
                    label = { Text(Strings.carbsPer100gLabel()) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    suffix = { Text("g") }
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(Strings.packedFood())
                    Switch(checked = isPacked, onCheckedChange = { isPacked = it })
                }

                if (isPacked) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = packWeightText,
                        onValueChange = { if (it.all { char -> char.isDigit() || char == '.' }) packWeightText = it },
                        label = { Text(Strings.packTotalWeight()) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        suffix = { Text("g") }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = packCountText,
                        onValueChange = { if (it.all { char -> char.isDigit() }) packCountText = it },
                        label = { Text(Strings.packCount()) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        suffix = { Text(Strings.pcs()) }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = isValid,
                onClick = {
                    val packWeight = if (isPacked) packWeightText.toDoubleOrNull() else null
                    val packCount = if (isPacked) packCountText.toIntOrNull() else null
                    onConfirm(name, carbsText.toDouble(), isPacked, packWeight, packCount)
                    onDismiss()
                }
            ) {
                Text(Strings.save())
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Strings.cancel())
            }
        }
    )
}
