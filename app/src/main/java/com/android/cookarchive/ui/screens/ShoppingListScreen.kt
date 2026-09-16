package com.android.cookarchive.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.android.cookarchive.data.entities.ShoppingListItem
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(
    shoppingItems: List<ShoppingListItem>,
    onToggleBought: (ShoppingListItem) -> Unit,
    onUpdateItem: (ShoppingListItem) -> Unit,
    onAddCustomItem: (String, Double, String) -> Unit,
    onDeleteItem: (ShoppingListItem) -> Unit,
    onClearBought: () -> Unit
) {
    var nameInput by remember { mutableStateOf("") }
    var qtyInput by remember { mutableStateOf("") }
    var unitInput by remember { mutableStateOf("") }

    var itemToEdit by remember { mutableStateOf<ShoppingListItem?>(null) }

    val pendingItems = shoppingItems.filter { !it.isBought }
    val boughtItems = shoppingItems.filter { it.isBought }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shopping List") },
                actions = {
                    if (boughtItems.isNotEmpty()) {
                        TextButton(onClick = onClearBought) {
                            Icon(Icons.Default.RemoveDone, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear Cart")
                        }
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
            // Add Custom Item Bar
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Add Item to List",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("Item Name") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = qtyInput,
                            onValueChange = { qtyInput = it },
                            label = { Text("Qty") },
                            singleLine = true,
                            modifier = Modifier.width(70.dp)
                        )
                        OutlinedTextField(
                            value = unitInput,
                            onValueChange = { unitInput = it },
                            label = { Text("Unit") },
                            singleLine = true,
                            modifier = Modifier.width(70.dp)
                        )
                        IconButton(
                            onClick = {
                                if (nameInput.isNotBlank()) {
                                    val qty = qtyInput.replace(",", ".").toDoubleOrNull() ?: 1.0
                                    onAddCustomItem(nameInput, qty, unitInput)
                                    nameInput = ""
                                    qtyInput = ""
                                    unitInput = ""
                                }
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Item", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            if (shoppingItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Your shopping list is empty!",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (pendingItems.isNotEmpty()) {
                        item {
                            Text(
                                text = "Still Needed (${pendingItems.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        items(pendingItems, key = { it.id }) { item ->
                            ShoppingRow(
                                item = item,
                                onToggleBought = { onToggleBought(item) },
                                onEdit = { itemToEdit = item },
                                onDelete = { onDeleteItem(item) }
                            )
                        }
                    }

                    if (boughtItems.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "In Cart (${boughtItems.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        items(boughtItems, key = { it.id }) { item ->
                            ShoppingRow(
                                item = item,
                                onToggleBought = { onToggleBought(item) },
                                onEdit = { itemToEdit = item },
                                onDelete = { onDeleteItem(item) }
                            )
                        }
                    }
                }
            }
        }

        itemToEdit?.let { item ->
            EditShoppingItemDialog(
                item = item,
                onDismiss = { itemToEdit = null },
                onSave = { updated ->
                    onUpdateItem(updated)
                    itemToEdit = null
                }
            )
        }
    }
}

@Composable
private fun ShoppingRow(
    item: ShoppingListItem,
    onToggleBought: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onToggleBought() }, // 1-tap anywhere on the row toggles item to cart
        shape = MaterialTheme.shapes.small,
        tonalElevation = if (item.isBought) 0.dp else 2.dp,
        color = if (item.isBought) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) 
                else MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.isBought,
                onCheckedChange = { onToggleBought() }
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        textDecoration = if (item.isBought) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = if (item.isBought) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
                if (!item.originRecipe.isNullOrBlank()) {
                    Text(
                        text = "From ${item.originRecipe}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            val qtyStr = if (item.quantity > 0) {
                if (item.quantity % 1.0 == 0.0) {
                    "${item.quantity.toInt()} ${item.unit}".trim()
                } else {
                    String.format(Locale.US, "%.1f %s", item.quantity, item.unit).trim()
                }
            } else {
                item.unit
            }

            if (qtyStr.isNotBlank()) {
                Text(
                    text = qtyStr,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (item.isBought) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit Item",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete Item",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun EditShoppingItemDialog(
    item: ShoppingListItem,
    onDismiss: () -> Unit,
    onSave: (ShoppingListItem) -> Unit
) {
    var editName by remember { mutableStateOf(item.name) }
    var editQty by remember {
        mutableStateOf(
            if (item.quantity > 0) {
                if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString()
                else item.quantity.toString()
            } else ""
        )
    }
    var editUnit by remember { mutableStateOf(item.unit) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Item") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = editQty,
                        onValueChange = { editQty = it },
                        label = { Text("Qty") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = editUnit,
                        onValueChange = { editUnit = it },
                        label = { Text("Unit") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val q = editQty.replace(",", ".").toDoubleOrNull() ?: 0.0
                    onSave(item.copy(name = editName.trim(), quantity = q, unit = editUnit.trim()))
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
