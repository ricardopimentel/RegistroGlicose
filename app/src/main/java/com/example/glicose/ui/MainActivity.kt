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
import com.google.firebase.auth.FirebaseAuth
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.navigation.compose.*
import coil.compose.AsyncImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.camera.view.PreviewView
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.ui.viewinterop.AndroidView
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
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
    val auth = FirebaseAuth.getInstance()
    
    // Permission Handling
    PermissionHandler(context)

    var showAddDialogFromNotification by remember { 
        mutableStateOf(activity?.intent?.getBooleanExtra("OPEN_ADD_DIALOG", false) ?: false) 
    }

    Scaffold(
        bottomBar = {
            if (currentRoute != "login") {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, null) },
                        label = { Text("Início") },
                        selected = currentRoute == "dashboard",
                        onClick = { navController.navigate("dashboard") { popUpTo(0) } }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.List, null) },
                        label = { Text("Relatórios") },
                        selected = currentRoute == "reports",
                        onClick = { navController.navigate("reports") { popUpTo(0) } }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Notifications, null) },
                        label = { Text("Lembretes") },
                        selected = currentRoute == "reminders",
                        onClick = { navController.navigate("reminders") { popUpTo(0) } }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Settings, null) },
                        label = { Text("Ajustes") },
                        selected = currentRoute == "settings",
                        onClick = { navController.navigate("settings") { popUpTo(0) } }
                    )
                }
            }
        },
        floatingActionButton = {
            val currentViewUid by viewModel.currentUserId.collectAsState()
            val myUid = auth.currentUser?.uid ?: ""
            
            if (currentRoute != "login" && currentViewUid == myUid) {
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
        }
    ) { innerPadding ->
        val startDestination = if (auth.currentUser != null) "dashboard" else "login"
        NavHost(navController, startDestination = startDestination, Modifier.padding(innerPadding)) {
            composable("login") { 
                LoginScreen(onLoginSuccess = { 
                    viewModel.setCurrentUserId(auth.currentUser?.uid ?: "")
                    navController.navigate("dashboard") { 
                        popUpTo("login") { inclusive = true } 
                    } 
                }) 
            }
            composable("dashboard") { DashboardScreen(viewModel) }
            composable("reports") { ReportsScreen(viewModel) }
            composable("reminders") { RemindersScreen(viewModel) }
            composable("settings") { 
                SettingsScreen(viewModel, navController) {
                    auth.signOut()
                    
                    // Also sign out from Google to allow selecting a different account
                    val webClientId = context.resources.getString(
                        context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
                    )
                    val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(webClientId)
                        .requestEmail()
                        .build()
                    com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso).signOut()
                    
                    navController.navigate("login") { 
                        popUpTo(0) 
                    }
                } 
            }
            composable("manage_following") { ManageFollowingScreen(viewModel, navController) }
            composable("manage_followers") { ManageFollowersScreen(viewModel, navController) }
        }

        if (showAddDialogFromNotification && auth.currentUser != null) {
            AddRecordDialog(
                onDismiss = { 
                    showAddDialogFromNotification = false 
                    activity?.intent?.removeExtra("OPEN_ADD_DIALOG")
                },
                onSave = { value, note, timestamp ->
                    viewModel.addRecord(value.toFloat(), note, timestamp)
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

    val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
    val currentViewUid by viewModel.currentUserId.collectAsState()
    val followedUsers by viewModel.followedUsers.collectAsState()
    
    val myName = auth.currentUser?.displayName?.split(" ")?.firstOrNull() ?: "Eu"
    val myUid = auth.currentUser?.uid ?: ""
    
    val currentViewName = if (currentViewUid == myUid) "Olá, $myName!" 
                         else followedUsers.find { it.second == currentViewUid }?.first ?: "Paciente"

    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        var showProfileMenu by remember { mutableStateOf(false) }
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(currentViewName, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            
            if (followedUsers.isNotEmpty()) {
                Box {
                    IconButton(onClick = { showProfileMenu = true }) {
                        Icon(Icons.Default.KeyboardArrowDown, null)
                    }
                    DropdownMenu(expanded = showProfileMenu, onDismissRequest = { showProfileMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Meu Perfil ($myName)") },
                            onClick = { 
                                viewModel.setCurrentUserId(myUid)
                                showProfileMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.Person, null) }
                        )
                        followedUsers.forEach { (name, uid) ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = { 
                                    viewModel.setCurrentUserId(uid)
                                    showProfileMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.AccountCircle, null) }
                            )
                        }
                    }
                }
            }
        }
        
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
            onSave = { value, note, timestamp ->
                viewModel.updateRecord(record, value.toFloat(), note)
                editingRecord = null
            },
            initialValue = record.value.toInt().toString(),
            initialNote = record.note,
            initialTimestamp = record.timestamp
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
fun SettingsScreen(viewModel: GlucoseViewModel, navController: androidx.navigation.NavController, onLogout: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val records by viewModel.allRecords.collectAsState()
    val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
    val user = auth.currentUser
    
    var vibrationEnabled by remember { mutableStateOf(true) }
    var notificationVolume by remember { mutableStateOf(0.7f) }

    // Camera Permission Request
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Permissão de câmera negada", Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Ajustes", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        // Perfil do Usuário
        if (user != null) {
            val userCode = user.uid.take(6).uppercase()
            var showQrCodeDialog by remember { mutableStateOf(false) }
            val clipboardManager = LocalClipboardManager.current
            
            Spacer(Modifier.height(40.dp)) // Espaço para a imagem sair metade pra fora

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        Modifier.padding(top = 50.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(user.displayName ?: "Sem nome", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(user.email ?: "Sem e-mail", style = MaterialTheme.typography.bodyMedium)
                        
                        Spacer(Modifier.height(24.dp))
                        
                        Text("Seu Código de Compartilhamento:", style = MaterialTheme.typography.labelMedium)
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(userCode, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(Modifier.width(8.dp))
                            IconButton(onClick = { 
                                clipboardManager.setText(AnnotatedString(userCode))
                                Toast.makeText(context, "Código copiado!", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, "Copiar", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { showQrCodeDialog = true }) {
                                Icon(Icons.Default.QrCode, "QR Code", tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        var isSyncing by remember { mutableStateOf(false) }
                        Button(
                            onClick = { 
                                isSyncing = true
                                viewModel.syncLocalDataToCloud {
                                    isSyncing = false
                                    Toast.makeText(context, "Sincronização concluída!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isSyncing,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                            } else {
                                Icon(Icons.Default.Sync, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Sincronizar Dados com Nuvem")
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = onLogout,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Sair da Conta")
                        }
                    }
                }

                // Imagem de Perfil Redonda e Centralizada (Metade pra fora)
                Surface(
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape,
                    border = BorderStroke(4.dp, MaterialTheme.colorScheme.surface),
                    shadowElevation = 8.dp
                ) {
                    AsyncImage(
                        model = user.photoUrl ?: "https://www.gravatar.com/avatar/00000000000000000000000000000000?d=mp&f=y",
                        contentDescription = "Foto de Perfil",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            
            if (showQrCodeDialog) {
                QrCodeDialog(code = userCode, onDismiss = { showQrCodeDialog = false })
            }
            Spacer(Modifier.height(16.dp))
        }

        // Acompanhamento
        var showFollowDialog by remember { mutableStateOf(false) }
        var showScanner by remember { mutableStateOf(false) }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Compartilhamento", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("Acompanhe os dados de glicose de um paciente ou familiar usando o código dele.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))
                
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { showFollowDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PersonAdd, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Adicionar")
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = { 
                            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                showScanner = true 
                            } else {
                                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.QrCodeScanner, "Escanear QR Code")
                    }
                }
            }
        }

        if (showFollowDialog) {
            FollowPatientDialog(
                onDismiss = { showFollowDialog = false },
                onFollow = { code ->
                    viewModel.followUser(
                        code = code,
                        onSuccess = { showFollowDialog = false },
                        onError = { error ->
                            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                        }
                    )
                }
            )
        }
        
        if (showScanner) {
            QrScannerDialog(
                onDismiss = { showScanner = false },
                onCodeScanned = { code ->
                    showScanner = false
                    viewModel.followUser(
                        code = code,
                        onSuccess = { },
                        onError = { error ->
                            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                        }
                    )
                }
            )
        }

        Spacer(Modifier.height(16.dp))

        // Gerenciamento de Conexões
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Gerenciar Conexões", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))

                OutlinedButton(
                    onClick = { navController.navigate("manage_following") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Group, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Pessoas que eu sigo")
                }

                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { navController.navigate("manage_followers") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.GroupAdd, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Quem vê meus dados")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

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

        // Backup Card
        val importCsvLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                try {
                    val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return@rememberLauncherForActivityResult
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.useLines { lines ->
                        lines.drop(1).forEach { line ->
                            val parts = line.split(",")
                            if (parts.size >= 2) {
                                val value = parts[0].trim().toFloatOrNull() ?: return@forEach
                                val note = parts.getOrElse(1) { "" }.trim()
                                val timestamp = parts.getOrElse(2) { "" }.trim().toLongOrNull() ?: System.currentTimeMillis()
                                viewModel.addRecord(value, note, timestamp)
                            }
                        }
                    }
                    Toast.makeText(context, "Importação concluída!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Erro na importação: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Backup de Dados", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("Exporte ou importe seus registros em formato CSV.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { CsvExporter.exportAndHandle(context, records) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Share, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Exportar CSV")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { importCsvLauncher.launch("text/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FileUpload, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Importar CSV")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageFollowingScreen(viewModel: GlucoseViewModel, navController: androidx.navigation.NavController) {
    val following by viewModel.followedUsers.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pessoas que sigo") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        if (following.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Você não está seguindo ninguém.", color = Color.Gray)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                items(following) { (name, uid) ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.width(16.dp))
                            Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            IconButton(onClick = { viewModel.unfollow(uid) }) {
                                Icon(Icons.Default.Delete, "Remover", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageFollowersScreen(viewModel: GlucoseViewModel, navController: androidx.navigation.NavController) {
    val followers by viewModel.followers.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quem vê meus dados") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        if (followers.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Ninguém está vendo seus dados.", color = Color.Gray)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                items(followers) { (name, uid) ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.width(16.dp))
                            Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            IconButton(onClick = { viewModel.removeFollower(uid) }) {
                                Icon(Icons.Default.PersonRemove, "Remover Acesso", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QrCodeDialog(code: String, onDismiss: () -> Unit) {
    val bitmap = remember(code) { generateQrCode(code) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seu QR Code") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "QR Code",
                        modifier = Modifier.size(200.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(code, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Fechar") }
        }
    )
}

fun generateQrCode(text: String): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, 512, 512)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}

@Composable
fun QrScannerDialog(onDismiss: () -> Unit, onCodeScanned: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scanner = remember { BarcodeScanning.getClient() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Escanear Código") },
        text = {
            Box(Modifier.size(300.dp).clip(RoundedCornerShape(16.dp))) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            
                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                            
                            imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                                val mediaImage = imageProxy.image
                                if (mediaImage != null) {
                                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                    scanner.process(image)
                                        .addOnSuccessListener { barcodes ->
                                            for (barcode in barcodes) {
                                                val rawValue = barcode.rawValue
                                                if (rawValue != null && rawValue.length == 6) {
                                                    onCodeScanned(rawValue)
                                                }
                                            }
                                        }
                                        .addOnCompleteListener {
                                            imageProxy.close()
                                        }
                                } else {
                                    imageProxy.close()
                                }
                            }

                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        },
        confirmButton = { },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun FollowPatientDialog(onDismiss: () -> Unit, onFollow: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar Paciente") },
        text = {
            Column {
                Text("Digite o código de 6 dígitos que aparece nos ajustes do celular do paciente.")
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { if (it.length <= 6) code = it.uppercase() },
                    label = { Text("Código (ex: A7X9WQ)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = { if (code.length == 6) onFollow(code) }) {
                Text("Adicionar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun AddRecordDialog(
    onDismiss: () -> Unit, 
    onSave: (String, String, Long) -> Unit,
    initialValue: String = "",
    initialNote: String = "",
    initialTimestamp: Long = System.currentTimeMillis()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var value by remember { mutableStateOf(initialValue) }
    var note by remember { mutableStateOf(initialNote) }
    var selectedTimestamp by remember { mutableStateOf(initialTimestamp) }

    // Format timestamp for display
    val dateTimeText = remember(selectedTimestamp) {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = selectedTimestamp }
        val fmt = android.text.format.DateFormat.getDateFormat(context)
        val timeFmt = android.text.format.DateFormat.getTimeFormat(context)
        "${fmt.format(cal.time)}  ${timeFmt.format(cal.time)}"
    }

    fun showDateTimePicker() {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = selectedTimestamp }
        android.app.DatePickerDialog(
            context,
            { _, year, month, day ->
                android.app.TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        val newCal = java.util.Calendar.getInstance().apply {
                            set(year, month, day, hour, minute, 0)
                            set(java.util.Calendar.MILLISECOND, 0)
                        }
                        selectedTimestamp = newCal.timeInMillis
                    },
                    cal.get(java.util.Calendar.HOUR_OF_DAY),
                    cal.get(java.util.Calendar.MINUTE),
                    android.text.format.DateFormat.is24HourFormat(context)
                ).show()
            },
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH),
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialValue.isEmpty()) "Nova Medição" else "Editar Medição") },
        text = {
            Column {
                // Numeric glucose value field
                OutlinedTextField(
                    value = value,
                    onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 4) value = it },
                    label = { Text("Valor (mg/dL)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    trailingIcon = { Text("mg/dL", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp)) }
                )
                Spacer(Modifier.height(8.dp))
                // Date/Time field — tap to change
                OutlinedTextField(
                    value = dateTimeText,
                    onValueChange = {},
                    label = { Text("Data e Hora") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDateTimePicker() },
                    readOnly = true,
                    enabled = false,
                    trailingIcon = {
                        IconButton(onClick = { showDateTimePicker() }) {
                            Icon(Icons.Default.Schedule, "Alterar data e hora")
                        }
                    }
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
            Button(onClick = { if (value.isNotEmpty()) onSave(value, note, selectedTimestamp) }) {
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
