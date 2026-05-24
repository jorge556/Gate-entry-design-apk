package com.example.data

import kotlinx.coroutines.flow.Flow

class PlateRepository(private val plateDao: PlateDao) {
    val allPlates: Flow<List<PlateRecord>> = plateDao.getAllPlates()

    fun getPlateByNumber(plateNumber: String): Flow<PlateRecord?> {
        return plateDao.getPlateByNumber(normalizePlate(plateNumber))
    }

    suspend fun getPlateDirect(plateNumber: String): PlateRecord? {
        return plateDao.getPlateByNumberDirect(normalizePlate(plateNumber))
    }

    suspend fun insert(plate: PlateRecord) {
        val normalized = plate.copy(plateNumber = normalizePlate(plate.plateNumber))
        plateDao.insertPlate(normalized)
    }

    suspend fun update(plate: PlateRecord) {
        val normalized = plate.copy(plateNumber = normalizePlate(plate.plateNumber))
        plateDao.insertPlate(normalized) // insert handles REPLACE, but using Dao insert plate fits
    }

    suspend fun delete(plate: PlateRecord) {
        plateDao.deletePlate(plate)
    }

    suspend fun deleteByNumber(plateNumber: String) {
        plateDao.deletePlateByNumber(normalizePlate(plateNumber))
    }

    suspend fun clearAll() {
        plateDao.deleteAllPlates()
    }

    private fun normalizePlate(plate: String): String {
        return plate.trim().uppercase().replace("\\s+".toRegex(), "")
    }

    suspend fun seedSampleData() {
        val samplePlates = listOf(
            PlateRecord("TX8892", "John McClane", "REGISTERED", "Active corporate employee. Allowed 24/7 access.", 0.0),
            PlateRecord("CA4456", "Sarah Connor", "FLAGGED", "VIP Visitor. Escort requested upon arrival.", 0.0),
            PlateRecord("NY2289", "Tony Stark", "REGISTERED", "Executive contractor. Access to all levels.", 0.0),
            PlateRecord("DEBT404", "Bob Vance", "BANNED_DEBT", "Outstanding entry and parking invoice fees since Dec 2025.", 185.00),
            PlateRecord("BAN666", "Marcus Brody", "BANNED_PREMISES", "Banned from premises due to security violations and repeated trespass.", 0.0),
            PlateRecord("FL7712", "Walter White", "FLAGGED", "Flagged for audit. Notify shipping manager immediately on arrival.", 0.0),
            PlateRecord("OWE990", "Arthur Dent", "BANNED_DEBT", "Unpaid damage fees for company vehicle.", 95.25)
        )
        for (plate in samplePlates) {
            insert(plate)
        }
    }
}
