package com.ubcsc.checkout.data.repository

import com.ubcsc.checkout.data.db.AppDatabase
import com.ubcsc.checkout.data.db.entities.CraftEntity
import com.ubcsc.checkout.viewmodel.Craft
import com.ubcsc.checkout.viewmodel.CraftFleetStatus
import kotlin.math.abs

class CraftRepository(db: AppDatabase) {
    private val craftDao         = db.craftDao()
    private val sessionDao       = db.checkoutSessionDao()

    suspend fun syncFromFleetStatus(craftStatus: Map<String, CraftFleetStatus>) {
        val toInsert = craftStatus.mapNotNull { (code, cs) ->
            val name = cs.name?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val cls  = cs.craftClass?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            CraftEntity(
                id               = abs(code.hashCode()),
                craftCode        = code,
                displayName      = name,
                fleetType        = cls,
                craftClass       = cls,
                capacity         = null,
                isActive         = cs.status != "deactivated",
                requiresCheckout = true,
                status           = "available",
                statusReason     = null
            )
        }
        if (toInsert.isEmpty()) return
        craftDao.insertIfAbsent(toInsert)
        toInsert.forEach { e ->
            craftDao.updateMeta(e.craftCode, e.displayName, e.fleetType, e.craftClass, e.isActive)
        }
        craftDao.deactivateMissingCodes(craftStatus.keys.toList())
    }

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
