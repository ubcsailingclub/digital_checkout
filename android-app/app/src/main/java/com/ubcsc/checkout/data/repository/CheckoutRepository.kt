package com.ubcsc.checkout.data.repository

import com.ubcsc.checkout.data.SheetsUploader
import com.ubcsc.checkout.data.db.AppDatabase
import com.ubcsc.checkout.data.db.entities.CheckoutSessionEntity
import com.ubcsc.checkout.data.db.entities.SessionCrewMemberEntity
import com.ubcsc.checkout.viewmodel.ActiveCheckout
import com.ubcsc.checkout.viewmodel.ActiveSession
import com.ubcsc.checkout.viewmodel.CrewEntry
import com.ubcsc.checkout.viewmodel.Member
import com.ubcsc.checkout.viewmodel.RecentSession

class CheckoutRepository(db: AppDatabase) {
    private val sessionDao = db.checkoutSessionDao()
    private val crewDao    = db.sessionCrewMemberDao()
    private val memberDao  = db.memberDao()
    private val craftDao   = db.craftDao()

    /** Returns the active checkout for this member, or null if none. */
    suspend fun getActiveCheckoutForMember(memberId: Int): ActiveCheckout? {
        val session = sessionDao.getActiveByMember(memberId) ?: return null
        val craft   = craftDao.getAll().firstOrNull { it.id == session.craftId } ?: return null
        return ActiveCheckout(
            sessionId = session.id,
            craftCode = craft.craftCode,
            craftName = craft.displayName
        )
    }

    suspend fun createCheckout(
        member:              Member,
        craftId:             Int,
        crew:                List<CrewEntry>,
        expectedReturnHours: Int?,
        sheetsScriptUrl:     String = ""
    ) {
        val now = System.currentTimeMillis()
        val etr = expectedReturnHours?.let { now + it * 3_600_000L }
        val sessionId = sessionDao.insert(
            CheckoutSessionEntity(
                memberId            = member.id.toInt(),
                craftId             = craftId,
                checkoutTime        = now,
                checkinTime         = null,
                status              = "active",
                expectedReturnTime  = etr,
                notesOut            = null,
                notesIn             = null,
                damageReported      = false
            )
        ).toInt()

        if (crew.isNotEmpty()) {
            crewDao.insertAll(crew.map { entry ->
                SessionCrewMemberEntity(
                    sessionId   = sessionId,
                    memberId    = entry.memberId,
                    displayName = entry.name,
                    isGuest     = entry.isGuest
                )
            })
        }

        if (sheetsScriptUrl.isNotBlank()) {
            val craftEntity = craftDao.getAll().firstOrNull { it.id == craftId }
            SheetsUploader.logCheckout(
                scriptUrl     = sheetsScriptUrl,
                sessionId     = sessionId,
                skipper       = member.name,
                crew          = crew.map { it.name },
                craft         = craftEntity?.displayName ?: "Unknown",
                code          = craftEntity?.craftCode ?: "",
                checkoutEpoch = now,
                etaEpoch      = etr
            )
        }
    }

    suspend fun completeCheckin(
        sessionId:       Int,
        notes:           String?,
        hasDamage:       Boolean,
        sheetsScriptUrl: String = ""
    ) {
        val checkinTime = System.currentTimeMillis()
        sessionDao.complete(
            id          = sessionId,
            checkinTime = checkinTime,
            notes       = notes?.takeIf { it.isNotBlank() },
            damage      = hasDamage
        )

        if (sheetsScriptUrl.isNotBlank()) {
            val session = sessionDao.getById(sessionId)
            val member  = session?.let { memberDao.getById(it.memberId) }
            val craft   = session?.let { s -> craftDao.getAll().firstOrNull { it.id == s.craftId } }
            SheetsUploader.logCheckin(
                scriptUrl     = sheetsScriptUrl,
                sessionId     = sessionId,
                notes         = notes,
                hasDamage     = hasDamage,
                checkinEpoch  = checkinTime,
                checkoutEpoch = session?.checkoutTime ?: checkinTime
            )

            if (hasDamage && !notes.isNullOrBlank()) {
                SheetsUploader.logDamage(
                    scriptUrl = sheetsScriptUrl,
                    sessionId = sessionId,
                    craft     = craft?.displayName ?: "Unknown",
                    skipper   = member?.fullName ?: "Unknown",
                    notes     = notes
                )
            }
        }
    }

    suspend fun updateEtr(sessionId: Int, expectedReturnHours: Int?) {
        val etaEpoch = expectedReturnHours?.let { System.currentTimeMillis() + it * 3_600_000L }
        sessionDao.updateEtr(sessionId, etaEpoch)
    }

    suspend fun getAllActiveSessions(): List<ActiveSession> {
        val sessions = sessionDao.getAllActive()
        return sessions.mapNotNull { session ->
            val member = memberDao.getById(session.memberId) ?: return@mapNotNull null
            val craft  = craftDao.getAll().firstOrNull { it.id == session.craftId } ?: return@mapNotNull null
            session.toActiveDomain(member.fullName, craft.craftCode, craft.displayName, craft.craftClass ?: craft.displayName)
        }
    }

    suspend fun getRecentSessions(limit: Int = 50): List<RecentSession> {
        val sessions = sessionDao.getRecent(limit)
        return sessions.mapNotNull { session ->
            val member = memberDao.getById(session.memberId) ?: return@mapNotNull null
            val craft  = craftDao.getAll().firstOrNull { it.id == session.craftId } ?: return@mapNotNull null
            val crew   = crewDao.getBySession(session.id)
            session.toRecentDomain(member.fullName, craft.displayName, crew.map { it.displayName })
        }
    }
}

// ---------------------------------------------------------------------------
// Entity → domain mappers
// ---------------------------------------------------------------------------

private fun CheckoutSessionEntity.toActiveDomain(
    memberName: String,
    craftCode:  String,
    craftName:  String,
    craftClass: String
): ActiveSession {
    val checkoutInstant = java.time.Instant.ofEpochMilli(checkoutTime)
    val minutesOut = java.time.Duration.between(checkoutInstant, java.time.Instant.now()).toMinutes()
        .coerceAtLeast(0)
    val timeOutStr = when {
        minutesOut < 60       -> "${minutesOut}m"
        minutesOut % 60 == 0L -> "${minutesOut / 60}h"
        else                  -> "${minutesOut / 60}h ${minutesOut % 60}m"
    }
    val etr = expectedReturnTime?.let { millis ->
        java.time.Instant.ofEpochMilli(millis)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalTime()
    }
    return ActiveSession(
        sessionId          = id,
        craftCode          = craftCode,
        craftName          = craftName,
        craftClass         = craftClass,
        memberName         = memberName,
        timeOut            = timeOutStr,
        expectedReturnTime = etr,
        isOverdue          = etr != null && java.time.LocalTime.now().isAfter(etr)
    )
}

private fun CheckoutSessionEntity.toRecentDomain(
    skipperName: String,
    craftName:   String,
    crewNames:   List<String>
): RecentSession {
    val fmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
    fun epochToTime(millis: Long?) = millis?.let {
        java.time.Instant.ofEpochMilli(it)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalTime()
            .format(fmt)
    } ?: "—"

    val localDate = java.time.Instant.ofEpochMilli(checkoutTime)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate()

    return RecentSession(
        skipperName       = skipperName,
        crewNames         = crewNames,
        craft             = craftName,
        timeOut           = epochToTime(checkoutTime),
        eta               = epochToTime(expectedReturnTime),
        timeIn            = epochToTime(checkinTime),
        isActive          = status == "active",
        checkoutLocalDate = localDate
    )
}
