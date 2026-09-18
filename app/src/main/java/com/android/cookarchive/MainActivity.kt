package com.android.cookarchive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.cookarchive.ui.RecipeViewModel
import com.android.cookarchive.ui.screens.*
import com.android.cookarchive.ui.theme.CookArchiveTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CookArchiveTheme {
                MainContent()
            }
        }
    }
}

enum class MainTab {
    MenuPlan,
    Recipes,
    ShoppingList
}

sealed class SubScreen {
    object None : SubScreen()
    object Detail : SubScreen()
    object Edit : SubScreen()
    object Cooking : SubScreen()
}

@Composable
fun MainContent(viewModel: RecipeViewModel = viewModel()) {
    val recipes by viewModel.allRecipes.collectAsState(initial = emptyList())
    val mealPlans by viewModel.allMealPlans.collectAsState(initial = emptyList())
    val shoppingItems by viewModel.allShoppingItems.collectAsState(initial = emptyList())

    val currentRecipe by viewModel.currentRecipe.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val recipeWishes by viewModel.recipeWishes.collectAsState()

    var activeTab by remember { mutableStateOf(MainTab.MenuPlan) }
    var activeSubScreen by remember { mutableStateOf<SubScreen>(SubScreen.None) }

    // Handle back gesture / back button press navigation
    BackHandler(enabled = activeSubScreen != SubScreen.None) {
        when (activeSubScreen) {
            SubScreen.Cooking -> activeSubScreen = SubScreen.Detail
            SubScreen.Edit -> activeSubScreen = SubScreen.Detail
            SubScreen.Detail -> activeSubScreen = SubScreen.None
            SubScreen.None -> { /* Handled by enabled check */ }
        }
    }

    BackHandler(enabled = activeSubScreen == SubScreen.None && activeTab != MainTab.Recipes) {
        activeTab = MainTab.Recipes
    }

    Scaffold(
        bottomBar = {
            if (activeSubScreen == SubScreen.None) {
                NavigationBar {
                    NavigationBarItem(
                        selected = activeTab == MainTab.MenuPlan,
                        onClick = { activeTab = MainTab.MenuPlan },
                        icon = { Icon(Icons.Default.CalendarMonth, contentDescription = "Menu Plan") },
                        label = { Text("Menu Plan") }
                    )
                    NavigationBarItem(
                        selected = activeTab == MainTab.Recipes,
                        onClick = { activeTab = MainTab.Recipes },
                        icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Recipes") },
                        label = { Text("Recipes") }
                    )
                    NavigationBarItem(
                        selected = activeTab == MainTab.ShoppingList,
                        onClick = { activeTab = MainTab.ShoppingList },
                        icon = { Icon(Icons.Default.ShoppingCart, contentDescription = "Shopping List") },
                        label = { Text("Shopping List") }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(bottom = padding.calculateBottomPadding())) {
            when (activeSubScreen) {
                is SubScreen.Detail -> {
                    currentRecipe?.let { recipe ->
                        RecipeDetailScreen(
                            recipeWithDetails = recipe,
                            onStartCooking = { activeSubScreen = SubScreen.Cooking },
                            onEdit = { activeSubScreen = SubScreen.Edit },
                            onDelete = {
                                viewModel.deleteRecipe(recipe.recipe)
                                activeSubScreen = SubScreen.None
                                activeTab = MainTab.Recipes
                            },
                            onAddToMenuPlan = { date, selectedIngredients, scaleFactor ->
                                viewModel.addMealPlan(
                                    recipe = recipe.recipe,
                                    date = date,
                                    selectedIngredients = selectedIngredients,
                                    scaleFactor = scaleFactor
                                )
                            },
                            onBack = { activeSubScreen = SubScreen.None }
                        )
                    }
                }
                is SubScreen.Edit -> {
                    currentRecipe?.let { recipe ->
                        EditRecipeScreen(
                            recipeWithDetails = recipe,
                            onSave = { updated ->
                                viewModel.updateRecipe(updated.recipe, updated.ingredients, updated.steps)
                                activeSubScreen = SubScreen.Detail
                            },
                            onCancel = { activeSubScreen = SubScreen.Detail }
                        )
                    }
                }
                is SubScreen.Cooking -> {
                    currentRecipe?.let { recipe ->
                        CookingModeScreen(
                            recipeWithDetails = recipe,
                            onExit = { activeSubScreen = SubScreen.Detail }
                        )
                    }
                }
                is SubScreen.None -> {
                    when (activeTab) {
                        MainTab.MenuPlan -> {
                            LaunchedEffect(Unit) {
                                viewModel.fetchRecipeWishes()
                            }
                            MealPlanScreen(
                                mealPlans = mealPlans,
                                recipeWishes = recipeWishes,
                                onCookRecipe = { recipeId ->
                                    viewModel.loadRecipe(recipeId)
                                    activeSubScreen = SubScreen.Cooking
                                },
                                onAddCustomMealPlan = { customTitle, date ->
                                    viewModel.addCustomMealPlan(customTitle, date)
                                },
                                onMoveMealPlan = { mealPlanId, newDate ->
                                    viewModel.moveMealPlan(mealPlanId, newDate)
                                },
                                onDeleteMealPlan = { mealPlan ->
                                    viewModel.deleteMealPlan(mealPlan)
                                },
                                onDismissWish = { wishId ->
                                    viewModel.dismissWish(wishId)
                                }
                            )
                        }
                        MainTab.Recipes -> {
                            RecipeListScreen(
                                recipes = recipes,
                                isLoading = isLoading,
                                errorEvents = viewModel.errorEvents,
                                onRecipeClick = { id ->
                                    viewModel.loadRecipe(id)
                                    activeSubScreen = SubScreen.Detail
                                },
                                onAddRecipe = { url ->
                                    viewModel.scrapeAndSaveRecipe(url)
                                },
                                onAddRecipesFromImages = { bitmaps ->
                                    viewModel.importRecipeFromImages(bitmaps)
                                }
                            )
                        }
                        MainTab.ShoppingList -> {
                            ShoppingListScreen(
                                shoppingItems = shoppingItems,
                                onToggleBought = { item ->
                                    viewModel.toggleShoppingItemBought(item)
                                },
                                onUpdateItem = { item ->
                                    viewModel.updateShoppingItem(item)
                                },
                                onAddCustomItem = { name, quantity, unit ->
                                    viewModel.addCustomShoppingItem(name, quantity, unit)
                                },
                                onDeleteItem = { item ->
                                    viewModel.deleteShoppingItem(item)
                                },
                                onClearBought = {
                                    viewModel.clearBoughtShoppingItems()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
