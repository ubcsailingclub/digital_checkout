package com.ubcsc.checkout.data.repository

import com.ubcsc.checkout.data.db.AppDatabase
import com.ubcsc.checkout.data.db.normalizeCardUid
import com.ubcsc.checkout.data.db.entities.MemberEntity
import com.ubcsc.checkout.viewmodel.MemberSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MemberRepository(db: AppDatabase) {
    private val memberDao = db.memberDao()

    suspend fun getByCardUid(rawUid: String): MemberEntity? =
        memberDao.getByCardUid(normalizeCardUid(rawUid))

    suspend fun getById(id: Int): MemberEntity? =
        memberDao.getById(id)

    suspend fun getAllActive(): List<MemberSummary> =
        memberDao.getAllActive().map { m ->
            MemberSummary(
                id   = m.id,
                name = listOfNotNull(m.firstName, m.lastName)
                    .joinToString(" ")
                    .ifBlank { m.fullName }
            )
        }

    fun getAllActiveFlow(): Flow<List<MemberSummary>> =
        memberDao.getAllActiveFlow().map { list ->
            list.map { m ->
                MemberSummary(
                    id   = m.id,
                    name = listOfNotNull(m.firstName, m.lastName)
                        .joinToString(" ")
                        .ifBlank { m.fullName }
                )
            }
        }
}
