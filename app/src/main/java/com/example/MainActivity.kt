package com.example

import android.Manifest
import android.os.Bundle
import android.widget.Space
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.flow.StateFlow
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.PlateDatabase
import com.example.data.PlateRecord
import com.example.data.PlateRepository
import com.example.ui.CameraOcrScanner
import com.example.ui.PlateViewModel
import com.example.ui.PlateViewModelFactory
import com.example.ui.theme.MyApplicationTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Init Room Database and Repository
        val database = PlateDatabase.getDatabase(applicationContext)
        val repository = PlateRepository(database.plateDao())
        
        // Initialize MVVM ViewModel using factory
        val viewModel: PlateViewModel = ViewModelProvider(
            this,
            PlateViewModelFactory(repository)
        )[PlateViewModel::class.java]

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    PlateSentinelApp(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlateSentinelApp(
    viewModel: PlateViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Core VM States
    val searchedPlate by viewModel.searchedPlate.collectAsStateWithLifecycle()
    val scannedRecord by viewModel.scannedRecord.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val allRecords by viewModel.allRecords.collectAsStateWithLifecycle()
    val isAdminAuthorized by viewModel.isAdminAuthorized.collectAsStateWithLifecycle()
    val adminError by viewModel.adminError.collectAsStateWithLifecycle()
    val actionFeedback by viewModel.actionFeedback.collectAsStateWithLifecycle()

    // UI Navigation/Control States
    var showCameraScanner by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf("checker") } // "checker" or "admin"
    var showImportDialog by remember { mutableStateOf(false) }
    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingRecord by remember { mutableStateOf<PlateRecord?>(null) }
    
    // Admin password passcode login state
    var passcodeText by remember { mutableStateOf("") }
    var isPasscodeVisible by remember { mutableStateOf(false) }

    // Camera permission handler (Accompanist)
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    // Layout values
    val gradientBackground = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    )

    // Camera OCR Overlay launcher
    if (showCameraScanner) {
        CameraOcrScanner(
            onPlateScanned = { plate ->
                viewModel.onPlateSearchChanged(plate)
                showCameraScanner = false
            },
            onClose = {
                showCameraScanner = false
            }
        )
        return // Take up full viewport
    }

    // Main App view
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(gradientBackground)
    ) {
        // Premium, slim top header bar styled exactly like the Professional Polish HTML
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(64.dp)
                .background(MaterialTheme.colorScheme.background)
                .drawBehind {
                    drawLine(
                        color = Color(0xFFE2E8F0), // Slate-200 equivalent
                        start = androidx.compose.ui.geometry.Offset(0f, size.height),
                        end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = "App Logo",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Gate Sentry OCR",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp, // tracking-tight
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Plate Sentinel Network",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }

            // Right header status / active panel indicators
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isAdminAuthorized) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            activeTab = "admin"
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isAdminAuthorized) Icons.Default.LockOpen else Icons.Default.Lock,
                            contentDescription = "Admin Status",
                            tint = if (isAdminAuthorized) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isAdminAuthorized) "Admin Mode" else "Staff",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isAdminAuthorized) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Active View Panels
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            if (activeTab == "checker") {
                // VERIFICATION / LOOKUP SCREEN
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Headline
                    Text(
                        text = "Gate Sentry Gate-Verification",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Plate Input Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = searchedPlate,
                                    onValueChange = { viewModel.onPlateSearchChanged(it) },
                                    label = { Text("License Plate Number") },
                                    placeholder = { Text("e.g. TX8892") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.DirectionsCar,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    trailingIcon = {
                                        if (searchedPlate.isNotEmpty()) {
                                            IconButton(onClick = { viewModel.onPlateSearchChanged("") }) {
                                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("plate_input_text")
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                // Camera Launch button with elegant style
                                Button(
                                    onClick = {
                                        if (cameraPermissionState.status.isGranted) {
                                            showCameraScanner = true
                                        } else {
                                            cameraPermissionState.launchPermissionRequest()
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier
                                        .height(56.dp)
                                        .testTag("camera_scan_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "Scan with camera"
                                    )
                                }
                            }

                            if (!cameraPermissionState.status.isGranted) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { cameraPermissionState.launchPermissionRequest() }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Camera permission needed for live OCR. Grant here.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Verification Results Dashboard Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        AnimatedContent(
                            targetState = searchedPlate.trim().isEmpty(),
                            transitionSpec = {
                                fadeIn() togetherWith fadeOut()
                            },
                            label = "verification_transition"
                        ) { isQueryEmpty ->
                            if (isQueryEmpty) {
                                // Empty state guide
                                EmptyLicensePlateGuide()
                            } else {
                                // Full plate verification details
                                PlateCardVerificationResult(
                                    searchedPlate = searchedPlate,
                                    record = scannedRecord,
                                    onRegisterShortcut = { name ->
                                        viewModel.registerNewPlate(searchedPlate, name)
                                    },
                                    isAdmin = isAdminAuthorized
                                )
                            }
                        }
                    }
                }
            } else {
                // ADMINISTRATOR BACKEND PRIVILEGED VIEW
                if (!isAdminAuthorized) {
                    // Password gate
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Administrator Log In Required",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Please verify your authorized security passcode to read all database records, edit settings, and access Excel file backups.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(0.9f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                OutlinedTextField(
                                    value = passcodeText,
                                    onValueChange = { passcodeText = it },
                                    label = { Text("Secured Passcode") },
                                    placeholder = { Text("Enter admin password") },
                                    visualTransformation = if (isPasscodeVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    leadingIcon = {
                                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    },
                                    trailingIcon = {
                                        IconButton(onClick = { isPasscodeVisible = !isPasscodeVisible }) {
                                            Icon(
                                                imageVector = if (isPasscodeVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = "Toggle password view",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("admin_password_input")
                                )

                                if (adminError != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = adminError ?: "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = {
                                        val success = viewModel.authenticateAdmin(passcodeText)
                                        if (success) {
                                            passcodeText = "" // reset
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("admin_login_submit"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Authorize Settings", fontWeight = FontWeight.Bold)
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Demo login passcode: 'admin123' or 'admin'",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.3.sp
                                )
                            }
                        }
                    }
                } else {
                    // Authenticated Backend Administrator Interface
                    AdminAuthenticatedDashboard(
                        allRecords = allRecords,
                        onExportCsv = { viewModel.exportToCsv(context) },
                        onImportCsvIntent = { showImportDialog = true },
                        onResetDb = { viewModel.resetDatabaseToSamples() },
                        onEditRecord = { record ->
                            editingRecord = record
                            showAddEditDialog = true
                        },
                        onDeleteRecord = { plateNum ->
                            viewModel.deleteRecordByNumber(plateNum)
                        },
                        onAddRecordClick = {
                            editingRecord = null
                            showAddEditDialog = true
                        },
                        onLogout = { viewModel.logoutAdmin() }
                    )
                }
            }
        }

        // Custom elegant bottom navigation bar styled exactly like the HTML!
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding() // avoid overlapping system navigation gesture bar
                .height(72.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .drawBehind {
                    // Draw a thin top border line like HTML border-t border-[#CAC4D0]/30
                    drawLine(
                        color = Color(0xFFCAC4D0).copy(alpha = 0.3f),
                        start = androidx.compose.ui.geometry.Offset(0f, 0f),
                        end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // First nav button: Verify Scanner
            val isCheckerActive = activeTab == "checker"
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { activeTab = "checker" }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isCheckerActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    modifier = Modifier.height(32.dp).width(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Scan",
                            tint = if (isCheckerActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Verify Scan",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isCheckerActive) FontWeight.Bold else FontWeight.Medium,
                    color = if (isCheckerActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }

            // Second nav button: System Admin
            val isAdminActive = activeTab == "admin"
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { activeTab = "admin" }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isAdminActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    modifier = Modifier.height(32.dp).width(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Admin",
                            tint = if (isAdminActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "System Admin",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isAdminActive) FontWeight.Bold else FontWeight.Medium,
                    color = if (isAdminActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }

    // Modal Import pasting dialog
    if (showImportDialog) {
        CsvImportModal(
            onImport = { csvText ->
                viewModel.importFromCsvText(csvText)
                showImportDialog = false
            },
            onClose = { showImportDialog = false }
        )
    }

    // Modal Add / Edit Plate dialog
    if (showAddEditDialog) {
        AddEditPlateModal(
            record = editingRecord,
            onSave = { plate, name, status, notes, debt ->
                viewModel.saveOrUpdateRecord(plate, name, status, notes, debt)
                showAddEditDialog = false
            },
            onClose = { showAddEditDialog = false }
        )
    }

    // Quick Toast-like Action Feedback Banner (Action Feedback)
    if (actionFeedback != null) {
        Dialog(onDismissRequest = { viewModel.dismissFeedback() }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.inverseOnSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "System Action",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.inverseSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = actionFeedback ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.dismissFeedback() },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Acknowledge", fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyLicensePlateGuide() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            modifier = Modifier.size(96.dp)
        ) {
            Icon(
                imageVector = Icons.Default.QrCodeScanner,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Awaiting Verification Input",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Directly type any plate alphanumeric sequence above, or use the camera OCR scanner to scan code from a physical car bumper instantly.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Displays visual tags info
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatusQuickLegend(color = Color(0xFF2E7D32), label = "Registered")
            StatusQuickLegend(color = Color(0xFFEF6C00), label = "Flagged")
            StatusQuickLegend(color = Color(0xFFC62828), label = "Banned/Debt")
            StatusQuickLegend(color = Color(0xFF880E4F), label = "Banned/Premises")
        }
    }
}

@Composable
fun StatusQuickLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, shape = CircleShape)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
fun PlateCardVerificationResult(
    searchedPlate: String,
    record: PlateRecord?,
    onRegisterShortcut: (String) -> Unit,
    isAdmin: Boolean
) {
    if (record == null) {
        // UNREGISTERED PLATE STATE (Styled with Professional Polish)
        var newDriverName by remember { mutableStateOf("") }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant // Matches #F3EDF7 surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)), // subtle #CAC4D0 outline
            shape = RoundedCornerShape(28.dp) // rounded-3xl standard
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                // Header Row with Access Tag
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "DETECTED CODE",
                            style = MaterialTheme.typography.labelSmall,
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary // #6750A4
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = searchedPlate.uppercase(),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    
                    // Badge Pill Alert
                    Surface(
                        color = Color(0xFFFFECEB),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, Color(0xFFC62828).copy(alpha = 0.2f)),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color(0xFFC62828), CircleShape)
                            )
                            Text(
                                text = "UNREGISTERED",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC62828)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Information banner with modern glassmorphism matching the HTML style
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Access Clearance Warning",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "This vehicle code is not indexed in the secured security database. Gate clearance is restricted. Driver must represent valid manual paperwork.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Quick Approve Box for staff action
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AddModerator,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Quick-Register Guest Access",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = newDriverName,
                            onValueChange = { newDriverName = it },
                            label = { Text("Visitor / Driver Full Name") },
                            placeholder = { Text("e.g. Courier Guest") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("quick_reg_name")
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (newDriverName.trim().isNotEmpty()) {
                                    onRegisterShortcut(newDriverName)
                                }
                            },
                            enabled = newDriverName.trim().isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("quick_reg_submit"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Fast-Approve & Save Driver", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    } else {
        // PLATE DEFINED IN SECURE DATABASE (Polished Design style)
        val isDebtBanned = record.status == "BANNED_DEBT"
        val isPremisesBanned = record.status == "BANNED_PREMISES"
        val isFlagged = record.status == "FLAGGED"
        val isRegistered = record.status == "REGISTERED"

        val (themeSettings, actionMessage) = when {
            isRegistered -> Triple(
                Color(0xFF2E7D32),
                "ACCESS GRANTED",
                Icons.Default.CheckCircle
            ) to "All credentials are clear. Driver enjoys authorized clearance onto the premises."
            
            isFlagged -> Triple(
                Color(0xFFEF6C00),
                "FLAGGED ENTRY",
                Icons.Default.Warning
            ) to "Perform driver ID lookup before authorization. Inspect notes listed in log."
            
            isDebtBanned -> Triple(
                Color(0xFFB3261E),
                "ACCESS BANNED",
                Icons.Default.Dangerous
            ) to "ENTRY DENIED: Outstanding debts detected on vehicle account. Advise driver of financial obligations."
            
            isPremisesBanned -> Triple(
                Color(0xFFB3261E),
                "SITE PROHIBITED",
                Icons.Default.Dangerous
            ) to "ENTRY FORBIDDEN: Driver represents a banned person. Deny entry and contact security command immediately."
            
            else -> Triple(Color.Gray, "INSPECT RECORD", Icons.Default.Info) to "Contact management head."
        }
        val (bannerColor, badgeText, statusIcon) = themeSettings

        // Render card matching the HTML template's exact card styling block
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant // #F3EDF7
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                // Header Area with Tag Badges matches HTML live scan card
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "DETECTED PLATE",
                            style = MaterialTheme.typography.labelSmall,
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary // #6750A4
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = record.plateNumber.uppercase(),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    // Elegant Badge Pill (like bg-[#F9DEDC] border-[#B3261E]/20 text-[#B3261E])
                    val containerBg = when {
                        isRegistered -> Color(0xFFE8F5E9)
                        isFlagged -> Color(0xFFFFF3E0)
                        else -> Color(0xFFF9DEDC) // pinkish red M3 error container
                    }
                    val badgeTextColor = when {
                        isRegistered -> Color(0xFF2E7D32)
                        isFlagged -> Color(0xFFEF6C00)
                        else -> Color(0xFFB3261E)
                    }

                    Surface(
                        color = containerBg,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, badgeTextColor.copy(alpha = 0.2f)),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(badgeTextColor, CircleShape)
                            )
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = badgeTextColor
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Grid detail cells styled in Glassmorphic white overlay boxes matching Tailwind bg-white/60 p-3 rounded-2xl
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Cell 1: Driver status Name
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = "DRIVER / OWNER",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = record.driverName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Cell 2: Financial Debt/Status
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = if (isDebtBanned) "FINANCIAL DEBT" else "SENTRY STATUS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isDebtBanned) Color(0xFFB3261E) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isDebtBanned) String.format("$%.2f Owed", record.debtAmount) else record.status.replace("_", " ").lowercase(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isDebtBanned) Color(0xFFB3261E) else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Cell 3 (Full row width): System memo notes
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "SECURITY LOG MEMORANDUM",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (record.notes.trim().isEmpty()) "No security notices are loaded into database archives." else record.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Solid guidance instructions card matching the bottom of Sentry OCR in HTML
                Surface(
                    color = bannerColor.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, bannerColor.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = "Guidance Icon",
                            tint = bannerColor,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "SENTRY SECURITY DIRECTIVE",
                                style = MaterialTheme.typography.labelSmall,
                                letterSpacing = 1.sp,
                                fontWeight = FontWeight.Black,
                                color = bannerColor
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = actionMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Int.cpp(): androidx.compose.ui.unit.Dp? = this.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAuthenticatedDashboard(
    allRecords: List<PlateRecord>,
    onExportCsv: () -> Unit,
    onImportCsvIntent: () -> Unit,
    onResetDb: () -> Unit,
    onEditRecord: (PlateRecord) -> Unit,
    onDeleteRecord: (String) -> Unit,
    onAddRecordClick: () -> Unit,
    onLogout: () -> Unit
) {
    val recordsList = allRecords
    var recordQueryText by remember { mutableStateOf("") }
    
    // Compute quick dashboard counts
    val totalPlates = recordsList.size
    val totalBanned = recordsList.count { it.status == "BANNED_DEBT" || it.status == "BANNED_PREMISES" }
    val totalFlagged = recordsList.count { it.status == "FLAGGED" }
    val totalDebtAmount = recordsList.sumOf { it.debtAmount }

    // Filter current list based on search bar text
    val filteredList = recordsList.filter {
        it.plateNumber.contains(recordQueryText, ignoreCase = true) ||
        it.driverName.contains(recordQueryText, ignoreCase = true)
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Dashboard title row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Plate Sentinel Back End",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Authorized Administrator Control",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            IconButton(
                onClick = onLogout,
                colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Icon(
                    imageVector = Icons.Default.ExitToApp,
                    contentDescription = "Logout Admin",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Grid of Quick Counters
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AdminDashboardCountCard(
                label = "Total Index",
                value = "$totalPlates",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            AdminDashboardCountCard(
                label = "Banned Files",
                value = "$totalBanned",
                color = Color(0xFFC62828),
                modifier = Modifier.weight(1f)
            )
            AdminDashboardCountCard(
                label = "Total Debt Due",
                value = String.format("$%.2f", totalDebtAmount),
                color = Color(0xFFEF6C00),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Advanced Back End Export/Import excel backup buttons
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Excel-Readable CSV Backups & Records Transfer",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onExportCsv,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("admin_export_csv_btn")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export Excel", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onImportCsvIntent,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("admin_import_csv_btn")
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Import CSV", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onResetDb,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("admin_reset_data_btn")
                    ) {
                        Text("Reset Seeder", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Records List Actions Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = recordQueryText,
                onValueChange = { recordQueryText = it },
                placeholder = { Text("Filter by Plate...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("admin_search_bar")
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onAddRecordClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .height(52.dp)
                    .testTag("admin_add_new_btn")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Record")
                Spacer(modifier = Modifier.width(4.dp))
                Text("New", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // List Scroll View
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = filteredList,
                key = { it.plateNumber }
            ) { item ->
                PlateRecordRowItem(
                    record = item,
                    onEdit = { onEditRecord(item) },
                    onDelete = { onDeleteRecord(item.plateNumber) }
                )
            }

            if (filteredList.isEmpty()) {
                item {
                    Text(
                        text = "No records found matching filters.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AdminDashboardCountCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
        }
    }
}

@Composable
fun PlateRecordRowItem(
    record: PlateRecord,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val statusColor = when (record.status) {
        "REGISTERED" -> Color(0xFF2E7D32)
        "FLAGGED" -> Color(0xFFEF6C00)
        "BANNED_DEBT" -> Color(0xFFB3261E)
        "BANNED_PREMISES" -> Color(0xFFB3261E)
        else -> Color.Gray
    }
    
    val badgeBg = when (record.status) {
        "REGISTERED" -> Color(0xFFE8F5E9)
        "FLAGGED" -> Color(0xFFFFF3E0)
        else -> Color(0xFFF9DEDC)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = record.plateNumber.uppercase(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    // Small status chip
                    Surface(
                        color = badgeBg,
                        shape = CircleShape,
                        border = BorderStroke(0.5.dp, statusColor.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(statusColor, CircleShape)
                            )
                            Text(
                                text = record.status.replace("_", " ").lowercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = record.driverName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
                
                if (record.notes.trim().isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = record.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }
                
                if (record.status == "BANNED_DEBT") {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = String.format("Outstanding: $%.2f", record.debtAmount),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFB3261E),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Right interactions edit/delete
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Plate Record",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Plate Record",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// Dialog window to import CSV by pasting raw texts
@Composable
fun CsvImportModal(
    onImport: (String) -> Unit,
    onClose: () -> Unit
) {
    var rawCsvText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = {
            Button(
                onClick = {
                    if (rawCsvText.trim().isNotEmpty()) {
                        onImport(rawCsvText)
                    }
                },
                modifier = Modifier.testTag("csv_paste_submit")
            ) {
                Text("Execute Import", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) {
                Text("Cancel")
            }
        },
        title = {
            Text("Import Excel Records (CSV Format)", fontWeight = FontWeight.Black)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Format template column order:\n`Plate Number, Driver Name, Status, Notes, Debt`",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = rawCsvText,
                    onValueChange = { rawCsvText = it },
                    placeholder = {
                        Text(
                            "e.g.\nTX8892,John McClane,REGISTERED,Active worker,0.0\n"+
                            "DEBT404,Bob Vance,BANNED_DEBT,Overdue fine,185.0\n"+
                            "BAN666,Marcus Brody,BANNED_PREMISES,Trespass,0.0"
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .testTag("csv_pasted_data_text"),
                    singleLine = false
                )
            }
        }
    )
}

// Dialog window to Add or Edit license record values
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditPlateModal(
    record: PlateRecord?,
    onSave: (String, String, String, String, Double) -> Unit,
    onClose: () -> Unit
) {
    var plateNumber by remember { mutableStateOf(record?.plateNumber ?: "") }
    var driverName by remember { mutableStateOf(record?.driverName ?: "") }
    var statusSelection by remember { mutableStateOf(record?.status ?: "REGISTERED") }
    var notesText by remember { mutableStateOf(record?.notes ?: "") }
    var debtAmountVal by remember { mutableStateOf(if (record?.status == "BANNED_DEBT") record.debtAmount.toString() else "0") }

    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = {
            Button(
                onClick = {
                    if (plateNumber.trim().isNotEmpty() && driverName.trim().isNotEmpty()) {
                        onSave(
                            plateNumber,
                            driverName,
                            statusSelection,
                            notesText,
                            debtAmountVal.trim().toDoubleOrNull() ?: 0.0
                        )
                    }
                },
                modifier = Modifier.testTag("admin_save_form_submit")
            ) {
                Text("Save Record", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) {
                Text("Go Back")
            }
        },
        title = {
            Text(if (record == null) "Add Secure Plate Record" else "Modify Plate Record", fontWeight = FontWeight.Black)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = plateNumber,
                    onValueChange = { plateNumber = it.uppercase() },
                    label = { Text("Plate Number (Uppercase)") },
                    enabled = record == null, // Lock plate number if editing
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("form_plate_input")
                )

                OutlinedTextField(
                    value = driverName,
                    onValueChange = { driverName = it },
                    label = { Text("Driver / Owner Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("form_driver_input")
                )

                // Select Status
                Text("Plate Status Category", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    StatusChipOption(name = "REG", selected = statusSelection == "REGISTERED", color = Color(0xFF2E7D32), onClick = { statusSelection = "REGISTERED" })
                    StatusChipOption(name = "FLAG", selected = statusSelection == "FLAGGED", color = Color(0xFFEF6C00), onClick = { statusSelection = "FLAGGED" })
                    StatusChipOption(name = "DEBT", selected = statusSelection == "BANNED_DEBT", color = Color(0xFFC62828), onClick = { statusSelection = "BANNED_DEBT" })
                    StatusChipOption(name = "BAN", selected = statusSelection == "BANNED_PREMISES", color = Color(0xFF880E4F), onClick = { statusSelection = "BANNED_PREMISES" })
                }

                if (statusSelection == "BANNED_DEBT") {
                    OutlinedTextField(
                        value = debtAmountVal,
                        onValueChange = { debtAmountVal = it },
                        label = { Text("Outstanding Unpaid Debt ($)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("form_debt_input")
                    )
                }

                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Internal Security Notes Summary") },
                    singleLine = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .testTag("form_notes_input")
                )
            }
        }
    )
}

@Composable
fun StatusChipOption(
    name: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clickable(onClick = onClick)
            .border(
                width = 1.dp,
                color = if (selected) color else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp)
            )
            .clip(RoundedCornerShape(8.dp)),
        color = if (selected) color.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.ExtraBold,
            color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
