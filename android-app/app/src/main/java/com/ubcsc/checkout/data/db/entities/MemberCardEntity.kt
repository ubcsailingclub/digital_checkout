package com.ubcsc.checkout.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "member_cards",
    foreignKeys = [ForeignKey(
        entity = MemberEntity::class,
        parentColumns = ["id"],
        childColumns = ["member_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index("member_id"),
        Index("card_uid_normalized", unique = true)
    ]
)
data class MemberCardEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo("member_id")          val memberId: Int,
    @ColumnInfo("card_uid_normalized") val cardUidNormalized: String,
    @ColumnInfo("is_active")          val isActive: Boolean
)
