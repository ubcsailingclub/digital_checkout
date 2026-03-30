package com.ubcsc.checkout.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ubcsc.checkout.data.db.dao.CraftDao
import com.ubcsc.checkout.data.db.dao.CheckoutSessionDao
import com.ubcsc.checkout.data.db.dao.MemberDao
import com.ubcsc.checkout.data.db.dao.SessionCrewMemberDao
import com.ubcsc.checkout.data.db.entities.CheckoutSessionEntity
import com.ubcsc.checkout.data.db.entities.CraftEntity
import com.ubcsc.checkout.data.db.entities.MemberCardEntity
import com.ubcsc.checkout.data.db.entities.MemberEntity
import com.ubcsc.checkout.data.db.entities.SessionCrewMemberEntity

@Database(
    entities = [
        MemberEntity::class,
        MemberCardEntity::class,
        CraftEntity::class,
        CheckoutSessionEntity::class,
        SessionCrewMemberEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun memberDao(): MemberDao
    abstract fun craftDao(): CraftDao
    abstract fun checkoutSessionDao(): CheckoutSessionDao
    abstract fun sessionCrewMemberDao(): SessionCrewMemberDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "checkout.db"
            ).build().also { INSTANCE = it }
        }
    }
}
