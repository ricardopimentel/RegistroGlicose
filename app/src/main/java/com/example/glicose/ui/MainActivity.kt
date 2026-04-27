package com.example.glicose.ui

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import android.os.Build
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.example.glicose.data.Reminder
import com.example.glicose.notifications.ReminderReceiver
import com.example.glicose.utils.CsvExporter
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GlucoseApp()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlucoseApp(viewModel: GlucoseViewModel = viewModel()) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? MainActivity
    
    // Permission Handling
    PermissionHandler(context)

    var showAddDialogFromNotification by remember { 
        mutableStateOf(activity?.intent?.getBooleanExtra("OPEN_ADD_DIALOG", false) ?: false) 
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, null) },
                    label = { Text("Início") },
                    selected = currentRoute == "dashboard",
                    onClick = { navController.navigate("dashboard") }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.List, null) },
                    label = { Text("Relatórios") },
                    selected = currentRoute == "reports",
                    onClick = { navController.navigate("reports") }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Notifications, null) },
                    label = { Text("Lembretes") },
                    selected = currentRoute == "reminders",
                    onClick = { navController.navigate("reminders") }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, null) },
                    label = { Text("Ajustes") },
                    selected = currentRoute == "settings",
                    onClick = { navController.navigate("settings") }
                )
            }
        },
        floatingActionButton = {
            if (currentRoute == "dashboard" || currentRoute == "reports") {
                FloatingActionButton(onClick = { showAddDialogFromNotification = true }) {
                    Icon(Icons.Default.Add, "Adicionar Medição")
                }
            } else if (currentRoute == "reminders") {
                FloatingActionButton(onClick = {
                    android.app.TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            viewModel.addReminder(hour, minute)
                            scheduleNotification(context, hour, minute)
                        },
                        8, 0, android.text.format.DateFormat.is24HourFormat(context)
                    ).show()
                }) {
                    Icon(Icons.Default.Notifications, "Adicionar Lembrete")
                }
            }
        }
    ) { innerPadding ->
        NavHost(navController, startDestination = "dashboard", Modifier.padding(innerPadding)) {
            composable("dashboard") { DashboardScreen(viewModel) }
            composable("reports") { ReportsScreen(viewModel) }
            composable("reminders") { RemindersScreen(viewModel) }
            composable("settings") { SettingsScreen(viewModel) }
        }

        if (showAddDialogFromNotification) {
            AddRecordDialog(
                onDismiss = { 
                    showAddDialogFromNotification = false 
                    activity?.intent?.removeExtra("OPEN_ADD_DIALOG")
                },
                onSave = { value, note ->
                    viewModel.addRecord(value.toFloat(), note)
                    showAddDialogFromNotification = false
                    activity?.intent?.removeExtra("OPEN_ADD_DIALOG")
                }
            )
        }
    }
}

@Composable
fun DashboardScreen(viewModel: GlucoseViewModel) {
    val allRecords by viewModel.allRecords.collectAsState()
    var selectedDate by remember { mutableStateOf(Calendar.getInstance()) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // Filter records for the selected date
    val dayRecords = allRecords.filter { record ->
        val recordCal = Calendar.getInstance().apply { timeInMillis = record.timestamp }
        recordCal.get(Calendar.YEAR) == selectedDate.get(Calendar.YEAR) &&
        recordCal.get(Calendar.DAY_OF_YEAR) == selectedDate.get(Calendar.DAY_OF_YEAR)
    }
    
    val latestOfDay = dayRecords.firstOrNull() // Records are sorted by timestamp DESC

    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Olá!", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(Modifier.padding(top = 24.dp, start = 24.dp, end = 24.dp, bottom = 12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (isToday(selectedDate)) "Última Medição" else "Última do Dia", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = latestOfDay?.value?.toInt()?.toString() ?: "--",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Black
                )
                Text("mg/dL", style = MaterialTheme.typography.bodyLarge)
                latestOfDay?.let {
                    Text(
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                
                Spacer(Modifier.height(16.dp))
                
                // Date Navigation Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
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
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        if (dayRecords.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().padding(vertical = 64.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Não há registros nesta data",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Gray
                )
            }
        } else {
            Text("Tendência do Dia", style = MaterialTheme.typography.titleMedium)
            GlucoseChart(dayRecords)
            
            Spacer(Modifier.height(24.dp))
            Text("Medições do dia", style = MaterialTheme.typography.titleMedium)
            
            dayRecords.forEach { record ->
                ListItem(
                    headlineContent = { Text("${record.value.toInt()} mg/dL") },
                    supportingContent = { Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(record.timestamp))) }
                )
            }
        }
    }
}

