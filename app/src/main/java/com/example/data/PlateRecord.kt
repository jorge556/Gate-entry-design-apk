package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "plate_records")
data class PlateRecord(
    @PrimaryKey val plateNumber: String, // Normalized plate number (uppercase, no whitespace)
    val driverName: String,
    val status: String, // "REGISTERED", "FLAGGED", "BANNED_DEBT", "BANNED_PREMISES"
    val notes: String = "",
    val debtAmount: Double = 0.0,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
) {
    val isBanned: Boolean
        get() = status == "BANNED_DEBT" || status == "BANNED_PREMISES"
}
