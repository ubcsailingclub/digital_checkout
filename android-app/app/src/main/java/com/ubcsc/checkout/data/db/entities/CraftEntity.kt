package com.ubcsc.checkout.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "craft",
    indices = [Index("status"), Index("is_active")]
)
data class CraftEntity(
    @PrimaryKey val id: Int,
    @ColumnInfo("craft_code")         val craftCode: String,
    @ColumnInfo("display_name")       val displayName: String,
    @ColumnInfo("fleet_type")         val fleetType: String,
    @ColumnInfo("craft_class")        val craftClass: String?,
    @ColumnInfo("capacity")           val capacity: Int?,
    @ColumnInfo("is_active")          val isActive: Boolean,
    @ColumnInfo("requires_checkout")  val requiresCheckout: Boolean,
    @ColumnInfo("status")             val status: String,       // "available" | "checked_out" | "grounded"
    @ColumnInfo("status_reason")      val statusReason: String?
)
