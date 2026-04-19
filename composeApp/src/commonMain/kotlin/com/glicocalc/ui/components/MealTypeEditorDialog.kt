package com.glicocalc.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.glicocalc.ui.Strings

@Composable
fun MealTypeEditorDialog(
    initialName: String = "",
    initialTargetCarbs: String = "",
    initialHourOfDay: String = "",
    onDismiss: () -> Unit,
    onConfirm: (name: String, targetCarbs: Double, hourOfDay: Int) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var targetCarbsText by remember { mutableStateOf(initialTargetCarbs) }
    var hourOfDayText by remember { mutableStateOf(initialHourOfDay) }

    val parsedHour = hourOfDayText.toIntOrNull()
    val isValid = name.isNotBlank() &&
        targetCarbsText.toDoubleOrNull() != null &&
        parsedHour != null &&
        parsedHour in 0..23

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialName.isEmpty()) Strings.addMealTypeTitle() else Strings.editMealTypeTitle())
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(Strings.mealTypeName()) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = targetCarbsText,
                    onValueChange = {
                        if (it.all { char -> char.isDigit() || char == '.' }) {
                            targetCarbsText = it
                        }
                    },
                    label = { Text(Strings.mealTargetCarbs()) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    suffix = { Text("g") }
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = hourOfDayText,
                    onValueChange = {
                        if (it.all(Char::isDigit) && it.length <= 2) {
                            hourOfDayText = it
                        }
                    },
                    label = { Text(Strings.mealHour()) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text(Strings.mealHourHint()) }
                )
            }
        },
        confirmButton = {
            Button(
                enabled = isValid,
                onClick = {
                    onConfirm(name, targetCarbsText.toDouble(), parsedHour!!)
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