fun isToday(cal: Calendar): Boolean {
    val today = Calendar.getInstance()
    return cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
           cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
}

@Composable
fun GlucoseChart(records: List<com.example.glicose.data.GlucoseRecord>) {
    if (records.size < 2) {
        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            Text("Dados insuficientes para o gráfico", color = Color.Gray)
        }
        return
    }

    val last7 = records.take(7).reversed()
    val maxValue = last7.maxOf { it.value }
    val minValue = last7.minOf { it.value }
    val range = (maxValue - minValue).coerceAtLeast(20f)
    val padding = range * 0.1f

    Canvas(modifier = Modifier.fillMaxWidth().height(200.dp).padding(vertical = 16.dp)) {
        val width = size.width
        val height = size.height
        val spacing = width / (last7.size - 1)

        val path = Path()
        val fillPath = Path()

        last7.forEachIndexed { i, record ->
            val x = i * spacing
            val y = height - ((record.value - (minValue - padding)) / (range + 2 * padding) * height)
            
            if (i == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, height)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(width, height)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF8B5CF6).copy(alpha = 0.3f), Color.Transparent)
            )
        )
        drawPath(
            path = path,
            color = Color(0xFF8B5CF6),
            style = Stroke(width = 3.dp.toPx())
        )
        
        // Draw points
        last7.forEachIndexed { i, record ->
            val x = i * spacing
            val y = height - ((record.value - (minValue - padding)) / (range + 2 * padding) * height)
            drawCircle(Color(0xFF8B5CF6), radius = 4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, y))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(viewModel: GlucoseViewModel) {
    val allRecords by viewModel.allRecords.collectAsState()
    var selectedDays by remember { mutableStateOf(7) }
    var editingRecord by remember { mutableStateOf<com.example.glicose.data.GlucoseRecord?>(null) }

    val filteredRecords = remember(allRecords, selectedDays) {
        if (selectedDays == 999) allRecords
        else {
            val cutoff = Calendar.getInstance().apply { 
                add(Calendar.DATE, -selectedDays)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }.timeInMillis
            allRecords.filter { it.timestamp >= cutoff }
        }
    }

    // Calculations
    val avg = if (filteredRecords.isNotEmpty()) filteredRecords.map { it.value }.average() else 0.0
    // eA1c formula: (Average + 46.7) / 28.7
    val eA1c = if (avg > 0) (avg + 46.7) / 28.7 else 0.0
    val maxVal = filteredRecords.maxOfOrNull { it.value } ?: 0f
    val minVal = filteredRecords.minOfOrNull { it.value } ?: 0f

    // Time in range
    val inRange = filteredRecords.count { it.value in 70f..140f }
    val high = filteredRecords.count { it.value > 140f }
    val low = filteredRecords.count { it.value < 70f }
    val total = filteredRecords.size.coerceAtLeast(1)

    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Relatórios", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        // Period Selector
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7 to "7 dias", 30 to "30 dias", 999 to "Tudo").forEach { (days, label) ->
                FilterChip(
                    selected = selectedDays == days,
                    onClick = { selectedDays = days },
                    label = { Text(label) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // KPI Grid
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ReportCard(Modifier.weight(1f), "Média", "${avg.toInt()}", "mg/dL", MaterialTheme.colorScheme.primary)
            ReportCard(Modifier.weight(1f), "eA1c", String.format("%.1f", eA1c), "%", MaterialTheme.colorScheme.secondary)
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ReportCard(Modifier.weight(1f), "Máxima", "${maxVal.toInt()}", "mg/dL", Color(0xFFFF5252))
            ReportCard(Modifier.weight(1f), "Mínima", "${minVal.toInt()}", "mg/dL", Color(0xFF448AFF))
        }

        Spacer(Modifier.height(24.dp))
        Text("Tempo no Alvo (70-140 mg/dL)", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        TimeInRangeBar(low.toFloat()/total, inRange.toFloat()/total, high.toFloat()/total)

        Spacer(Modifier.height(32.dp))
        Text("Histórico do Período", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        
        if (filteredRecords.isEmpty()) {
            Text("Nenhum dado no período selecionado", color = Color.Gray)
        } else {
            filteredRecords.forEach { record ->
                HistoryCard(
                    record = record, 
                    onDelete = { viewModel.deleteRecord(record) }, 
                    onEdit = { editingRecord = record }
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    editingRecord?.let { record ->
        AddRecordDialog(
            onDismiss = { editingRecord = null },
            onSave = { value, note ->
                viewModel.updateRecord(record, value.toFloat(), note)
                editingRecord = null
            },
            initialValue = record.value.toInt().toString(),
            initialNote = record.note
        )
    }
}

@Composable
fun ReportCard(modifier: Modifier, title: String, value: String, unit: String, color: Color) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = color)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = color)
                Text(unit, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp), color = color)
            }
        }
    }
}

