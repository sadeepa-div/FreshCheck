package com.freshcheck.app.data.local.dao

import androidx.room.*
import com.freshcheck.app.data.local.entity.FoodOutcomeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodOutcomeDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(outcome: FoodOutcomeEntity)
    @Query("SELECT * FROM food_outcomes WHERE outcome = 'Used' ORDER BY occurredAt DESC, id")
    fun observeUsed(): Flow<List<FoodOutcomeEntity>>
    @Query("SELECT * FROM food_outcomes WHERE outcome = 'Discarded' ORDER BY occurredAt DESC, id")
    fun observeDiscarded(): Flow<List<FoodOutcomeEntity>>
    @Query("SELECT * FROM food_outcomes ORDER BY occurredAt DESC, id")
    fun observeAll(): Flow<List<FoodOutcomeEntity>>
}
