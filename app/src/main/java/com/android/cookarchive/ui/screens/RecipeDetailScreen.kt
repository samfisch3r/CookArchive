package com.android.cookarchive.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.android.cookarchive.data.entities.Ingredient
import com.android.cookarchive.data.entities.RecipeWithDetails
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    recipeWithDetails: RecipeWithDetails,
    plannedDates: Set<String> = emptySet(),
    onStartCooking: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddToMenuPlan: (String, List<Ingredient>, Double) -> Unit,
    onBack: () -> Unit
) {
    val baseServings = recipeWithDetails.recipe.defaultServings.coerceAtLeast(1)
    var servings by remember { mutableIntStateOf(baseServings.coerceIn(1, 6)) }
    val scaleFactor = servings.toDouble() / baseServings

    var showMenuPlanDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(recipeWithDetails.recipe.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { showMenuPlanDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add to Menu")
                    }

                    Button(
                        onClick = onStartCooking,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Restaurant, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Cooking")
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp)
        ) {
            // Main Hero Image
            item {
                if (!recipeWithDetails.recipe.imagePath.isNullOrBlank()) {
                    AsyncImage(
                        model = recipeWithDetails.recipe.imagePath,
                        contentDescription = recipeWithDetails.recipe.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Category & Description
            item {
                Text(
                    text = recipeWithDetails.recipe.category.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (recipeWithDetails.recipe.description.isNotBlank()) {
                    Text(
                        text = recipeWithDetails.recipe.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                // Cook Tracking Stats
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val count = recipeWithDetails.recipe.cookCount
                        val lastCookedText = if (recipeWithDetails.recipe.lastCooked == 0L) {
                            "Never"
                        } else {
                            Instant.ofEpochMilli(recipeWithDetails.recipe.lastCooked)
                                .atZone(ZoneId.systemDefault())
                                .format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US))
                        }

                        Text(
                            text = "Cooked: $count times",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Last: $lastCookedText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Adjust Servings:", fontWeight = FontWeight.Bold)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Slider(
                        value = servings.toFloat(),
                        onValueChange = { servings = it.toInt() },
                        valueRange = 1f..6f,
                        steps = 4,
                        modifier = Modifier.weight(1f)
                    )
                    Text(text = "$servings Servings", modifier = Modifier.padding(start = 8.dp))
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            }

            // Ingredients Section
            item {
                Text(
                    text = "Ingredients",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(recipeWithDetails.ingredients) { ingredient ->
                val scaledQty = ingredient.quantity * scaleFactor
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = ingredient.name,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = formatQuantity(scaledQty, ingredient.unit),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            // Steps Section
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                Text(
                    text = "Instructions",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(recipeWithDetails.steps) { step ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = "Step ${step.stepNumber}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (!step.stepImagePath.isNullOrBlank()) {
                        AsyncImage(
                            model = step.stepImagePath,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .padding(bottom = 8.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Text(text = step.instructionText)
                }
            }
        }

        if (showMenuPlanDialog) {
            AddToMenuPlanDialog(
                recipeWithDetails = recipeWithDetails,
                plannedDates = plannedDates,
                scaleFactor = scaleFactor,
                onDismiss = { showMenuPlanDialog = false },
                onConfirm = { date, selectedIngredients ->
                    onAddToMenuPlan(date, selectedIngredients, scaleFactor)
                    showMenuPlanDialog = false
                }
            )
        }
    }
}

@Composable
private fun AddToMenuPlanDialog(
    recipeWithDetails: RecipeWithDetails,
    plannedDates: Set<String>,
    scaleFactor: Double,
    onDismiss: () -> Unit,
    onConfirm: (String, List<Ingredient>) -> Unit
) {
    val daySlots = remember { getNext5Days() }
    var selectedIsoDate by remember { mutableStateOf(daySlots.first().isoDate) }

    // Ingredients start UNCHECKED by default as requested
    val checkedMap = remember {
        mutableStateMapOf<Long, Boolean>().apply {
            recipeWithDetails.ingredients.forEach { ing ->
                put(ing.id, false)
            }
        }
    }

    val allChecked = recipeWithDetails.ingredients.isNotEmpty() &&
            recipeWithDetails.ingredients.all { checkedMap[it.id] == true }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Menu Plan") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                Text(
                    text = "Select Day:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Days Selection (2 Rows)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        daySlots.take(3).forEach { slot ->
                            val hasPlannedMeals = slot.isoDate in plannedDates
                            FilterChip(
                                selected = selectedIsoDate == slot.isoDate,
                                onClick = { selectedIsoDate = slot.isoDate },
                                label = {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = slot.displayTitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = slot.formattedDateStr,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                },
                                colors = if (hasPlannedMeals && selectedIsoDate != slot.isoDate) {
                                    FilterChipDefaults.filterChipColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                } else {
                                    FilterChipDefaults.filterChipColors()
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        daySlots.drop(3).forEach { slot ->
                            val hasPlannedMeals = slot.isoDate in plannedDates
                            FilterChip(
                                selected = selectedIsoDate == slot.isoDate,
                                onClick = { selectedIsoDate = slot.isoDate },
                                label = {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = slot.displayTitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = slot.formattedDateStr,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                },
                                colors = if (hasPlannedMeals && selectedIsoDate != slot.isoDate) {
                                    FilterChipDefaults.filterChipColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                } else {
                                    FilterChipDefaults.filterChipColors()
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Add to Shopping List?",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    TextButton(onClick = {
                        val newValue = !allChecked
                        recipeWithDetails.ingredients.forEach { ing ->
                            checkedMap[ing.id] = newValue
                        }
                    }) {
                        Text(if (allChecked) "Deselect All" else "Select All")
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f)
                ) {
                    items(recipeWithDetails.ingredients, key = { it.id }) { ing ->
                        val scaledQty = ing.quantity * scaleFactor
                        val isChecked = checkedMap[ing.id] == true

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { checkedMap[ing.id] = !isChecked },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checkedMap[ing.id] = it }
                            )
                            Text(
                                text = ing.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = formatQuantity(scaledQty, ing.unit),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val selectedIngredients = recipeWithDetails.ingredients.filter {
                        checkedMap[it.id] == true
                    }
                    onConfirm(selectedIsoDate, selectedIngredients)
                }
            ) {
                Text("Add to Plan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatQuantity(quantity: Double, unit: String): String {
    if (quantity <= 0.0) return unit
    val formattedQty = if (quantity % 1.0 == 0.0) {
        quantity.toInt().toString()
    } else {
        String.format(Locale.US, "%.1f", quantity)
    }
    return if (unit.isBlank()) formattedQty else "$formattedQty $unit"
}
