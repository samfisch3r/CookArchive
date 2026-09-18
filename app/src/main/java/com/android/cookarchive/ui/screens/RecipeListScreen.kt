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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
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
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import androidx.compose.material.icons.automirrored.filled.Sort
import java.text.Collator
import java.time.LocalDate
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
    onAddRecipesFromImages: (List<Bitmap>) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Search Box
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Search recipes...") },
                singleLine = true,
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                }
            )

            Box(modifier = Modifier.weight(1f)) {
                if (filteredRecipes.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isLoading) "Importing recipe..." 
                                   else if (searchQuery.isNotEmpty()) "No recipes match your search."
                                   else "No recipes found. Add one to get started!",
                            modifier = Modifier.padding(32.dp)
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filteredRecipes) { item ->
                            val lastCookedText = if (item.recipe.lastCooked == 0L) {
                                "Never cooked"
                            } else {
                                val date = Instant.ofEpochMilli(item.recipe.lastCooked).atZone(ZoneId.systemDefault()).toLocalDate()
                                val daysAgo = ChronoUnit.DAYS.between(date, LocalDate.now())
                                when {
                                    daysAgo == 0L -> "Today"
                                    daysAgo == 1L -> "Yesterday"
                                    daysAgo < 7L -> "$daysAgo days ago"
                                    daysAgo < 30L -> "${daysAgo / 7} weeks ago"
                                    else -> "${daysAgo / 30} months ago"
                                }
                            }

                            ListItem(
                                headlineContent = { Text(item.recipe.title, fontWeight = FontWeight.Bold) },
                                supportingContent = { Text("${item.recipe.category} • $lastCookedText") },
                                leadingContent = {
                                    AsyncImage(
                                        model = item.recipe.imagePath,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                },
                                modifier = Modifier.clickable { onRecipeClick(item.recipe.id) }
                            )
                            HorizontalDivider()
                        }
                    }
                }
                
                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("Add New Recipe") },
                text = {
                    Column {
                        TextField(
                            value = urlText,
                            onValueChange = { urlText = it },
                            label = { Text("Enter Website URL") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = "Or import from photos / PDF via AI scan")
                        Text(
                            text = "(Tip: Select multiple photos if food and text are on separate pages)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { imagePickerLauncher.launch("image/*") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Pick Images")
                            }
                            OutlinedButton(
                                onClick = { pdfPickerLauncher.launch("application/pdf") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Pick PDF")
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (urlText.isNotBlank()) {
                            onAddRecipe(urlText)
                            showDialog = false
                            urlText = ""
                        }
                    }) {
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
