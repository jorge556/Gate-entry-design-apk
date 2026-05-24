package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.PlateRecord
import com.example.data.PlateRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PlateViewModel(private val repository: PlateRepository) : ViewModel() {

    // Current plate search text (from keyboard or scanner)
    private val _searchedPlate = MutableStateFlow("")
    val searchedPlate: StateFlow<String> = _searchedPlate.asStateFlow()

    // Flag to determine if real-time plate checking found a match or not
    private val _scannedRecord = MutableStateFlow<PlateRecord?>(null)
    val scannedRecord: StateFlow<PlateRecord?> = _scannedRecord.asStateFlow()

    // Indicates if we are currently searching for a plate (useful for UI states)
    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    // List of all records (visible to Admin)
    val allRecords: StateFlow<List<PlateRecord>> = repository.allPlates
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Admin login privilege status
    private val _isAdminAuthorized = MutableStateFlow(false)
    val isAdminAuthorized: StateFlow<Boolean> = _isAdminAuthorized.asStateFlow()

    // Admin authorization error state
    private val _adminError = MutableStateFlow<String?>(null)
    val adminError: StateFlow<String?> = _adminError.asStateFlow()

    // Export/Import feedback alert message
    private val _actionFeedback = MutableStateFlow<String?>(null)
    val actionFeedback: StateFlow<String?> = _actionFeedback.asStateFlow()

    init {
        // Automatically precheck database. Seed with sample records if empty so the app has live, testable records.
        viewModelScope.launch {
            repository.allPlates.first().let { items ->
                if (items.isEmpty()) {
                    repository.seedSampleData()
                }
            }
        }
    }

    // Set plate search text and query DB instantly
    fun onPlateSearchChanged(plate: String) {
        val uppercaseClean = plate.trim().uppercase().replace("\\s+".toRegex(), "")
        _searchedPlate.value = plate // Keep user raw text for typing experience
        
        if (uppercaseClean.isEmpty()) {
            _scannedRecord.value = null
            _isSearching.value = false
            return
        }

        _isSearching.value = true
        viewModelScope.launch {
            val record = repository.getPlateDirect(uppercaseClean)
            _scannedRecord.value = record
            _isSearching.value = false
        }
    }

    // Trigger instant check (forces query refresh, e.g. after editing)
    fun refreshCurrentCheck() {
        onPlateSearchChanged(_searchedPlate.value)
    }

    // Authenticate administrator
    fun authenticateAdmin(passcode: String): Boolean {
        // Standard admin passcode is "admin123" for simple testing as requested
        return if (passcode == "admin123" || passcode.lowercase() == "admin") {
            _isAdminAuthorized.value = true
            _adminError.value = null
            true
        } else {
            _adminError.value = "Access Denied: Invalid administrator passcode."
            false
        }
    }

    fun logoutAdmin() {
        _isAdminAuthorized.value = false
    }

    // Clear feedback notifications
    fun dismissFeedback() {
        _actionFeedback.value = null
    }

    // Add or Edit a plate record
    fun saveOrUpdateRecord(
        plateNumber: String,
        driverName: String,
        status: String,
        notes: String,
        debtAmount: Double
    ) {
        viewModelScope.launch {
            val cleanPlate = plateNumber.trim().uppercase().replace("\\s+".toRegex(), "")
            if (cleanPlate.isEmpty()) return@launch

            val record = PlateRecord(
                plateNumber = cleanPlate,
                driverName = driverName,
                status = status,
                notes = notes,
                debtAmount = if (status == "BANNED_DEBT") debtAmount else 0.0,
                lastSeenTimestamp = System.currentTimeMillis()
            )
            repository.insert(record)
            _actionFeedback.value = "Saved record of plate $cleanPlate successfully."
            refreshCurrentCheck() // Refresh verification card if searching this plate
        }
    }

    // Quick trigger to add a newly checked plate to db from search screen
    fun registerNewPlate(plateNumber: String, driverName: String) {
        saveOrUpdateRecord(plateNumber, driverName, "REGISTERED", "Auto-registered from scanner.", 0.0)
    }

    // Delete a plate record
    fun deleteRecordByNumber(plateNumber: String) {
        viewModelScope.launch {
            repository.deleteByNumber(plateNumber)
            _actionFeedback.value = "Deleted plate $plateNumber."
            refreshCurrentCheck() // Update verification state
        }
    }

    // Reset database to initial sample records for simple testing
    fun resetDatabaseToSamples() {
        viewModelScope.launch {
            repository.clearAll()
            repository.seedSampleData()
            _actionFeedback.value = "Database successfully reset to initial plate records."
            refreshCurrentCheck()
        }
    }

    // Export Plate DB as a Excel/CSV document file and trigger standard Android share panel
    fun exportToCsv(context: Context) {
        viewModelScope.launch {
            try {
                val records = allRecords.value
                val csvHeader = "Plate Number,Driver Name,Status,Notes,Outstanding Debt,Last Seen\n"
                val csvContent = records.joinToString("\n") { r ->
                    // Escape commas in notes or names to produce valid RFC 4180 style CSV
                    val safeName = if (r.driverName.contains(",")) "\"${r.driverName}\"" else r.driverName
                    val safeNotes = if (r.notes.contains(",")) "\"${r.notes}\"" else r.notes
                    val formattedDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(r.lastSeenTimestamp))
                    "${r.plateNumber},$safeName,${r.status},$safeNotes,${r.debtAmount},$formattedDate"
                }

                val fullCsv = csvHeader + csvContent

                // Save in the app public downloads directory. No permission needed on Android 10+.
                val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                val csvFile = File(downloadsDir, "PlateSentinel_Backup_${System.currentTimeMillis()}.csv")
                csvFile.writeText(fullCsv)

                // Copy to a simpler clip or save locally
                // Prepare Sharing Intent so they can open directly in Excel or Google Sheets
                val fileUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    csvFile
                )

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    putExtra(Intent.EXTRA_SUBJECT, "Plate Sentinel Excel Export")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooserIntent = Intent.createChooser(intent, "Open or Export Excel/CSV Records").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooserIntent)

                _actionFeedback.value = "Exported ${records.size} plates as CSV. Saved to Downloads folder."
            } catch (e: Exception) {
                _actionFeedback.value = "Failed to export CSV: ${e.localizedMessage}"
            }
        }
    }

    // Parse and import custom CSV file contents as plates
    fun importFromCsvText(csvText: String) {
        viewModelScope.launch {
            try {
                var importedCount = 0
                val lines = csvText.lines()
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) continue
                    
                    // Skip header lines
                    if (trimmed.startsWith("Plate Number") || trimmed.startsWith("PlateNumber") || trimmed.startsWith("PLATE")) {
                        continue
                    }

                    // Basic comma parsing
                    // Supporting standard split or simple quotes splitting
                    val parts = splitCsvLine(trimmed)
                    if (parts.size >= 3) {
                        val plateNumber = parts[0].trim().uppercase().replace("\\s+".toRegex(), "")
                        if (plateNumber.isEmpty()) continue

                        val driverName = parts[1].trim()
                        val statusInput = parts[2].trim().uppercase()
                        
                        // Map status safely
                        val status = when {
                            statusInput.contains("BANNED_DEBT") || statusInput.contains("DEBT") || statusInput.contains("OWE") -> "BANNED_DEBT"
                            statusInput.contains("BANNED_PREMISES") || statusInput.contains("PREMISES") || statusInput.contains("BANNED") -> "BANNED_PREMISES"
                            statusInput.contains("FLAGGED") -> "FLAGGED"
                            else -> "REGISTERED"
                        }

                        val notes = parts.getOrNull(3)?.trim() ?: ""
                        val debt = parts.getOrNull(4)?.trim()?.toDoubleOrNull() ?: 0.0

                        val record = PlateRecord(
                            plateNumber = plateNumber,
                            driverName = driverName,
                            status = status,
                            notes = notes,
                            debtAmount = debt,
                            lastSeenTimestamp = System.currentTimeMillis()
                        )
                        repository.insert(record)
                        importedCount++
                    }
                }
                _actionFeedback.value = "Successfully imported $importedCount records from CSV."
                refreshCurrentCheck()
            } catch (e: Exception) {
                _actionFeedback.value = "Failed to import CSV: ${e.localizedMessage}"
            }
        }
    }

    // Helper to split CSV lines, supporting simple double quote groupings
    private fun splitCsvLine(line: String): List<String> {
        val tokens = mutableListOf<String>()
        var sb = StringBuilder()
        var insideQuotes = false
        for (char in line) {
            if (char == '\"') {
                insideQuotes = !insideQuotes
            } else if (char == ',' && !insideQuotes) {
                tokens.add(sb.toString())
                sb = StringBuilder()
            } else {
                sb.append(char)
            }
        }
        tokens.add(sb.toString())
        return tokens
    }
}

// ViewModelFactory definition to feed Repository into VM cleanly
class PlateViewModelFactory(private val repository: PlateRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PlateViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PlateViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
