package com.ubcsc.checkout.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "checkout_sessions",
    foreignKeys = [
        ForeignKey(MemberEntity::class, ["id"], ["member_id"]),
        ForeignKey(CraftEntity::class,  ["id"], ["craft_id"])
    ],
    indices = [Index("member_id"), Index("craft_id"), Index("status")]
)
data class CheckoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo("member_id")             val memberId: Int,
    @ColumnInfo("craft_id")              val craftId: Int,
    @ColumnInfo("checkout_time")         val checkoutTime: Long,       // epoch millis
    @ColumnInfo("checkin_time")          val checkinTime: Long?,
    @ColumnInfo("status")                val status: String,           // "active" | "completed"
    @ColumnInfo("expected_return_time")  val expectedReturnTime: Long?,
    @ColumnInfo("notes_out")             val notesOut: String?,
    @ColumnInfo("notes_in")              val notesIn: String?,
    @ColumnInfo("damage_reported")       val damageReported: Boolean
)
