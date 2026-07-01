package com.example.glicose.ui

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.glicose.data.FoodItem
import com.example.glicose.data.GlucoseRecord
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.*

// Helper function to normalize strings (remove accents for easier search)
fun String.normalizeForSearch(): String {
    val temp = Normalizer.normalize(this, Normalizer.Form.NFD)
    return temp.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "").lowercase(Locale.getDefault()).trim()
}

data class MealItem(
    val food: FoodItem,
    val quantityMultiplier: Float // 1.0f means 1 portion
) {
    val totalCarbs: Float get() = food.carbs * quantityMultiplier
    val totalCalories: Float get() = food.calories * quantityMultiplier
    val totalGrams: Float get() = food.grams * quantityMultiplier
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AddMealScreen(
    viewModel: GlucoseViewModel,
    editTimestamp: Long? = null,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val carbRatio by viewModel.carbRatio.collectAsState()
    
    val customFoods by viewModel.customFoods.collectAsState()
    // Load foods once and merge with custom foods
    val allFoods = remember(customFoods) { viewModel.loadFoodsList(context) + customFoods }
    
    var searchQuery by remember { mutableStateOf("") }
    val selectedMealItems = remember { mutableStateListOf<MealItem>() }
    
    val allRecords by viewModel.allRecords.collectAsState()
    val originalMeal = remember(allRecords, editTimestamp) {
        if (editTimestamp != null) allRecords.find { it.timestamp == editTimestamp } else null
    }
    
    val associatedGlucose = remember(allRecords, originalMeal) {
        if (originalMeal != null) {
            allRecords.find { it.timestamp == originalMeal.timestamp + 1 && it.value > 0f }
        } else null
    }

    var isInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(originalMeal, allFoods) {
        if (originalMeal != null && !isInitialized && allFoods.isNotEmpty()) {
            // Prefer structured JSON mealDetails; fallback to regex parse of note
            val parsed = if (!originalMeal.mealDetails.isNullOrEmpty()) {
                parseMealItemsFromJson(originalMeal.mealDetails, allFoods)
            } else {
                parseMealItems(originalMeal.note, allFoods)
            }
            selectedMealItems.clear()
            selectedMealItems.addAll(parsed)
            isInitialized = true
        }
    }
    
    // Filtered search results (limit to 50 for performance)
    val filteredFoods = remember(searchQuery) {
        if (searchQuery.length < 2) emptyList()
        else {
            val normalizedQuery = searchQuery.normalizeForSearch()
            val queryWords = normalizedQuery.split(" ").filter { it.isNotEmpty() }
            if (queryWords.isEmpty()) emptyList()
            else {
                allFoods.filter { food ->
                    val normalizedName = food.name.normalizeForSearch()
                    queryWords.all { word -> normalizedName.contains(word) }
                }.take(50)
            }
        }
    }
    
    // Sum calculations
    val totalCarbs = selectedMealItems.sumOf { it.totalCarbs.toDouble() }.toFloat()
    val totalCalories = selectedMealItems.sumOf { it.totalCalories.toDouble() }.toFloat()
    
    // Insulin suggestion
    val suggestedInsulin = remember(totalCarbs, carbRatio) {
        if (carbRatio > 0) totalCarbs / carbRatio else 0f
    }
    
    var showSaveDialog by remember { mutableStateOf(false) }
    var foodToEditQuantity by remember { mutableStateOf<MealItem?>(null) }
    var showCreateCustomFoodDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editTimestamp != null) "Editar Refeição" else "Contagem de Carboidratos", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                placeholder = { Text("Buscar alimento (ex: arroz, pão...)") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, null)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
            
            // Search results list overlay or tray
            if (searchQuery.isNotEmpty()) {
                Text(
                    text = "Resultados da Busca (${filteredFoods.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    if (filteredFoods.isEmpty() && searchQuery.length >= 2) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Nenhum alimento encontrado", color = Color.Gray)
                            }
                        }
                    }
                    
                    items(filteredFoods) { food ->
                        ListItem(
                            headlineContent = { Text(food.name, fontWeight = FontWeight.SemiBold) },
                            supportingContent = { 
                                Text("${food.measure} (${food.grams.toInt()}g/ml) • ${food.carbs.toInt()}g CHO • ${food.calories.toInt()} kcal") 
                            },
                            trailingContent = {
                                IconButton(
                                    onClick = {
                                        // Check if already in meal
                                        val existing = selectedMealItems.find { it.food.id == food.id }
                                        if (existing != null) {
                                            val idx = selectedMealItems.indexOf(existing)
                                            if (idx != -1) {
                                                selectedMealItems[idx] = existing.copy(quantityMultiplier = existing.quantityMultiplier + 1f)
                                            }
                                        } else {
                                            selectedMealItems.add(MealItem(food, 1.0f))
                                        }
                                        searchQuery = "" // Clear search after adding
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddCircle, 
                                        contentDescription = "Adicionar à refeição",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            modifier = Modifier.clickable {
                                val existing = selectedMealItems.find { it.food.id == food.id }
                                if (existing != null) {
                                    foodToEditQuantity = existing
                                } else {
                                    val newItem = MealItem(food, 1.0f)
                                    selectedMealItems.add(newItem)
                                    foodToEditQuantity = newItem
                                }
                                searchQuery = ""
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                    
                    if (searchQuery.length >= 2) {
                        item {
                            Button(
                                onClick = { showCreateCustomFoodDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                Icon(Icons.Default.Add, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Cadastrar Alimento Personalizado")
                            }
                        }
                    }
                }
            } else {
                // Active meal list (Meal Tray)
                Text(
                    text = "Itens na Refeição",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
                
                if (selectedMealItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.RestaurantMenu, 
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("Use a busca acima para adicionar alimentos à sua refeição", color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(selectedMealItems, key = { it.food.id }) { mealItem ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = mealItem.food.name, 
                                            fontWeight = FontWeight.Bold, 
                                            maxLines = 1, 
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${mealItem.food.measure} (${mealItem.food.grams.toInt()}g/ml)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Text(
                                                text = "${mealItem.totalCarbs.toInt()}g CHO",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${mealItem.totalCalories.toInt()} kcal",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = Color.Gray
                                            )
                                            Text(
                                                text = "${mealItem.totalGrams.toInt()}g peso",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                    
                                    // Quantity Controls
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                if (mealItem.quantityMultiplier > 0.5f) {
                                                    val idx = selectedMealItems.indexOf(mealItem)
                                                    if (idx != -1) {
                                                        selectedMealItems[idx] = mealItem.copy(quantityMultiplier = mealItem.quantityMultiplier - 0.5f)
                                                    }
                                                } else {
                                                    selectedMealItems.remove(mealItem)
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.RemoveCircleOutline, "Diminuir porção")
                                        }
                                        
                                        Text(
                                            text = if (mealItem.quantityMultiplier.isInteger()) 
                                                "${mealItem.quantityMultiplier.toInt()}x" 
                                            else 
                                                "${mealItem.quantityMultiplier}x",
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .clickable { foodToEditQuantity = mealItem }
                                                .padding(horizontal = 4.dp)
                                        )
                                        
                                        IconButton(
                                            onClick = {
                                                val idx = selectedMealItems.indexOf(mealItem)
                                                if (idx != -1) {
                                                    selectedMealItems[idx] = mealItem.copy(quantityMultiplier = mealItem.quantityMultiplier + 0.5f)
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.AddCircleOutline, "Aumentar porção")
                                        }
                                        
                                        Spacer(Modifier.width(4.dp))
                                        
                                        IconButton(
                                            onClick = { selectedMealItems.remove(mealItem) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, "Remover", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // Bottom Summary Panel
            if (selectedMealItems.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Total Carboidratos", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "${totalCarbs.toInt()} g",
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Total Energia", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "${totalCalories.toInt()} kcal",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        
                        // Insulin Dose Suggestion
                        if (carbRatio > 0f) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Vaccines, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Sugestão de Insulina Bolus", fontWeight = FontWeight.Medium)
                                }
                                Text(
                                    text = String.format(Locale.getDefault(), "%.1f U", suggestedInsulin),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        
                        Button(
                            onClick = { showSaveDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.Check, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Registrar Refeição", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
    
    // Save Unified Meal Dialog
    if (showSaveDialog) {
        val initialMealType = remember(originalMeal) {
            originalMeal?.note?.substringBefore(": ")?.trim() ?: "Almoço"
        }
        
        val initialNotes = remember(originalMeal, selectedMealItems) {
            if (originalMeal != null) {
                val afterColon = originalMeal.note.substringAfter(": ").trim()
                val summary = selectedMealItems.joinToString(", ") { "${it.food.name} (${if (it.quantityMultiplier.isInteger()) it.quantityMultiplier.toInt() else it.quantityMultiplier}x)" }
                if (afterColon == summary) "" else afterColon
            } else ""
        }
        
        val initialGlucose = remember(associatedGlucose) {
            associatedGlucose?.value?.toInt()?.toString() ?: ""
        }
        
        val initialTimestamp = remember(originalMeal) {
            originalMeal?.timestamp ?: System.currentTimeMillis()
        }

        SaveMealDialog(
            totalCarbs = totalCarbs,
            totalCalories = totalCalories,
            mealItemsSummary = selectedMealItems.joinToString(", ") { "${it.food.name} (${if (it.quantityMultiplier.isInteger()) it.quantityMultiplier.toInt() else it.quantityMultiplier}x)" },
            initialMealType = initialMealType,
            initialNotes = initialNotes,
            initialGlucose = initialGlucose,
            initialTimestamp = initialTimestamp,
            onDismiss = { showSaveDialog = false },
            onConfirm = { glucoseStr, mealType, notes, timestamp ->
                // Delete old records first if editing
                if (originalMeal != null) {
                    viewModel.deleteRecord(originalMeal)
                    if (associatedGlucose != null) {
                        viewModel.deleteRecord(associatedGlucose)
                    }
                }
                
                val glucoseVal = glucoseStr.toFloatOrNull() ?: 0f
                val finalNote = if (notes.isNotEmpty()) "$mealType: $notes" else "$mealType: " + selectedMealItems.joinToString(", ") { "${it.food.name} (${if (it.quantityMultiplier.isInteger()) it.quantityMultiplier.toInt() else it.quantityMultiplier}x)" }
                
                // Build structured JSON for cloud sync
                val mealDetailsJson = JSONArray().apply {
                    selectedMealItems.forEach { item ->
                        put(JSONObject().apply {
                            put("name", item.food.name)
                            put("multiplier", item.quantityMultiplier.toDouble())
                        })
                    }
                }.toString()

                // 1. Save the meal record (value = 0f, carbs & calories populated)
                viewModel.addRecord(
                    value = 0f,
                    note = finalNote,
                    timestamp = timestamp,
                    carbs = totalCarbs,
                    calories = totalCalories,
                    mealDetails = mealDetailsJson
                )
                
                // 2. Save the glucose record separately if provided (value = glucoseVal, carbs & calories null)
                if (glucoseVal > 0f) {
                    val glucoseNote = "Refeição ($mealType)${if (notes.isNotEmpty()) ": $notes" else ""}"
                    viewModel.addRecord(
                        value = glucoseVal,
                        note = glucoseNote,
                        timestamp = timestamp + 1, // Avoid primary key collision
                        carbs = null,
                        calories = null
                    )
                }
                
                showSaveDialog = false
                selectedMealItems.clear()
                onNavigateBack()
            }
        )
    }
    
    if (showCreateCustomFoodDialog) {
        CreateCustomFoodDialog(
            initialName = searchQuery,
            onDismiss = { showCreateCustomFoodDialog = false },
            onConfirm = { food ->
                viewModel.addCustomFood(food)
                selectedMealItems.add(MealItem(food, 1.0f))
                searchQuery = "" // Clear search and show plate
                showCreateCustomFoodDialog = false
            }
        )
    }
    
    // Edit item quantity dialog (support custom grams)
    if (foodToEditQuantity != null) {
        val editingItem = foodToEditQuantity!!
        EditQuantityDialog(
            foodItem = editingItem.food,
            currentMultiplier = editingItem.quantityMultiplier,
            onDismiss = { foodToEditQuantity = null },
            onConfirm = { newMultiplier ->
                val idx = selectedMealItems.indexOfFirst { it.food.id == editingItem.food.id }
                if (idx != -1) {
                    if (newMultiplier <= 0f) {
                        selectedMealItems.removeAt(idx)
                    } else {
                        selectedMealItems[idx] = editingItem.copy(quantityMultiplier = newMultiplier)
                    }
                }
                foodToEditQuantity = null
            }
        )
    }
}

@Composable
fun EditQuantityDialog(
    foodItem: FoodItem,
    currentMultiplier: Float,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit
) {
    var portionsInput by remember { mutableStateOf(if (currentMultiplier.isInteger()) currentMultiplier.toInt().toString() else currentMultiplier.toString()) }
    var weightInput by remember { mutableStateOf((foodItem.grams * currentMultiplier).toInt().toString()) }
    
    var isInputInGrams by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajustar Quantidade", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(foodItem.name, fontWeight = FontWeight.SemiBold)
                
                // Toggle mode
                TabRow(selectedTabIndex = if (isInputInGrams) 1 else 0, modifier = Modifier.clip(RoundedCornerShape(8.dp))) {
                    Tab(
                        selected = !isInputInGrams, 
                        onClick = { isInputInGrams = false },
                        text = { Text("Porções") }
                    )
                    Tab(
                        selected = isInputInGrams, 
                        onClick = { isInputInGrams = true },
                        text = { Text("Peso (g/ml)") }
                    )
                }
                
                if (!isInputInGrams) {
                    OutlinedTextField(
                        value = portionsInput,
                        onValueChange = { 
                            if (it.isEmpty() || it.toFloatOrNull() != null) {
                                portionsInput = it
                                val floatVal = it.toFloatOrNull() ?: 0f
                                weightInput = (foodItem.grams * floatVal).toInt().toString()
                            }
                        },
                        label = { Text("Número de Porções") },
                        supportingText = { Text("Medida padrão: ${foodItem.measure} (${foodItem.grams.toInt()}g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = weightInput,
                        onValueChange = { 
                            if (it.isEmpty() || it.toFloatOrNull() != null) {
                                weightInput = it
                                val floatVal = it.toFloatOrNull() ?: 0f
                                val portions = if (foodItem.grams > 0) floatVal / foodItem.grams else 0f
                                portionsInput = String.format(Locale.US, "%.2f", portions)
                            }
                        },
                        label = { Text("Quantidade em gramas ou ml") },
                        supportingText = { Text("Medida padrão: ${foodItem.measure} = ${foodItem.grams.toInt()}g") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalMultiplier = if (!isInputInGrams) {
                        portionsInput.toFloatOrNull() ?: 0f
                    } else {
                        val grams = weightInput.toFloatOrNull() ?: 0f
                        if (foodItem.grams > 0) grams / foodItem.grams else 0f
                    }
                    onConfirm(finalMultiplier)
                }
            ) {
                Text("Confirmar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun SaveMealDialog(
    totalCarbs: Float,
    totalCalories: Float,
    mealItemsSummary: String,
    initialMealType: String = "Almoço",
    initialNotes: String = "",
    initialGlucose: String = "",
    initialTimestamp: Long = System.currentTimeMillis(),
    onDismiss: () -> Unit,
    onConfirm: (glucose: String, mealType: String, notes: String, timestamp: Long) -> Unit
) {
    val context = LocalContext.current
    var glucose by remember { mutableStateOf(initialGlucose) }
    var notes by remember { mutableStateOf(initialNotes) }
    var selectedTimestamp by remember { mutableStateOf(initialTimestamp) }
    
    val mealTypes = listOf("Café da Manhã", "Almoço", "Lanche", "Jantar", "Ceia", "Outro")
    var selectedMealType by remember { mutableStateOf(if (mealTypes.contains(initialMealType)) initialMealType else mealTypes[1]) } // default: Almoço
    var isDropdownExpanded by remember { mutableStateOf(false) }

    val dateTimeText = remember(selectedTimestamp) {
        val cal = Calendar.getInstance().apply { timeInMillis = selectedTimestamp }
        val fmt = android.text.format.DateFormat.getDateFormat(context)
        val timeFmt = android.text.format.DateFormat.getTimeFormat(context)
        "${fmt.format(cal.time)}  ${timeFmt.format(cal.time)}"
    }

    fun showDateTimePicker() {
        val cal = Calendar.getInstance().apply { timeInMillis = selectedTimestamp }
        android.app.DatePickerDialog(
            context,
            { _, year, month, day ->
                android.app.TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        val newCal = Calendar.getInstance().apply {
                            set(year, month, day, hour, minute, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        selectedTimestamp = newCal.timeInMillis
                    },
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                    android.text.format.DateFormat.is24HourFormat(context)
                ).show()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar Refeição no Histórico", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Info Summary
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Resumo Nutricional", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 4.dp)) {
                            Text("🍞 ${totalCarbs.toInt()}g CHO", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("🔥 ${totalCalories.toInt()} kcal", color = Color.Gray)
                        }
                    }
                }
                
                // Meal Type Dropdown
                Box(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedMealType,
                        onValueChange = {},
                        label = { Text("Tipo de Refeição") },
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { isDropdownExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().clickable { isDropdownExpanded = true },
                        enabled = false,
                        colors = TextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    DropdownMenu(
                        expanded = isDropdownExpanded,
                        onDismissRequest = { isDropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        mealTypes.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type) },
                                onClick = {
                                    selectedMealType = type
                                    isDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
                
                // Blood Glucose (Optional)
                OutlinedTextField(
                    value = glucose,
                    onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 3) glucose = it },
                    label = { Text("Glicemia atual (opcional)") },
                    placeholder = { Text("mg/dL") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    trailingIcon = { Text("mg/dL", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp)) },
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Date picker trigger
                OutlinedTextField(
                    value = dateTimeText,
                    onValueChange = {},
                    label = { Text("Data e Hora") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDateTimePicker() },
                    readOnly = true,
                    enabled = false,
                    colors = TextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    trailingIcon = {
                        IconButton(onClick = { showDateTimePicker() }) {
                            Icon(Icons.Default.Schedule, "Alterar data e hora")
                        }
                    }
                )
                
                // Custom Note
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Nota personalizada (opcional)") },
                    placeholder = { Text("Ex: Pré-treino, insulina rápida aplicada") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(glucose, selectedMealType, notes, selectedTimestamp) }
            ) {
                Text("Confirmar e Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun CreateCustomFoodDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (FoodItem) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var carbs by remember { mutableStateOf("") }
    var calories by remember { mutableStateOf("") }
    var measure by remember { mutableStateOf("1 porção") }
    var grams by remember { mutableStateOf("100") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cadastrar Alimento Personalizado", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome do alimento") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = carbs,
                    onValueChange = { carbs = it },
                    label = { Text("Carboidratos (g) por porção") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = calories,
                    onValueChange = { calories = it },
                    label = { Text("Calorias (kcal) por porção") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = measure,
                    onValueChange = { measure = it },
                    label = { Text("Medida caseira (ex: 1 fatia, 1 copo)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = grams,
                    onValueChange = { grams = it },
                    label = { Text("Peso da porção em gramas/ml (opcional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotEmpty()) {
                        val carbsVal = carbs.toFloatOrNull() ?: 0f
                        val calVal = calories.toFloatOrNull() ?: 0f
                        val gramsVal = grams.toFloatOrNull() ?: 100f
                        val food = FoodItem(
                            id = (100000 + (1..900000).random()),
                            name = name.trim(),
                            measure = measure.trim(),
                            grams = gramsVal,
                            carbs = carbsVal,
                            calories = calVal
                        )
                        onConfirm(food)
                    }
                },
                enabled = name.trim().isNotEmpty()
            ) {
                Text("Cadastrar e Adicionar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

// Extension function to check if float is integer
fun Float.isInteger(): Boolean {
    return this % 1 == 0f
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarbCounterScreen(
    viewModel: GlucoseViewModel,
    onNavigateToEditMeal: (Long) -> Unit
) {
    val allRecords by viewModel.allRecords.collectAsState()
    var selectedDate by remember { mutableStateOf(Calendar.getInstance()) }
    val context = LocalContext.current
    
    // Filter records for the selected date to find meals (carbs != null)
    val dayMeals = remember(allRecords, selectedDate) {
        allRecords.filter { record ->
            val isMeal = record.carbs != null
            if (!isMeal) return@filter false
            
            val recordCal = Calendar.getInstance().apply { timeInMillis = record.timestamp }
            recordCal.get(Calendar.YEAR) == selectedDate.get(Calendar.YEAR) &&
            recordCal.get(Calendar.DAY_OF_YEAR) == selectedDate.get(Calendar.DAY_OF_YEAR)
        }
    }
    
    val totalCarbs = dayMeals.sumOf { it.carbs?.toDouble() ?: 0.0 }.toFloat()
    val totalCalories = dayMeals.sumOf { it.calories?.toDouble() ?: 0.0 }.toFloat()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Histórico de Refeições", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Dashboard summary card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Consumo do Dia", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${totalCarbs.toInt()} g CHO",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${totalCalories.toInt()} kcal",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                    )
                }
            }
            
            // Date Navigation Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                IconButton(onClick = { 
                    selectedDate = (selectedDate.clone() as Calendar).apply { add(Calendar.DATE, -1) }
                }) {
                    Icon(Icons.Default.KeyboardArrowLeft, null)
                }
                
                TextButton(onClick = {
                    android.app.DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            selectedDate = Calendar.getInstance().apply {
                                set(year, month, dayOfMonth)
                            }
                        },
                        selectedDate.get(Calendar.YEAR),
                        selectedDate.get(Calendar.MONTH),
                        selectedDate.get(Calendar.DAY_OF_MONTH)
                    ).show()
                }) {
                    Text(
                        text = if (isToday(selectedDate)) "Hoje" else SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(selectedDate.time),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = { 
                    selectedDate = (selectedDate.clone() as Calendar).apply { add(Calendar.DATE, 1) }
                }) {
                    Icon(Icons.Default.KeyboardArrowRight, null)
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            Text(
                text = "Refeições Registradas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            if (dayMeals.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nenhuma refeição registrada nesta data.\nToque no botão + para adicionar.",
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(dayMeals, key = { it.timestamp }) { record ->
                        val parts = record.note.split(": ", limit = 2)
                        val mealType = parts.getOrNull(0) ?: "Refeição"
                        val mealDetails = parts.getOrNull(1) ?: record.note
                        
                        val timeText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(record.timestamp))
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToEditMeal(record.timestamp) },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = mealType,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = timeText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = mealDetails,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        Text(
                                            text = "${record.carbs?.toInt() ?: 0}g CHO",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${record.calories?.toInt() ?: 0} kcal",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color.Gray
                                        )
                                    }
                                }
                                
                                IconButton(
                                    onClick = { viewModel.deleteRecord(record) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Excluir refeição",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
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

fun parseMealItems(note: String, allFoods: List<FoodItem>): List<MealItem> {
    val parts = note.split(": ", limit = 2)
    if (parts.size < 2) return emptyList()
    val details = parts[1]
    
    val items = mutableListOf<MealItem>()
    val regex = Regex("""(.+?)\s*\((\d+(?:\.\d+)?)[xX]\)""")
    val matches = regex.findAll(details)
    for (match in matches) {
        val rawName = match.groupValues[1]
        val name = rawName.trim().removePrefix(",").trim()
        val multiplierStr = match.groupValues[2]
        val multiplier = multiplierStr.toFloatOrNull() ?: 1.0f
        
        val food = allFoods.find { it.name.trim().lowercase() == name.lowercase() }
        if (food != null) {
            items.add(MealItem(food, multiplier))
        }
    }
    return items
}

/**
 * Parses structured JSON mealDetails (preferred over regex).
 * Format: [{"name":"Arroz branco","multiplier":2.0},{...}]
 */
fun parseMealItemsFromJson(json: String, allFoods: List<FoodItem>): List<MealItem> {
    return try {
        val array = JSONArray(json)
        val items = mutableListOf<MealItem>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val name = obj.getString("name")
            val multiplier = obj.getDouble("multiplier").toFloat()
            val food = allFoods.find { it.name.trim().lowercase() == name.trim().lowercase() }
            if (food != null) {
                items.add(MealItem(food, multiplier))
            }
        }
        items
    } catch (e: Exception) {
        android.util.Log.e("CarbCounter", "Error parsing mealDetails JSON", e)
        emptyList()
    }
}
