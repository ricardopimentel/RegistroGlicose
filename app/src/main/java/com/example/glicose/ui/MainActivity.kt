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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.pulltorefresh.*
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
import androidx.compose.ui.graphics.toArgb
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
import com.example.glicose.utils.PdfExporter
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
    
    // Theme Colors
    val primaryColor = Color(0xFFD0BCFF) // Light Purple for Dark Theme
    val primaryColorLight = Color(0xFF6750A4) // Vibrant Purple (M3 Primary)
    
    val appTheme by viewModel.appTheme.collectAsState()
    val isDarkTheme = when (appTheme) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }

    val colorScheme = if (isDarkTheme) {
        darkColorScheme(
            primary = primaryColor,
            onPrimary = Color(0xFF381E72),
            primaryContainer = Color(0xFF4F378B),
            onPrimaryContainer = Color(0xFFEADDFF),
            secondary = Color(0xFFCCC2DC),
            onSecondary = Color(0xFF332D41),
            surface = Color(0xFF1C1B1F),
            onSurface = Color(0xFFE6E1E5)
        )
    } else {
        lightColorScheme(
            primary = primaryColorLight,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFEADDFF),
            onPrimaryContainer = Color(0xFF21005D),
            secondary = Color(0xFF625B71),
            onSecondary = Color.White,
            surface = Color.White,
            onSurface = Color(0xFF1C1B1F)
        )
    }

    MaterialTheme(colorScheme = colorScheme) {
        val currentStatusBarColor = if (isDarkTheme) colorScheme.surface else primaryColorLight
        val isAppearanceLightStatusBars = isDarkTheme
        
        // Set Status Bar Color
        SideEffect {
            (context as? android.app.Activity)?.window?.apply {
                statusBarColor = currentStatusBarColor.toArgb()
                androidx.core.view.WindowCompat.getInsetsController(this, decorView).isAppearanceLightStatusBars = isAppearanceLightStatusBars
            }
        }

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
                when (currentRoute) {
                    "dashboard" -> FloatingActionButton(
                        onClick = { showAddDialogFromNotification = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    ) {
                        Icon(Icons.Default.Add, "Adicionar Medição")
                    }
                    "reminders" -> FloatingActionButton(
                        onClick = {
                            android.app.TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    viewModel.addReminder(hour, minute)
                                    scheduleNotification(context, hour, minute)
                                },
                                8, 0, android.text.format.DateFormat.is24HourFormat(context)
                            ).show()
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    ) {
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
}

@OptIn(ExperimentalMaterial3Api::class)
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

    var isRefreshing by remember { mutableStateOf(false) }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            viewModel.refreshData { isRefreshing = false }
        }
    ) {
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
    val targetMin by viewModel.targetMin.collectAsState()
    val targetMax by viewModel.targetMax.collectAsState()
    
    var selectedDays by remember { mutableStateOf(7) }
    var editingRecord by remember { mutableStateOf<com.example.glicose.data.GlucoseRecord?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

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

    val avg = if (filteredRecords.isNotEmpty()) filteredRecords.map { it.value }.average() else 0.0
    val eA1c = if (avg > 0) (avg + 46.7) / 28.7 else 0.0
    val maxVal = filteredRecords.maxOfOrNull { it.value } ?: 0f
    val minVal = filteredRecords.minOfOrNull { it.value } ?: 0f

    val inRange = filteredRecords.count { it.value in targetMin..targetMax }
    val high = filteredRecords.count { it.value > targetMax }
    val low = filteredRecords.count { it.value < targetMin }
    val total = filteredRecords.size.coerceAtLeast(1)

    // Export FAB state
    var fabExpanded by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
    var isRefreshing by remember { mutableStateOf(false) }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            viewModel.refreshData { isRefreshing = false }
        }
    ) {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Relatórios", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

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
            Text("Tempo no Alvo (${targetMin.toInt()}-${targetMax.toInt()} mg/dL)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            TimeInRangeBar(low.toFloat() / total, inRange.toFloat() / total, high.toFloat() / total)

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

            // Bottom padding so FAB doesn't overlap last item
            Spacer(Modifier.height(80.dp))
        }
    }

        // Scrim to close FAB when tapping outside (moved before FAB Column to be behind it)
        if (fabExpanded) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.1f)) // Added subtle dimming
                    .clickable { fabExpanded = false }
            )
        }

        // ── Export FAB (speed dial) ──────────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Mini action buttons (shown when expanded)
            if (fabExpanded) {
                // Dismiss scrim on tap outside
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 2.dp
                    ) {
                        Text(
                            "Exportar PDF",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    SmallFloatingActionButton(
                        onClick = {
                            fabExpanded = false
                            PdfExporter.exportAndShare(context, filteredRecords, targetMin, targetMax)
                        },
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Icon(Icons.Default.PictureAsPdf, "Exportar PDF")
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 2.dp
                    ) {
                        Text(
                            "Exportar CSV",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    SmallFloatingActionButton(
                        onClick = {
                            fabExpanded = false
                            CsvExporter.exportAndHandle(context, filteredRecords)
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(Icons.Default.TableChart, "Exportar CSV")
                    }
                }
            }

            // Main FAB
            FloatingActionButton(
                onClick = { fabExpanded = !fabExpanded },
                containerColor = if (fabExpanded) MaterialTheme.colorScheme.surfaceVariant
                                 else MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    if (fabExpanded) Icons.Default.Close else Icons.Default.FileDownload,
                    "Exportar"
                )
            }
        }

    }

    editingRecord?.let { record ->
        AddRecordDialog(
            onDismiss = { editingRecord = null },
            onSave = { value, note, timestamp ->
                viewModel.updateRecord(record, value.toFloat(), note, timestamp)
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

            val myUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
            if (record.userId == myUid) {
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

    var showFollowDialog by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var showQrCodeDialog by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) showScanner = true
        else Toast.makeText(context, "Permissão de câmera negada", Toast.LENGTH_SHORT).show()
    }

    // Import state: null = nenhum arquivo selecionado, non-null = resultado da validação
    var importPreviewState by remember { mutableStateOf<Pair<android.net.Uri, com.example.glicose.utils.CsvExporter.CsvParseResult>?>(null) }

    val importCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val result = CsvExporter.parseAndValidateBackupCsv(context, uri)
                importPreviewState = uri to result
            } catch (e: Exception) {
                Toast.makeText(context, "Erro ao ler arquivo: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Dialogs
    if (showFollowDialog) {
        FollowPatientDialog(
            onDismiss = { showFollowDialog = false },
            onFollow = { code ->
                viewModel.followUser(
                    code = code,
                    onSuccess = { showFollowDialog = false },
                    onError = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
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
                    onError = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                )
            }
        )
    }
    if (user != null && showQrCodeDialog) {
        QrCodeDialog(code = user.uid.take(6).uppercase(), onDismiss = { showQrCodeDialog = false })
    }

    // Dialog de preview e confirmação de importação
    importPreviewState?.let { (uri, result) ->
        ImportPreviewDialog(
            result = result,
            onConfirm = {
                viewModel.importRecords(result.validRecords) { count ->
                    importPreviewState = null
                    Toast.makeText(context, "$count registros importados!", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { importPreviewState = null }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // ── Header Banner ────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(top = 32.dp, bottom = 48.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (user != null) {
                    Surface(
                        modifier = Modifier.size(88.dp),
                        shape = CircleShape,
                        border = BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        AsyncImage(
                            model = user.photoUrl ?: "https://www.gravatar.com/avatar/00000000000000000000000000000000?d=mp&f=y",
                            contentDescription = "Foto de Perfil",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        user.displayName ?: "Sem nome",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        user.email ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // Content with padding
        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(16.dp))

            // ── Seção: Código de Compartilhamento ────────────────────────────
            if (user != null) {
                val userCode = user.uid.take(6).uppercase()
                val clipboardManager = LocalClipboardManager.current

                SettingsSection(title = "Código de Compartilhamento", icon = Icons.Default.Share) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Compartilhe este código com quem deseja que acompanhe seus dados.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            userCode,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 6.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                clipboardManager.setText(AnnotatedString(userCode))
                                Toast.makeText(context, "Código copiado!", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Copiar")
                            }
                            OutlinedButton(onClick = { showQrCodeDialog = true }) {
                                Icon(Icons.Default.QrCode, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("QR Code")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ── Seção: Adicionar Paciente ─────────────────────────────────
                SettingsSection(title = "Adicionar Acompanhamento", icon = Icons.Default.PersonAdd) {
                    Text(
                        "Adicione alguém pelo código ou lendo o QR Code.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { showFollowDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Por Código")
                        }
                        FilledIconButton(
                            onClick = {
                                if (androidx.core.content.ContextCompat.checkSelfPermission(
                                        context, android.Manifest.permission.CAMERA
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                ) showScanner = true
                                else cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.QrCodeScanner, "Escanear QR Code")
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ── Seção: Conexões ───────────────────────────────────────────
                SettingsSection(title = "Gerenciar Conexões", icon = Icons.Default.Group) {
                    SettingsNavItem(
                        icon = Icons.Default.Visibility,
                        label = "Pessoas que eu sigo",
                        description = "Ver e remover quem você acompanha"
                    ) { navController.navigate("manage_following") }
                Divider(Modifier.padding(vertical = 4.dp))
                    SettingsNavItem(
                        icon = Icons.Default.VisibilityOff,
                        label = "Quem vê meus dados",
                        description = "Revogar acesso de acompanhantes"
                    ) { navController.navigate("manage_followers") }
                }

                Spacer(Modifier.height(12.dp))

                // ── Seção: Metas ──────────────────────────────────────────────
                val targetMinFlow by viewModel.targetMin.collectAsState()
                val targetMaxFlow by viewModel.targetMax.collectAsState()
                var tempMin by remember(targetMinFlow) { mutableStateOf(targetMinFlow.toInt().toString()) }
                var tempMax by remember(targetMaxFlow) { mutableStateOf(targetMaxFlow.toInt().toString()) }

                SettingsSection(title = "Metas de Glicemia", icon = Icons.Default.Flag) {
                    Text(
                        "Defina o intervalo alvo para seus relatórios.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            value = tempMin,
                            onValueChange = { 
                                if (it.all { char -> char.isDigit() }) {
                                    tempMin = it
                                    it.toFloatOrNull()?.let { min -> 
                                        viewModel.updateTargetRange(min, targetMaxFlow) 
                                    }
                                }
                            },
                            label = { Text("Mínimo") },
                            modifier = Modifier.weight(1f),
                            suffix = { Text("mg/dL") },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            )
                        )
                        OutlinedTextField(
                            value = tempMax,
                            onValueChange = { 
                                if (it.all { char -> char.isDigit() }) {
                                    tempMax = it
                                    it.toFloatOrNull()?.let { max -> 
                                        viewModel.updateTargetRange(targetMinFlow, max) 
                                    }
                                }
                            },
                            label = { Text("Máximo") },
                            modifier = Modifier.weight(1f),
                            suffix = { Text("mg/dL") },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            )
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                
                // ── Seção: Aparência ──────────────────────────────────────────
                val currentTheme by viewModel.appTheme.collectAsState()
                
                SettingsSection(title = "Aparência", icon = Icons.Default.Palette) {
                    val themeLabels = listOf("Sistema", "Claro", "Escuro")
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = themeLabels[currentTheme],
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = currentTheme.toFloat(),
                            onValueChange = { viewModel.updateAppTheme(it.toInt()) },
                            valueRange = 0f..2f,
                            steps = 1,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            themeLabels.forEachIndexed { index, label ->
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (currentTheme == index) MaterialTheme.colorScheme.primary else Color.Gray
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
            }

            // ── Seção: Nuvem e Dados ──────────────────────────────────────────
            SettingsSection(title = "Nuvem e Backup", icon = Icons.Default.CloudSync) {
                SettingsNavItem(
                    icon = Icons.Default.Sync,
                    label = "Sincronizar com a Nuvem",
                    description = if (isSyncing) "Sincronizando..." else "Enviar dados locais para o Firebase"
                ) {
                    if (!isSyncing) {
                        isSyncing = true
                        viewModel.syncLocalDataToCloud {
                            isSyncing = false
                            Toast.makeText(context, "Sincronização concluída!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                Divider(Modifier.padding(vertical = 4.dp))
                SettingsNavItem(
                    icon = Icons.Default.FileDownload,
                    label = "Exportar CSV",
                    description = "Compartilhar registros como planilha"
                ) { CsvExporter.exportBackup(context, records) }
                Divider(Modifier.padding(vertical = 4.dp))
                SettingsNavItem(
                    icon = Icons.Default.FileUpload,
                    label = "Importar CSV",
                    description = "Restaurar registros de um arquivo CSV"
                ) { importCsvLauncher.launch("text/*") }
            }

            Spacer(Modifier.height(12.dp))

            // ── Seção: Conta ──────────────────────────────────────────────────
            var showClearDataDialog by remember { mutableStateOf(false) }
            
            if (showClearDataDialog) {
                AlertDialog(
                    onDismissRequest = { showClearDataDialog = false },
                    icon = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
                    title = { Text("Limpar Meus Dados?") },
                    text = { Text("Isso removerá permanentemente todos os SEUS registros deste dispositivo E da nuvem. Os dados de pessoas que você segue não serão afetados.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.clearAllData()
                                showClearDataDialog = false
                                Toast.makeText(context, "Seus dados foram apagados!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) { Text("Apagar Meus Dados") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearDataDialog = false }) { Text("Cancelar") }
                    }
                )
            }

            SettingsSection(title = "Conta", icon = Icons.Default.AccountCircle) {
                SettingsNavItem(
                    icon = Icons.Default.DeleteSweep,
                    label = "Limpar Todos os Dados",
                    description = "Apagar registros do celular e da nuvem",
                    tint = MaterialTheme.colorScheme.error
                ) { showClearDataDialog = true }
                Divider(Modifier.padding(vertical = 4.dp))
                SettingsNavItem(
                    icon = Icons.Default.Logout,
                    label = "Sair da Conta",
                    description = "Desconectar e trocar de usuário",
                    tint = MaterialTheme.colorScheme.error
                ) { onLogout() }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(16.dp), content = content)
        }
    }
}

@Composable
fun SettingsNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    description: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = tint)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun ImportPreviewDialog(
    result: com.example.glicose.utils.CsvExporter.CsvParseResult,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.FileUpload, null) },
        title = { Text("Importar Registros") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {

                // Formato esperado
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Formato esperado do arquivo:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "valor_mgdl,nota,timestamp_ms,data_hora_legivel",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Exemplo: 120,Em jejum,1714320000000,28/04/2025 08:00",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "• Colunas obrigatórias: valor_mgdl e timestamp_ms\n• A coluna nota pode estar vazia\n• data_hora_legivel é ignorada",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Resultado da validação
                val hasValid = result.validRecords.isNotEmpty()
                val hasInvalid = result.invalidLines > 0

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (hasValid) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        null,
                        tint = if (hasValid) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${result.validRecords.size} registro(s) válido(s) encontrado(s)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (hasValid) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                    )
                }

                if (hasInvalid) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Warning,
                            null,
                            tint = Color(0xFFFFA000),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${result.invalidLines} linha(s) inválida(s) serão ignoradas",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFFA000)
                        )
                    }
                }

                if (!hasValid) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Nenhum registro válido encontrado. Verifique se o arquivo está no formato correto.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = result.validRecords.isNotEmpty()
            ) {
                Text("Importar ${result.validRecords.size} registro(s)")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
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
        var isRefreshing by remember { mutableStateOf(false) }
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                viewModel.refreshData { isRefreshing = false }
            }
        ) {
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
        var isRefreshing by remember { mutableStateOf(false) }
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                viewModel.refreshData { isRefreshing = false }
            }
        ) {
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