@Composable
fun TimeInRangeBar(low: Float, inRange: Float, high: Float) {
    Column {
        Row(Modifier.fillMaxWidth().height(24.dp).clip(RoundedCornerShape(12.dp))) {
            Box(Modifier.weight(low.coerceAtLeast(0.01f)).fillMaxHeight().background(Color(0xFF448AFF)))
            Box(Modifier.weight(inRange.coerceAtLeast(0.01f)).fillMaxHeight().background(Color(0xFF4CAF50)))
            Box(Modifier.weight(high.coerceAtLeast(0.01f)).fillMaxHeight().background(Color(0xFFFF5252)))
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            LegendItem("Baixo", Color(0xFF448AFF), "${(low * 100).toInt()}%")
            LegendItem("Alvo", Color(0xFF4CAF50), "${(inRange * 100).toInt()}%")
            LegendItem("Alto", Color(0xFFFF5252), "${(high * 100).toInt()}%")
        }
    }
}

@Composable
fun LegendItem(label: String, color: Color, percent: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(" $label: ", style = MaterialTheme.typography.labelSmall)
        Text(percent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryCard(
    record: com.example.glicose.data.GlucoseRecord, 
    onDelete: () -> Unit, 
    onEdit: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { /* Opcional */ },
                onLongClick = { showMenu = true }
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Box {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "${record.value.toInt()} mg/dL",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(record.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                    if (record.note.isNotEmpty()) {
                        Text(
                            text = record.note,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Editar") },
                    onClick = { 
                        showMenu = false
                        onEdit()
                    },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Excluir") },
                    onClick = { 
                        showMenu = false
                        onDelete()
                    },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) }
                )
            }
        }
    }
}

