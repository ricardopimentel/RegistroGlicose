package com.example.glicose.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import com.example.glicose.data.FoodItem
import com.example.glicose.utils.CsvExporter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomFoodListScreen(
    viewModel: GlucoseViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val customFoods by viewModel.customFoods.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var foodToEdit by remember { mutableStateOf<FoodItem?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<CsvExporter.CustomFoodCsvResult?>(null) }

    // Keyword-based search (same logic as in AddMealScreen)
    val filteredFoods = remember(searchQuery, customFoods) {
        if (searchQuery.length < 2) customFoods
        else {
            val normalized = searchQuery.normalizeForSearch()
            val words = normalized.split(" ").filter { it.isNotEmpty() }
            if (words.isEmpty()) customFoods
            else customFoods.filter { food ->
                val nameNorm = food.name.normalizeForSearch()
                words.all { nameNorm.contains(it) }
            }
        }
    }

    // File picker launcher for CSV import
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val result = CsvExporter.parseCustomFoodsCsv(context, uri)
            if (result.foods.isEmpty()) {
                Toast.makeText(
                    context,
                    "Nenhum alimento válido encontrado no arquivo (${result.invalidLines} linhas inválidas).",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                importResult = result
                showImportDialog = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Alimentos Personalizados", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, "Voltar") }
                },
                actions = {
                    // Export button
                    IconButton(onClick = {
                        CsvExporter.exportCustomFoods(context, customFoods)
                    }) {
                        Icon(Icons.Default.Upload, contentDescription = "Exportar CSV")
                    }
                    // Import button
                    IconButton(onClick = {
                        importLauncher.launch("text/*")
                    }) {
                        Icon(Icons.Default.Download, contentDescription = "Importar CSV")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                windowInsets = WindowInsets(0)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                placeholder = { Text("Buscar alimento (ex: leite, arroz …)") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, null) }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(filteredFoods) { food ->
                    ListItem(
                        headlineContent = { Text(food.name, fontWeight = FontWeight.SemiBold) },
                        supportingContent = {
                            Text("${food.measure} (${food.grams.toInt()}g) • ${food.carbs.toInt()}g CHO • ${food.calories.toInt()} kcal")
                        },
                        trailingContent = {
                            IconButton(onClick = { foodToEdit = food }) {
                                Icon(Icons.Default.Edit, "Editar", tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        modifier = Modifier.clickable { foodToEdit = food }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    // Edit dialog
    if (foodToEdit != null) {
        EditCustomFoodDialog(
            food = foodToEdit!!,
            onDismiss = { foodToEdit = null },
            onConfirm = { updatedFood ->
                viewModel.updateCustomFood(updatedFood)
                foodToEdit = null
            }
        )
    }

    // Import confirmation dialog
    if (showImportDialog && importResult != null) {
        val result = importResult!!
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            icon = { Icon(Icons.Default.Download, contentDescription = null) },
            title = { Text("Importar Alimentos", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Foram encontrados ${result.foods.size} alimento(s) válido(s) no arquivo.")
                    if (result.invalidLines > 0) {
                        Text(
                            "${result.invalidLines} linha(s) inválida(s) foram ignoradas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Text(
                        "Deseja adicionar esses alimentos à sua lista? Duplicatas não serão removidas automaticamente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    result.foods.forEach { viewModel.addCustomFood(it) }
                    Toast.makeText(
                        context,
                        "${result.foods.size} alimento(s) importado(s) com sucesso!",
                        Toast.LENGTH_LONG
                    ).show()
                    showImportDialog = false
                    importResult = null
                }) {
                    Icon(Icons.Default.Check, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Importar ${result.foods.size} alimento(s)")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportDialog = false
                    importResult = null
                }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
fun EditCustomFoodDialog(
    food: FoodItem,
    onDismiss: () -> Unit,
    onConfirm: (FoodItem) -> Unit
) {
    var name by remember { mutableStateOf(food.name) }
    var measure by remember { mutableStateOf(food.measure) }
    var grams by remember { mutableStateOf(food.grams.toString()) }
    var carbs by remember { mutableStateOf(food.carbs.toString()) }
    var calories by remember { mutableStateOf(food.calories.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Alimento Personalizado", fontWeight = FontWeight.Bold) },
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
                    value = measure,
                    onValueChange = { measure = it },
                    label = { Text("Medida (ex: 1 porção)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = grams,
                    onValueChange = { grams = it },
                    label = { Text("Peso (g/ml)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val updated = FoodItem(
                            id = food.id,
                            name = name.trim(),
                            measure = measure.trim(),
                            grams = grams.toFloatOrNull() ?: food.grams,
                            carbs = carbs.toFloatOrNull() ?: food.carbs,
                            calories = calories.toFloatOrNull() ?: food.calories
                        )
                        onConfirm(updated)
                    }
                },
                enabled = name.isNotBlank()
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
