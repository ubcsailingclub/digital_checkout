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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(crafts: List<CraftEntity>)

    @Query("""
        UPDATE craft
        SET display_name = :displayName,
            fleet_type   = :fleetType,
            craft_class  = :craftClass,
            is_active    = :isActive
        WHERE craft_code = :craftCode
    """)
    suspend fun updateMeta(
        craftCode: String,
        displayName: String,
        fleetType: String,
        craftClass: String?,
        isActive: Boolean
    )

    @Query("UPDATE craft SET is_active = 0 WHERE craft_code NOT IN (:codes)")
    suspend fun deactivateMissingCodes(codes: List<String>)
}
