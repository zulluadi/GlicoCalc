package com.glicocalc.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.glicocalc.database.FamilyMember
import com.glicocalc.database.GlicoRepository
import com.glicocalc.telemetry.Telemetry
import com.glicocalc.ui.theme.GlicoCalcTheme
import kotlinx.coroutines.launch

@Composable
fun MainApp(
    repository: GlicoRepository,
    telemetry: Telemetry,
    familyMembers: List<FamilyMember> = emptyList(),
    familyId: String? = null,
    familyName: String? = null,
    currentUserEmail: String? = null,
    isFamilyOwner: Boolean = false,
    pendingFamilyInviteLabel: String? = null,
    isSignedIn: Boolean = false,
    syncStatusMessage: String? = null,
    lastSyncedMessage: String? = null,
    syncIntervalMinutes: Int = 10,
    onSignInToSync: (() -> Unit)? = null,
    onSignOutFromSync: (() -> Unit)? = null,
    onManualSync: (() -> Unit)? = null,
    onSyncIntervalChanged: ((Int) -> Unit)? = null,
    onScanFamilyQr: (() -> Unit)? = null,
    onFamilyQrDialogClosed: (() -> Unit)? = null,
    onAddFamilyMember: ((email: String, name: String) -> Unit)? = null,
    onRemoveFamilyMember: ((email: String) -> Unit)? = null,
    onUpdateFamilyName: ((String?) -> Unit)? = null,
    onLeaveFamily: (() -> Unit)? = null,
    onJoinPendingFamilyInvite: (() -> Unit)? = null,
    onJoinFamilyById: ((String) -> Unit)? = null,
    onPermanentlyDeleteFood: ((Long) -> Unit)? = null,
    onPermanentlyDeleteDish: ((Long) -> Unit)? = null,
    resumeSignal: Int = 0
) {
    LaunchedEffect(repository) {
        repository.migrateSchemaIfNeeded()
        if (!hasLoadedPersistedAppLocale) {
            customAppLocale = repository.getLanguage()
            hasLoadedPersistedAppLocale = true
        }
        if (!hasLoadedPersistedFoodLocale) {
            customFoodLocale = repository.getFoodLanguage()
            hasLoadedPersistedFoodLocale = true
        }
    }

    AppEnvironment {
        val scope = rememberCoroutineScope()
        var currentScreen by remember { mutableStateOf(Screen.Calculator) }
        var editingDishId by remember { mutableStateOf<Long?>(null) }
        var showLanguageDialog by remember { mutableStateOf(false) }
        var showFoodLanguageDialog by remember { mutableStateOf(false) }

        val baseFoods by repository.getAllBaseFoods().collectAsState(initial = emptyList())
        val allBaseFoods by repository.getAllBaseFoodsIncludingDeletedFlow().collectAsState(initial = emptyList())
        val dishes by repository.getAllDishes().collectAsState(initial = emptyList())
        val allDishes by repository.getAllDishesIncludingDeletedFlow().collectAsState(initial = emptyList())
        val mealTypes by repository.getAllMealTypes().collectAsState(initial = emptyList())

        LaunchedEffect(currentScreen) {
            telemetry.screenViewed(currentScreen.name)
        }

        GlicoCalcTheme {
            val density = LocalDensity.current
            val isKeyboardVisible = WindowInsets.ime.getBottom(density) > 0

            Scaffold(
                contentWindowInsets = WindowInsets(0.dp),
                bottomBar = {
                    if (!isKeyboardVisible) {
                        NavigationBar(
                            windowInsets = WindowInsets(0.dp)
                        ) {
                            NavigationBarItem(
                                selected = currentScreen == Screen.Calculator,
                                onClick = { currentScreen = Screen.Calculator },
                                icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                                label = { Text(Strings.navCalculator()) }
                            )
                            NavigationBarItem(
                                selected = currentScreen == Screen.Dishes || currentScreen == Screen.DishEditor,
                                onClick = { currentScreen = Screen.Dishes },
                                icon = { Icon(Icons.Default.Menu, contentDescription = null) },
                                label = { Text(Strings.navDishes()) }
                            )
                            NavigationBarItem(
                                selected = currentScreen == Screen.FoodList,
                                onClick = { currentScreen = Screen.FoodList },
                                icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                                label = { Text(Strings.navFoods()) }
                            )
                            NavigationBarItem(
                                selected = currentScreen == Screen.Settings ||
                                    currentScreen == Screen.MealTypes ||
                                    currentScreen == Screen.DeletedItems ||
                                    currentScreen == Screen.FamilyManager,
                                onClick = { currentScreen = Screen.Settings },
                                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                label = { Text(Strings.settings()) }
                            )
                        }
                    }
                }
            ) { padding ->
                val modifier = Modifier.padding(padding)

                when (currentScreen) {
                    Screen.Calculator -> CalculatorScreen(
                        repository = repository,
                        dishes = dishes,
                        baseFoods = baseFoods,
                        mealTypes = mealTypes,
                        onSelectDish = { repository.getDishWithComposition(it) },
                        onSelectBaseFood = { repository.getBaseFood(it) },
                        resumeSignal = resumeSignal,
                        modifier = modifier
                    )
                    Screen.FoodList -> FoodListScreen(
                        foods = baseFoods,
                        onAddFood = { name, carbs, isPacked, packWeight, packCount ->
                            telemetry.action("food_added")
                            scope.launch { repository.insertBaseFood(name, carbs, isPacked, packWeight, packCount) }
                        },
                        onEditFood = { id, name, carbs, isPacked, packWeight, packCount ->
                            telemetry.action("food_edited")
                            scope.launch { repository.updateBaseFood(id, name, carbs, isPacked, packWeight, packCount) }
                        },
                        onDeleteFood = {
                            telemetry.action("food_deleted")
                            scope.launch { repository.deleteBaseFood(it) }
                        },
                        onUndeleteFood = { id ->
                            telemetry.action("food_restored")
                            scope.launch { repository.restoreBaseFood(id) }
                        },
                        modifier = modifier
                    )
                    Screen.Dishes -> {
                        val dishesWithCarbs = remember(dishes, baseFoods) { repository.getAllDishesWithCarbs() }
                        DishListScreen(
                            dishesWithCarbs = dishesWithCarbs,
                            onAddDish = {
                                telemetry.action("dish_editor_opened_new")
                                currentScreen = Screen.DishEditor
                                editingDishId = null
                            },
                            onEditDish = { id ->
                                telemetry.action("dish_editor_opened_existing")
                                currentScreen = Screen.DishEditor
                                editingDishId = id
                            },
                            onDeleteDish = {
                                telemetry.action("dish_deleted")
                                scope.launch { repository.deleteDish(it) }
                            },
                            onUndeleteDish = {
                                telemetry.action("dish_restored")
                                scope.launch { repository.restoreDish(it) }
                            },
                            modifier = modifier
                        )
                    }
                    Screen.DishEditor -> {
                        val initialDish = editingDishId?.let { repository.getDishWithComposition(it) }
                        DishEditorScreen(
                            initialName = initialDish?.dish?.name ?: "",
                            initialTotalCookedWeight = initialDish?.dish?.totalCookedWeight,
                            initialComponents = initialDish?.components?.map { it.baseFoodId to it.weightGrams } ?: emptyList(),
                            allBaseFoods = baseFoods,
                            onCancel = { currentScreen = Screen.Dishes },
                            onSave = { name, totalCookedWeight, components ->
                                telemetry.action("dish_saved")
                                scope.launch {
                                    if (editingDishId == null) {
                                        repository.insertDishWithComponents(name, totalCookedWeight, components)
                                    } else {
                                        repository.updateDishWithComponents(editingDishId!!, name, totalCookedWeight, components)
                                    }
                                    currentScreen = Screen.Dishes
                                }
                            }
                        )
                    }
                    Screen.Settings -> SettingsScreen(
                        selectedLanguage = customAppLocale,
                        selectedFoodLanguage = customFoodLocale,
                        familyMembers = familyMembers,
                        syncStatusMessage = syncStatusMessage,
                        lastSyncedMessage = lastSyncedMessage,
                        isSignedIn = isSignedIn,
                        onOpenLanguagePicker = { showLanguageDialog = true },
                        onOpenFoodLanguagePicker = { showFoodLanguageDialog = true },
                        onSignInToSync = onSignInToSync,
                        onOpenFamilyManager = { currentScreen = Screen.FamilyManager },
                        onOpenMealTypes = { currentScreen = Screen.MealTypes },
                        onOpenDeletedItems = { currentScreen = Screen.DeletedItems },
                        modifier = modifier
                    )
                    Screen.DeletedItems -> DeletedItemsScreen(
                        deletedFoods = remember(allBaseFoods) {
                            allBaseFoods.filter { it.isDeleted != 0L }
                        },
                        deletedDishes = remember(allDishes) {
                            allDishes.filter { it.isDeleted != 0L }
                        },
                        onRestoreFood = { id ->
                            telemetry.action("food_restored")
                            scope.launch { repository.restoreBaseFood(id) }
                        },
                        onRestoreDish = { id ->
                            telemetry.action("dish_restored")
                            scope.launch { repository.restoreDish(id) }
                        },
                        onPermanentlyDeleteFood = { id ->
                            telemetry.action("food_permanently_deleted")
                            onPermanentlyDeleteFood?.invoke(id)
                                ?: scope.launch { repository.permanentlyDeleteBaseFood(id) }
                        },
                        onPermanentlyDeleteDish = { id ->
                            telemetry.action("dish_permanently_deleted")
                            onPermanentlyDeleteDish?.invoke(id)
                                ?: scope.launch { repository.permanentlyDeleteDish(id) }
                        },
                        onBack = { currentScreen = Screen.Settings },
                        modifier = modifier
                    )
                    Screen.FamilyManager -> FamilyManagerScreen(
                        familyMembers = familyMembers,
                        familyId = familyId,
                        familyName = familyName,
                        currentUserEmail = currentUserEmail,
                        isFamilyOwner = isFamilyOwner,
                        pendingFamilyInviteLabel = pendingFamilyInviteLabel,
                        syncStatusMessage = syncStatusMessage,
                        lastSyncedMessage = lastSyncedMessage,
                        syncIntervalMinutes = syncIntervalMinutes,
                        isSignedIn = isSignedIn,
                        onAddMember = { email, name ->
                            telemetry.action("family_member_added")
                            onAddFamilyMember?.invoke(email, name)
                        },
                        onRemoveMember = { email ->
                            telemetry.action("family_member_removed")
                            onRemoveFamilyMember?.invoke(email)
                        },
                        onUpdateFamilyName = { name ->
                            telemetry.action("family_name_updated")
                            onUpdateFamilyName?.invoke(name)
                        },
                        onLeaveFamily = {
                            telemetry.action("family_left")
                            onLeaveFamily?.invoke()
                        },
                        onJoinPendingFamilyInvite = {
                            telemetry.action("family_invite_joined")
                            onJoinPendingFamilyInvite?.invoke()
                        },
                        onJoinFamilyById = { familyId ->
                            telemetry.action("family_joined_by_id")
                            onJoinFamilyById?.invoke(familyId)
                        },
                        onSignIn = { onSignInToSync?.invoke() },
                        onSignOut = { onSignOutFromSync?.invoke() },
                        onManualSync = onManualSync,
                        onSyncIntervalChanged = { minutes ->
                            telemetry.action("sync_interval_changed")
                            onSyncIntervalChanged?.invoke(minutes)
                        },
                        onScanFamilyQr = onScanFamilyQr?.let { scan ->
                            {
                                telemetry.action("family_qr_scan_opened")
                                scan()
                            }
                        },
                        onFamilyQrDialogClosed = {
                            telemetry.action("family_qr_dialog_closed")
                            onFamilyQrDialogClosed?.invoke()
                        },
                        onBack = { currentScreen = Screen.Settings },
                        modifier = modifier
                    )
                    Screen.MealTypes -> MealTypesScreen(
                        mealTypes = mealTypes,
                        onBack = { currentScreen = Screen.Settings },
                        onAddMealType = { name, targetCarbs, hourOfDay ->
                            telemetry.action("meal_type_added")
                            scope.launch { repository.insertMealType(name, targetCarbs, hourOfDay.toLong()) }
                        },
                        onEditMealType = { id, name, targetCarbs, hourOfDay ->
                            telemetry.action("meal_type_edited")
                            scope.launch { repository.updateMealType(id, name, targetCarbs, hourOfDay.toLong()) }
                        },
                        onDeleteMealType = { id ->
                            telemetry.action("meal_type_deleted")
                            scope.launch { repository.deleteMealType(id) }
                        },
                        modifier = modifier
                    )
                }
            }

            if (showLanguageDialog) {
                LanguageDialog(
                    title = Strings.language(),
                    selectedLanguage = customAppLocale,
                    options = appLanguageOptions,
                    defaultLabel = Strings.systemDefault(),
                    onDismiss = { showLanguageDialog = false },
                    onSelectLanguage = {
                        customAppLocale = it
                        repository.saveLanguage(it)
                        showLanguageDialog = false
                    }
                )
            }

            if (showFoodLanguageDialog) {
                LanguageDialog(
                    title = Strings.foodLanguage(),
                    selectedLanguage = customFoodLocale,
                    options = foodLanguageOptions,
                    defaultLabel = Strings.sameAsAppLanguage(),
                    onDismiss = { showFoodLanguageDialog = false },
                    onSelectLanguage = {
                        customFoodLocale = it
                        repository.saveFoodLanguage(it)
                        showFoodLanguageDialog = false
                    }
                )
            }
        }
    }
}

enum class Screen {
    Calculator, FoodList, Dishes, DishEditor, Settings, MealTypes, DeletedItems, FamilyManager
}

@Composable
private fun LanguageDialog(
    title: String,
    selectedLanguage: String?,
    options: List<AppLanguageOption>,
    defaultLabel: String,
    onDismiss: () -> Unit,
    onSelectLanguage: (String?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn {
                items(options) { option ->
                    val label = if (option.code == null) defaultLabel else option.label
                    TextButton(onClick = { onSelectLanguage(option.code) }) {
                        Text(if (selectedLanguage == option.code) "• $label" else label)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Strings.cancel())
            }
        }
    )
}
