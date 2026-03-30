package com.ubcsc.checkout.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "session_crew_members",
    foreignKeys = [ForeignKey(
        entity = CheckoutSessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["session_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("session_id")]
)
data class SessionCrewMemberEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo("session_id")   val sessionId: Int,
    @ColumnInfo("member_id")    val memberId: Int?,
    @ColumnInfo("display_name") val displayName: String,
    @ColumnInfo("is_guest")     val isGuest: Boolean
)
