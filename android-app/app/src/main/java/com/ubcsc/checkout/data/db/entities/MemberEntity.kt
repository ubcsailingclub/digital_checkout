package com.ubcsc.checkout.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "members",
    indices = [Index("is_active"), Index("full_name")]
)
data class MemberEntity(
    @PrimaryKey val id: Int,
    @ColumnInfo("wa_contact_id")      val waContactId: Int,
    @ColumnInfo("full_name")          val fullName: String,
    @ColumnInfo("first_name")         val firstName: String?,
    @ColumnInfo("last_name")          val lastName: String?,
    @ColumnInfo("membership_status")  val membershipStatus: String,
    @ColumnInfo("is_active")          val isActive: Boolean,
    @ColumnInfo("certifications_json") val certificationsJson: String?
)
