package com.glicocalc.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.glicocalc.database.MealType
import com.glicocalc.ui.components.MealTypeEditorDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealTypesScreen(
    mealTypes: List<MealType>,
    onBack: () -> Unit,
    onAddMealType: (name: String, targetCarbs: Double, hourOfDay: Int) -> Unit,
    onEditMealType: (id: Long, name: String, targetCarbs: Double, hourOfDay: Int) -> Unit,
    onDeleteMealType: (id: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val resolveMealTypeName = rememberMealTypeNameResolver()
    var showAddMealTypeDialog by remember { mutableStateOf(false) }
    var mealTypeToEdit by remember { mutableStateOf<MealType?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Strings.mealTypes()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Strings.close())
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddMealTypeDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = Strings.addMealType())
            }
        },
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            item {
                ListItem(
                    headlineContent = { Text(Strings.mealTypes()) },
                    supportingContent = {
                        Text(
                            if (mealTypes.isEmpty()) {
                                Strings.noMealTypesConfigured()
                            } else {
                                "${Strings.mealTypesDescription()} ${Strings.mealTypesCarePlanNote()}"
                            }
                        )
                    }
                )
                HorizontalDivider()
            }
            items(mealTypes) { mealType ->
                ListItem(
                    headlineContent = { Text(resolveMealTypeName(mealType.name)) },
                    supportingContent = {
                        Text("${formatHour(mealType.hourOfDay.toInt())} • ${formatDecimal(mealType.targetCarbs)}g")
                    },
                    leadingContent = {
                        IconButton(onClick = { onDeleteMealType(mealType.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = Strings.delete())
                        }
                    },
                    trailingContent = {
                        IconButton(onClick = { mealTypeToEdit = mealType }) {
                            Icon(Icons.Default.Edit, contentDescription = Strings.edit())
                        }
                    },
                    modifier = Modifier.clickable { mealTypeToEdit = mealType }
                )
                HorizontalDivider()
            }
        }

        if (showAddMealTypeDialog) {
            MealTypeEditorDialog(
                onDismiss = { showAddMealTypeDialog = false },
                onConfirm = onAddMealType
            )
        }

        mealTypeToEdit?.let { mealType ->
            MealTypeEditorDialog(
                initialName = resolveMealTypeName(mealType.name),
                initialTargetCarbs = formatDecimal(mealType.targetCarbs),
                initialHourOfDay = mealType.hourOfDay.toString(),
                onDismiss = { mealTypeToEdit = null },
                onConfirm = { name, targetCarbs, hourOfDay ->
                    onEditMealType(mealType.id, name, targetCarbs, hourOfDay)
                }
            )
        }
    }
}
