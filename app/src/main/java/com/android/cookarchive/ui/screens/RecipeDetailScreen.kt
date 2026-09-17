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
                        Icon(
                            Icons.Default.Delete, 
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
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
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add to Menu Plan")
                    }

                    Button(
                        onClick = onStartCooking,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Restaurant, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Start Cooking")
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            item {
                if (recipeWithDetails.recipe.imagePath != null) {
                    AsyncImage(
                        model = recipeWithDetails.recipe.imagePath,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentScale = ContentScale.Crop
                    )
                }
                Column(modifier = Modifier.padding(16.dp)) {
                    if (recipeWithDetails.recipe.description.isNotBlank()) {
                        Text(
                            text = recipeWithDetails.recipe.description,
                            style = MaterialTheme.typography.bodyLarge
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
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Ingredients:", style = MaterialTheme.typography.titleLarge)
                }
            }
            
            items(recipeWithDetails.ingredients) { ingredient ->
                val scaledQuantity = ingredient.quantity * scaleFactor
                val formattedQty = if (scaledQuantity > 0) {
                    if (scaledQuantity % 1.0 == 0.0) {
                        "${scaledQuantity.toInt()} ${ingredient.unit}".trim()
                    } else {
                        String.format(Locale.US, "%.1f %s", scaledQuantity, ingredient.unit).trim()
                    }
                } else {
                    ingredient.unit
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = ingredient.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (formattedQty.isNotBlank()) {
                        Text(
                            text = formattedQty,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Instructions:", style = MaterialTheme.typography.titleLarge)
                }
            }
            
            items(recipeWithDetails.steps) { step ->
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Step ${step.stepNumber}:", fontWeight = FontWeight.Bold)
                    if (step.stepImagePath != null) {
                        AsyncImage(
                            model = step.stepImagePath,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .padding(vertical = 8.dp)
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

                // Days Selection Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    daySlots.forEach { slot ->
                        FilterChip(
                            selected = selectedIsoDate == slot.isoDate,
                            onClick = { selectedIsoDate = slot.isoDate },
                            label = { 
                                Text(
                                    text = slot.displayTitle,
                                    style = MaterialTheme.typography.labelSmall
                                ) 
                            }
                        )
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

                Text(
                    text = "Check ingredients you need to buy (unchecked items won't be added):",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(recipeWithDetails.ingredients) { ingredient ->
                        val isChecked = checkedMap[ingredient.id] == true
                        val scaledQty = ingredient.quantity * scaleFactor
                        val qtyStr = if (scaledQty > 0) {
                            if (scaledQty % 1.0 == 0.0) "${scaledQty.toInt()} ${ingredient.unit}".trim()
                            else String.format(Locale.US, "%.1f %s", scaledQty, ingredient.unit).trim()
                        } else ingredient.unit

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { checkedMap[ingredient.id] = !isChecked }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checkedMap[ingredient.id] = it }
                            )
                            Text(
                                text = ingredient.name,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = qtyStr,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val selectedIngredients = recipeWithDetails.ingredients.filter {
                    checkedMap[it.id] == true
                }
                onConfirm(selectedIsoDate, selectedIngredients)
            }) {
                Text("Confirm & Plan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
