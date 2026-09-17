package com.android.cookarchive.ui.screens

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.android.cookarchive.data.entities.RecipeWithDetails
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookingModeScreen(
    recipeWithDetails: RecipeWithDetails,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val window = (context as? Activity)?.window
    var showIngredientsSheet by remember { mutableStateOf(false) }

    // Keep screen on while in Cooking Mode
    DisposableEffect(Unit) {
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    if (recipeWithDetails.steps.isEmpty()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(recipeWithDetails.recipe.title, maxLines = 1) },
                    navigationIcon = {
                        IconButton(onClick = onExit) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        text = "No instructions found for this recipe.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onExit) {
                        Text("Exit Cooking Mode")
                    }
                }
            }
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { recipeWithDetails.steps.size })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = recipeWithDetails.recipe.title,
                        maxLines = 1,
                        style = MaterialTheme.typography.titleMedium
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { showIngredientsSheet = true }) {
                        Icon(
                            Icons.Default.RestaurantMenu, 
                            contentDescription = "View Ingredients",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ingredients")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            LinearProgressIndicator(
                progress = { 
                    (pagerState.currentPage + 1).toFloat() / recipeWithDetails.steps.size 
                },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer
            )
            
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                beyondViewportPageCount = 1
            ) { page ->
                val step = recipeWithDetails.steps[page]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.Top
                ) {
                    // Step Badge
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Step ${step.stepNumber} of ${recipeWithDetails.steps.size}",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    if (!step.stepImagePath.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(20.dp))
                        AsyncImage(
                            model = step.stepImagePath,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = step.instructionText,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 20.sp,
                            lineHeight = 28.sp
                        ),
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            // Fixed Bottom Navigation
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val scope = rememberCoroutineScope()
                    
                    OutlinedButton(
                        onClick = { 
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } 
                        },
                        enabled = pagerState.currentPage > 0,
                        modifier = Modifier.height(56.dp).weight(1f)
                    ) {
                        Text("Previous")
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Button(
                        onClick = { 
                            if (pagerState.currentPage < recipeWithDetails.steps.size - 1) {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            } else {
                                onExit()
                            }
                        },
                        modifier = Modifier.height(56.dp).weight(1f)
                    ) {
                        Text(if (pagerState.currentPage < recipeWithDetails.steps.size - 1) "Next" else "Finish")
                    }
                }
            }
        }

        if (showIngredientsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showIngredientsSheet = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp)
                ) {
                    Text(
                        text = "Ingredients List",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                    HorizontalDivider()
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(recipeWithDetails.ingredients) { ingredient ->
                            val qtyStr = if (ingredient.quantity > 0) {
                                if (ingredient.quantity % 1.0 == 0.0) {
                                    "${ingredient.quantity.toInt()} ${ingredient.unit}".trim()
                                } else {
                                    String.format(Locale.US, "%.1f %s", ingredient.quantity, ingredient.unit).trim()
                                }
                            } else {
                                ingredient.unit
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = ingredient.name,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                if (qtyStr.isNotBlank()) {
                                    Text(
                                        text = qtyStr,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
                        }
                    }
                }
            }
        }
    }
}
