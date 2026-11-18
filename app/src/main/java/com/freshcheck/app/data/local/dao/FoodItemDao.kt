package com.freshcheck.app.data.local.dao

import androidx.room.*
import com.freshcheck.app.data.local.entity.*
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface FoodItemDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: FoodItemEntity)
    @Update suspend fun update(item: FoodItemEntity): Int
    @Query("DELETE FROM food_items WHERE id = :id")
    suspend fun delete(id: String): Int
    @Query("SELECT * FROM food_items WHERE id = :id")
    suspend fun findById(id: String): FoodItemEntity?
    @Query("SELECT * FROM food_items WHERE status = 'Active' ORDER BY added DESC, id")
    fun observeActive(): Flow<List<FoodItemEntity>>
    @Query("SELECT * FROM food_items ORDER BY added DESC, id")
    fun observeAll(): Flow<List<FoodItemEntity>>
    @Transaction
    @Query("SELECT * FROM food_items WHERE status = 'Active' ORDER BY added DESC, id")
    fun observeActiveWithScans(): Flow<List<FoodWithScans>>
    @Transaction
    @Query("SELECT * FROM food_items WHERE id = :id")
    suspend fun findWithScans(id: String): FoodWithScans?
    @Query("UPDATE food_items SET status = :status, updatedAt = :at WHERE id = :id AND status = 'Active'")
    suspend fun finish(id: String, status: FoodStatus, at: Instant): Int
}