@Composable
fun RemindersScreen(viewModel: GlucoseViewModel) {
    val reminders by viewModel.allReminders.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(Modifier.padding(16.dp)) {
        Text("Lembretes", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        if (reminders.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nenhum lembrete agendado", color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(reminders) { reminder ->
                    ReminderCard(
                        reminder = reminder,
                        onToggle = { enabled ->
                            viewModel.toggleReminder(reminder)
                            if (enabled) scheduleNotification(context, reminder.hour, reminder.minute)
                        },
                        onDelete = { viewModel.deleteReminder(reminder) },
                        onEdit = {
                            android.app.TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    viewModel.updateReminder(reminder, hour, minute)
                                    scheduleNotification(context, hour, minute)
                                },
                                reminder.hour,
                                reminder.minute,
                                android.text.format.DateFormat.is24HourFormat(context)
                            ).show()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReminderCard(
    reminder: Reminder, 
    onToggle: (Boolean) -> Unit, 
    onDelete: () -> Unit, 
    onEdit: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { /* Opcional: clique simples pode alternar o estado */ },
                onLongClick = { showMenu = true }
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (reminder.enabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Box {
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val timeText = remember(reminder.hour, reminder.minute) {
                        val cal = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, reminder.hour)
                            set(Calendar.MINUTE, reminder.minute)
                        }
                        android.text.format.DateFormat.getTimeFormat(context).format(cal.time)
                    }
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (reminder.enabled) "Ativo" else "Desativado",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (reminder.enabled) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
                Switch(
                    checked = reminder.enabled,
                    onCheckedChange = onToggle
                )
            }
            
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Editar") },
                    onClick = { 
                        showMenu = false
                        onEdit()
                    },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Excluir") },
                    onClick = { 
                        showMenu = false
                        onDelete()
                    },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) }
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(viewModel: GlucoseViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val records by viewModel.allRecords.collectAsState()
    
    var vibrationEnabled by remember { mutableStateOf(true) }
    var notificationVolume by remember { mutableStateOf(0.7f) }

    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Ajustes", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        // Notificações Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Notificações", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Vibração", Modifier.weight(1f))
                    Switch(checked = vibrationEnabled, onCheckedChange = { vibrationEnabled = it })
                }
                
                Spacer(Modifier.height(16.dp))
                Text("Volume do Lembrete")
                Slider(
                    value = notificationVolume,
                    onValueChange = { notificationVolume = it },
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { 
                        val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Settings, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Configurações do Sistema")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Exportação", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("Exporte seus dados para uma planilha CSV e compartilhe com seu médico.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { CsvExporter.exportAndHandle(context, records) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Share, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Exportar e Compartilhar CSV")
                }
            }
        }
    }
}

@Composable
fun AddRecordDialog(
    onDismiss: () -> Unit, 
    onSave: (String, String) -> Unit,
    initialValue: String = "",
    initialNote: String = ""
) {
    var value by remember { mutableStateOf(initialValue) }
    var note by remember { mutableStateOf(initialNote) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialValue.isEmpty()) "Nova Medição" else "Editar Medição") },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Valor (mg/dL)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Nota (ex: Em jejum)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { if (value.isNotEmpty()) onSave(value, note) }) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

fun scheduleNotification(context: Context, hour: Int, minute: Int) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, ReminderReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
        context, 0, intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        if (before(Calendar.getInstance())) {
            add(Calendar.DATE, 1)
        }
    }

    alarmManager.setExactAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        calendar.timeInMillis,
        pendingIntent
    )
    Toast.makeText(context, "Lembrete agendado para ${hour}:${String.format("%02d", minute)}", Toast.LENGTH_SHORT).show()
}

@Composable
fun PermissionHandler(context: Context) {
    // Notification Permission (Android 13+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (!isGranted) {
                Toast.makeText(context, "Permissão de notificação negada. Você não receberá lembretes.", Toast.LENGTH_LONG).show()
            }
        }

        LaunchedEffect(Unit) {
            val permission = android.Manifest.permission.POST_NOTIFICATIONS
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                launcher.launch(permission)
            }
        }
    }

    // Exact Alarm Permission (Android 12+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (!alarmManager.canScheduleExactAlarms()) {
            var showAlarmDialog by remember { mutableStateOf(true) }
            if (showAlarmDialog) {
                AlertDialog(
                    onDismissRequest = { showAlarmDialog = false },
                    title = { Text("Permissão Necessária") },
                    text = { Text("Para que os alarmes funcionem exatamente no horário, o app precisa da permissão de 'Alarmes e Lembretes'.") },
                    confirmButton = {
                        Button(onClick = {
                            showAlarmDialog = false
                            context.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            })
                        }) {
                            Text("Configurar")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAlarmDialog = false }) { Text("Depois") }
                    }
                )
            }
        }
    }
}
