package com.freshcheck.app.data.local.dao

import androidx.room.*
import com.freshcheck.app.data.local.entity.ScanRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanRecordDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(scan: ScanRecordEntity)
    @Query("SELECT * FROM scan_records ORDER BY scannedAt DESC, id")
    fun observeAll(): Flow<List<ScanRecordEntity>>
    @Query("SELECT * FROM scan_records WHERE foodId = :foodId ORDER BY scannedAt DESC, id")
    fun observeForFood(foodId: String): Flow<List<ScanRecordEntity>>
    @Query("SELECT * FROM scan_records WHERE id = :id")
    suspend fun findById(id: String): ScanRecordEntity?
    @Query("DELETE FROM scan_records WHERE id = :id")
    suspend fun delete(id: String): Int
    @Query("DELETE FROM scan_records")
    suspend fun clear(): Int
}
