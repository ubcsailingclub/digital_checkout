package com.ubcsc.checkout.data.repository

import com.ubcsc.checkout.data.db.AppDatabase
import com.ubcsc.checkout.data.db.entities.CraftEntity
import com.ubcsc.checkout.viewmodel.Craft

class CraftRepository(db: AppDatabase) {
    private val craftDao         = db.craftDao()
    private val sessionDao       = db.checkoutSessionDao()

    suspend fun getAll(): List<Craft> {
        val crafts = craftDao.getAll()
        return crafts.map { entity ->
            val activeSession = sessionDao.getActiveByCraft(entity.id)
            val etr = activeSession?.expectedReturnTime?.let { millis ->
                java.time.Instant.ofEpochMilli(millis)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalTime()
            }
            Craft(
                id                 = entity.id.toString(),
                code               = entity.craftCode,
                displayName        = entity.displayName,
                craftClass         = entity.craftClass ?: "",
                isAvailable        = entity.status == "available" && activeSession == null,
                expectedReturnTime = etr
            )
        }
    }
}
