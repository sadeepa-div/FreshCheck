package com.freshcheck.app.data.local.dao

import androidx.room.*
import com.freshcheck.app.data.local.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface ReminderDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(reminder: ReminderEntity)
    @Update suspend fun update(reminder: ReminderEntity): Int
    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: String): Int
    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun findById(id: String): ReminderEntity?
    @Query("SELECT * FROM reminders WHERE foodId = :foodId ORDER BY dueAt, id")
    fun observeForFood(foodId: String): Flow<List<ReminderEntity>>
    @Query("""SELECT * FROM reminders WHERE enabled = 1 AND state = 'DefinitionOnly'
        AND dueAt <= :at AND foodId IN (SELECT id FROM food_items WHERE status = 'Active')
        ORDER BY dueAt, id""")
    suspend fun findDue(at: Instant): List<ReminderEntity>
    @Query("UPDATE reminders SET enabled = 0, state = 'Disabled', updatedAt = :at WHERE foodId = :foodId")
    suspend fun disableForFood(foodId: String, at: Instant)
}
