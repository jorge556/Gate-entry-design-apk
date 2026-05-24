package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlateDao {
    @Query("SELECT * FROM plate_records ORDER BY lastSeenTimestamp DESC")
    fun getAllPlates(): Flow<List<PlateRecord>>

    @Query("SELECT * FROM plate_records WHERE plateNumber = :plateNumber LIMIT 1")
    fun getPlateByNumber(plateNumber: String): Flow<PlateRecord?>

    @Query("SELECT * FROM plate_records WHERE plateNumber = :plateNumber LIMIT 1")
    suspend fun getPlateByNumberDirect(plateNumber: String): PlateRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlate(plate: PlateRecord)

    @Update
    suspend fun updatePlate(plate: PlateRecord)

    @Delete
    suspend fun deletePlate(plate: PlateRecord)

    @Query("DELETE FROM plate_records WHERE plateNumber = :plateNumber")
    suspend fun deletePlateByNumber(plateNumber: String)

    @Query("DELETE FROM plate_records")
    suspend fun deleteAllPlates()
}
