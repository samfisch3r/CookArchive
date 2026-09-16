package com.android.cookarchive.ui.screens

import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.android.cookarchive.data.entities.Ingredient
import com.android.cookarchive.data.entities.InstructionStep
import com.android.cookarchive.data.entities.RecipeWithDetails
import com.android.cookarchive.util.ImageStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class EditableIngredient(
    val id: Long = 0,
    val name: String,
    val quantityText: String,
    val unit: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRecipeScreen(
    recipeWithDetails: RecipeWithDetails,
    onSave: (RecipeWithDetails) -> Unit,
    onCancel: () -> Unit
) {
    var title by remember { mutableStateOf(recipeWithDetails.recipe.title) }
    var description by remember { mutableStateOf(recipeWithDetails.recipe.description) }
    var category by remember { mutableStateOf(recipeWithDetails.recipe.category) }
    
    val ingredients = remember {
        mutableStateListOf(
            *recipeWithDetails.ingredients.map {
                EditableIngredient(
                    id = it.id,
                    name = it.name,
                    quantityText = if (it.quantity > 0) {
                        if (it.quantity % 1.0 == 0.0) it.quantity.toInt().toString()
                        else it.quantity.toString()
                    } else "",
                    unit = it.unit
                )
            }.toTypedArray()
        )
    }
    
    val steps = remember {
        mutableStateListOf(
            *recipeWithDetails.steps.toTypedArray()
        )
    }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var activeStepIndex by remember { mutableIntStateOf(-1) }

    val stepImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && activeStepIndex != -1) {
            val targetIndex = activeStepIndex
            scope.launch(Dispatchers.IO) {
                try {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    val bitmap = ImageDecoder.decodeBitmap(source)
                    val localPath = ImageStorage.saveBitmap(context, bitmap)
                    if (localPath != null) {
                        withContext(Dispatchers.Main) {
                            if (targetIndex in steps.indices) {
                                steps[targetIndex] = steps[targetIndex].copy(stepImagePath = localPath)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Recipe") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
                    }
                },
                actions = {
                    Button(onClick = {
                        val updatedRecipe = recipeWithDetails.recipe.copy(
                            title = title,
                            description = description,
                            category = category
                        )
                        val finalIngredients = ingredients.map {
                            Ingredient(
                                id = it.id,
                                recipeId = recipeWithDetails.recipe.id,
                                name = it.name,
                                quantity = it.quantityText.replace(",", ".").toDoubleOrNull() ?: 0.0,
                                unit = it.unit
                            )
                        }
                        val updatedSteps = steps.mapIndexed { index, step ->
                            step.copy(stepNumber = index + 1)
                        }
                        onSave(RecipeWithDetails(updatedRecipe, finalIngredients, updatedSteps))
                    }) {
                        Text("Save")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "header_title") {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item(key = "header_desc") {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item(key = "header_cat") {
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            item(key = "header_ingredients_label") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Ingredients", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = {
                        ingredients.add(
                            EditableIngredient(
                                id = 0,
                                name = "",
                                quantityText = "1",
                                unit = ""
                            )
                        )
                    }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Ingredient")
                    }
                }
            }
            
            itemsIndexed(
                ingredients,
                key = { index, item -> "ing_${item.id}_$index" }
            ) { index, ingredient ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = ingredient.name,
                        onValueChange = { ingredients[index] = ingredient.copy(name = it) },
                        label = { Text("Name") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = ingredient.quantityText,
                        onValueChange = { ingredients[index] = ingredient.copy(quantityText = it) },
                        label = { Text("Qty") },
                        singleLine = true,
                        modifier = Modifier.width(75.dp)
                    )
                    OutlinedTextField(
                        value = ingredient.unit,
                        onValueChange = { ingredients[index] = ingredient.copy(unit = it) },
                        label = { Text("Unit") },
                        singleLine = true,
                        modifier = Modifier.width(75.dp)
                    )
                    IconButton(onClick = { ingredients.removeAt(index) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete Ingredient",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            item(key = "header_steps_label") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Instructions", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = {
                        steps.add(
                            InstructionStep(
                                recipeId = recipeWithDetails.recipe.id,
                                stepNumber = steps.size + 1,
                                instructionText = ""
                            )
                        )
                    }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Step")
                    }
                }
            }
            
            itemsIndexed(
                steps,
                key = { index, item -> "step_${item.id}_$index" }
            ) { index, step ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = step.instructionText,
                            onValueChange = { steps[index] = step.copy(instructionText = it) },
                            label = { Text("Step ${index + 1}") },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { 
                            activeStepIndex = index
                            stepImagePicker.launch("image/*")
                        }) {
                            Icon(Icons.Default.AddAPhoto, contentDescription = "Add Step Photo")
                        }
                        IconButton(onClick = { steps.removeAt(index) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete Step",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    if (!step.stepImagePath.isNullOrBlank()) {
                        Box {
                            AsyncImage(
                                model = step.stepImagePath,
                                contentDescription = null,
                                modifier = Modifier
                                    .height(100.dp)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
        }
    }
}
