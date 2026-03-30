package com.ubcsc.checkout.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.ubcsc.checkout.data.db.entities.CheckoutSessionEntity

@Dao
interface CheckoutSessionDao {

    @Insert
    suspend fun insert(session: CheckoutSessionEntity): Long

    @Update
    suspend fun update(session: CheckoutSessionEntity)

    @Query("SELECT * FROM checkout_sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): CheckoutSessionEntity?

    @Query("SELECT * FROM checkout_sessions WHERE member_id = :memberId AND status = 'active' LIMIT 1")
    suspend fun getActiveByMember(memberId: Int): CheckoutSessionEntity?

    @Query("SELECT * FROM checkout_sessions WHERE craft_id = :craftId AND status = 'active' LIMIT 1")
    suspend fun getActiveByCraft(craftId: Int): CheckoutSessionEntity?

    @Query("SELECT * FROM checkout_sessions WHERE status = 'active' ORDER BY checkout_time DESC")
    suspend fun getAllActive(): List<CheckoutSessionEntity>

    @Query("""
        SELECT * FROM checkout_sessions
        ORDER BY checkout_time DESC
        LIMIT :limit
    """)
    suspend fun getRecent(limit: Int = 50): List<CheckoutSessionEntity>

    @Query("UPDATE checkout_sessions SET status = 'completed', checkin_time = :checkinTime, notes_in = :notes, damage_reported = :damage WHERE id = :id")
    suspend fun complete(id: Int, checkinTime: Long, notes: String?, damage: Boolean)
}
