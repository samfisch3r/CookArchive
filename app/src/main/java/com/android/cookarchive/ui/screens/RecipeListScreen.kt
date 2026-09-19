package com.android.cookarchive.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
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
import com.android.cookarchive.data.entities.RecipeWithDetails
import com.android.cookarchive.util.PdfUtil
import kotlinx.coroutines.flow.SharedFlow
import java.text.Collator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class SortMode {
    A_Z,
    LEAST_RECENTLY_COOKED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListScreen(
    recipes: List<RecipeWithDetails>,
    isLoading: Boolean,
    errorEvents: SharedFlow<String>,
    onRecipeClick: (Long) -> Unit,
    onAddRecipe: (String) -> Unit,
    onAddRecipesFromImages: (List<Bitmap>) -> Unit,
    onExportBackup: () -> Unit = {},
    onImportBackup: () -> Unit = {}
) {
    var showDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var urlText by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var sortMode by remember { mutableStateOf(SortMode.A_Z) }

    val collator = remember {
        Collator.getInstance(Locale.getDefault()).apply {
            strength = Collator.SECONDARY
        }
    }

    val filteredRecipes = recipes.filter { 
        it.recipe.title.contains(searchQuery, ignoreCase = true) ||
        it.recipe.category.contains(searchQuery, ignoreCase = true)
    }.let { list ->
        when (sortMode) {
            SortMode.A_Z -> list.sortedWith { r1, r2 -> collator.compare(r1.recipe.title, r2.recipe.title) }
            SortMode.LEAST_RECENTLY_COOKED -> list.sortedBy { it.recipe.lastCooked }
        }
    }

    LaunchedEffect(Unit) {
        errorEvents.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val bitmaps = uris.map { uri ->
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source)
            }
            onAddRecipesFromImages(bitmaps)
            showDialog = false
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val bitmaps = PdfUtil.pdfToBitmaps(context, it)
            if (bitmaps.isNotEmpty()) {
                onAddRecipesFromImages(bitmaps)
                showDialog = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recipes") },
                actions = {
                    IconButton(onClick = {
                        sortMode = if (sortMode == SortMode.A_Z) SortMode.LEAST_RECENTLY_COOKED else SortMode.A_Z
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Sort Recipes",
                            tint = if (sortMode == SortMode.LEAST_RECENTLY_COOKED) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }

                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Export Backup (ZIP)") },
                                leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onExportBackup()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Import Backup (ZIP)") },
                                leadingIcon = { Icon(Icons.Default.FileUpload, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onImportBackup()
                                }
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!isLoading) {
                FloatingActionButton(onClick = { showDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Recipe")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Search recipes or categories...") },
                singleLine = true,
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear Search")
                        }
                    }
                }
            )

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Importing Recipe...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            } else if (filteredRecipes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isEmpty()) "No recipes found. Tap + to add one!" else "No matching recipes found.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filteredRecipes, key = { it.recipe.id }) { item ->
                        RecipeItem(
                            recipeWithDetails = item,
                            onClick = { onRecipeClick(item.recipe.id) }
                        )
                    }
                }
            }
        }

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("Add Recipe") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            value = urlText,
                            onValueChange = { urlText = it },
                            label = { Text("Recipe Website URL") },
                            placeholder = { Text("https://example.com/recipe") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = "— OR —",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            color = MaterialTheme.colorScheme.outline
                        )

                        Button(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Pick Recipe Photo(s)")
                        }

                        OutlinedButton(
                            onClick = { pdfPickerLauncher.launch("application/pdf") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Pick Recipe PDF Document")
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (urlText.isNotBlank()) {
                                onAddRecipe(urlText)
                                showDialog = false
                                urlText = ""
                            }
                        },
                        enabled = urlText.isNotBlank()
                    ) {
                        Text("Import URL")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun RecipeItem(
    recipeWithDetails: RecipeWithDetails,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = recipeWithDetails.recipe.imagePath,
                contentDescription = null,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recipeWithDetails.recipe.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = recipeWithDetails.recipe.category,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )

                Spacer(modifier = Modifier.height(4.dp))

                val lastCookedDays = formatLastCookedDays(recipeWithDetails.recipe.lastCooked)
                Text(
                    text = "Cooked ${recipeWithDetails.recipe.cookCount} times • $lastCookedDays",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

private fun formatLastCookedDays(lastCookedEpochMs: Long): String {
    if (lastCookedEpochMs == 0L) return "Never cooked"
    
    val lastCookedDate = Instant.ofEpochMilli(lastCookedEpochMs)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    
    val today = LocalDate.now()
    val daysAgo = ChronoUnit.DAYS.between(lastCookedDate, today)

    return when {
        daysAgo == 0L -> "Cooked today"
        daysAgo == 1L -> "Cooked yesterday"
        daysAgo > 1L -> "Cooked $daysAgo days ago"
        else -> "Cooked recently"
    }
}
