package com.ubcsc.checkout.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ubcsc.checkout.data.db.entities.CraftEntity

@Dao
interface CraftDao {

    @Query("SELECT * FROM craft WHERE is_active = 1 ORDER BY fleet_type, display_name")
    suspend fun getAll(): List<CraftEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(crafts: List<CraftEntity>)

    @Query("UPDATE craft SET status = :status, status_reason = :reason WHERE id = :id")
    suspend fun updateStatus(id: Int, status: String, reason: String?)

    @Query("UPDATE craft SET status = :status, status_reason = :reason WHERE craft_class = :craftClass")
    suspend fun updateStatusByClass(craftClass: String, status: String, reason: String?)
}
