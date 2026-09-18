package com.android.cookarchive.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.android.cookarchive.data.entities.MealPlan
import com.android.cookarchive.data.entities.MealPlanWithRecipe
import com.android.cookarchive.util.WebSyncUtil
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

data class DaySlot(
    val isoDate: String,
    val displayTitle: String,
    val formattedDateStr: String
)

fun getNext5Days(): List<DaySlot> {
    val today = LocalDate.now()
    val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    val monthDayFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

    return (0..4).map { dayOffset ->
        val date = today.plusDays(dayOffset.toLong())
        val iso = date.format(isoFormatter)
        val monthDay = date.format(monthDayFormatter)

        val title = when (dayOffset) {
            0 -> "Today"
            1 -> "Tomorrow"
            else -> date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        }

        DaySlot(
            isoDate = iso,
            displayTitle = title,
            formattedDateStr = monthDay
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealPlanScreen(
    mealPlans: List<MealPlanWithRecipe>,
    recipeWishes: List<WebSyncUtil.MealWish> = emptyList(),
    onCookRecipe: (Long) -> Unit,
    onAddCustomMealPlan: (String, String) -> Unit,
    onMoveMealPlan: (Long, String) -> Unit,
    onDeleteMealPlan: (MealPlan) -> Unit,
    onDismissWish: (String) -> Unit = {}
) {
    val daySlots = remember { getNext5Days() }
    val mealPlansByDate = mealPlans.groupBy { it.mealPlan.date }
    val context = LocalContext.current

    var showCustomDialog by remember { mutableStateOf(false) }
    var targetSlotForCustomNote by remember { mutableStateOf<DaySlot?>(null) }
    var customNoteText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Menu Plan") },
                actions = {
                    IconButton(onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Household Menu Plan")
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Check out our household menu plan for this week!\nOpen: https://cookarchive.netlify.app"
                            )
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Household Menu Plan"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share Web Link")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (recipeWishes.isNotEmpty()) {
                item(key = "card_wishes") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "✨ Household Meal Requests (${recipeWishes.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            recipeWishes.forEach { wish ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = wish.title,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Row {
                                            IconButton(onClick = {
                                                targetSlotForCustomNote = daySlots.first()
                                                customNoteText = wish.title
                                                showCustomDialog = true
                                            }) {
                                                Icon(
                                                    Icons.Default.Add,
                                                    contentDescription = "Schedule Wish",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            IconButton(onClick = { onDismissWish(wish.id) }) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Dismiss Wish",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            items(daySlots, key = { it.isoDate }) { slot ->
                val mealsForDay = mealPlansByDate[slot.isoDate] ?: emptyList()

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (mealsForDay.isNotEmpty()) 
                            MaterialTheme.colorScheme.surfaceContainerHigh 
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = slot.displayTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = slot.formattedDateStr,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            TextButton(
                                onClick = {
                                    targetSlotForCustomNote = slot
                                    customNoteText = ""
                                    showCustomDialog = true
                                }
                            ) {
                                Icon(
                                    Icons.Default.EditNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Custom")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (mealsForDay.isEmpty()) {
                            Text(
                                text = "No meals planned for this day.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            mealsForDay.forEach { item ->
                                MealPlanItemRow(
                                    mealPlanWithRecipe = item,
                                    availableDays = daySlots,
                                    onCook = {
                                        item.recipeWithDetails?.recipe?.id?.let { recipeId ->
                                            onCookRecipe(recipeId)
                                        }
                                    },
                                    onMoveToDate = { newDate -> onMoveMealPlan(item.mealPlan.id, newDate) },
                                    onDelete = { onDeleteMealPlan(item.mealPlan) }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }

        if (showCustomDialog && targetSlotForCustomNote != null) {
            AlertDialog(
                onDismissRequest = { showCustomDialog = false },
                title = { Text("Add Custom Meal / Note") },
                text = {
                    Column {
                        Text(
                            text = "For ${targetSlotForCustomNote?.displayTitle} (${targetSlotForCustomNote?.formattedDateStr})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = customNoteText,
                            onValueChange = { customNoteText = it },
                            label = { Text("Note / Meal (e.g. Order Pizza)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (customNoteText.isNotBlank() && targetSlotForCustomNote != null) {
                                onAddCustomMealPlan(customNoteText, targetSlotForCustomNote!!.isoDate)
                                showCustomDialog = false
                                customNoteText = ""
                                targetSlotForCustomNote = null
                            }
                        }
                    ) {
                        Text("Add to Plan")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCustomDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun MealPlanItemRow(
    mealPlanWithRecipe: MealPlanWithRecipe,
    availableDays: List<DaySlot>,
    onCook: () -> Unit,
    onMoveToDate: (String) -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (mealPlanWithRecipe.isRecipe && !mealPlanWithRecipe.recipeWithDetails?.recipe?.imagePath.isNullOrBlank()) {
                AsyncImage(
                    model = mealPlanWithRecipe.recipeWithDetails.recipe.imagePath,
                    contentDescription = null,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.RestaurantMenu,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mealPlanWithRecipe.displayTitle,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1
                )
                Text(
                    text = mealPlanWithRecipe.displayCategory,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            if (mealPlanWithRecipe.isRecipe) {
                IconButton(onClick = onCook) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Cook Now",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options")
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Move to...") },
                        onClick = {},
                        enabled = false
                    )

                    availableDays.forEach { day ->
                        if (day.isoDate != mealPlanWithRecipe.mealPlan.date) {
                            DropdownMenuItem(
                                text = { Text("${day.displayTitle} (${day.formattedDateStr})") },
                                onClick = {
                                    showMenu = false
                                    onMoveToDate(day.isoDate)
                                }
                            )
                        }
                    }

                    HorizontalDivider()

                    DropdownMenuItem(
                        text = { 
                            Text(
                                "Remove from plan",
                                color = MaterialTheme.colorScheme.error
                            ) 
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}
