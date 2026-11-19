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
    @Query("""SELECT * FROM reminders WHERE enabled = 1 AND state IN ('DefinitionOnly','Enqueued','Blocked')
        AND dueAt <= :at AND foodId IN (SELECT id FROM food_items WHERE status = 'Active')
        ORDER BY dueAt, id""")
    suspend fun findDue(at: Instant): List<ReminderEntity>
    @Query("UPDATE reminders SET enabled = 0, state = 'Disabled', updatedAt = CASE WHEN :at <= updatedAt THEN updatedAt + 1 ELSE :at END WHERE foodId = :foodId")
    suspend fun disableForFood(foodId: String, at: Instant)

    @Query("SELECT * FROM reminders ORDER BY dueAt, id")
    suspend fun all(): List<ReminderEntity>
    @Query("SELECT * FROM reminders ORDER BY dueAt, id")
    fun observeAll(): Flow<List<ReminderEntity>>
    @Query("SELECT * FROM reminders WHERE foodId = :foodId ORDER BY dueAt, id")
    suspend fun forFood(foodId: String): List<ReminderEntity>
    @Query("""UPDATE reminders SET state = :state, enabled = :enabled
        WHERE id = :id AND dueAt = :dueAt AND updatedAt = :revision
        AND state IN ('DefinitionOnly','Enqueued','Blocked') AND enabled = 1""")
    suspend fun transitionPending(id: String, dueAt: Instant, revision: Instant,
        state: com.freshcheck.app.data.local.entity.ReminderState, enabled: Boolean): Int
}
