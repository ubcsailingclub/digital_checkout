package com.ubcsc.checkout.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ubcsc.checkout.data.db.entities.MemberCardEntity
import com.ubcsc.checkout.data.db.entities.MemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemberDao {

    @Query("SELECT * FROM members WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): MemberEntity?

    @Query("""
        SELECT m.* FROM members m
        INNER JOIN member_cards c ON c.member_id = m.id
        WHERE c.card_uid_normalized = :uid AND c.is_active = 1
        LIMIT 1
    """)
    suspend fun getByCardUid(uid: String): MemberEntity?

    @Query("SELECT * FROM members WHERE is_active = 1 ORDER BY last_name, first_name, full_name")
    suspend fun getAllActive(): List<MemberEntity>

    @Query("SELECT * FROM members WHERE is_active = 1 ORDER BY last_name, first_name, full_name")
    fun getAllActiveFlow(): Flow<List<MemberEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(members: List<MemberEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCards(cards: List<MemberCardEntity>)

    /** Mark members no longer in the sync payload as inactive. */
    @Query("UPDATE members SET is_active = 0 WHERE id NOT IN (:activeIds)")
    suspend fun deactivateMissing(activeIds: List<Int>)
}
