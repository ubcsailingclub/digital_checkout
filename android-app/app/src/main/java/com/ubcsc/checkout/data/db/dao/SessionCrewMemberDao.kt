package com.ubcsc.checkout.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.ubcsc.checkout.data.db.entities.SessionCrewMemberEntity

@Dao
interface SessionCrewMemberDao {

    @Insert
    suspend fun insertAll(crew: List<SessionCrewMemberEntity>)

    @Query("SELECT * FROM session_crew_members WHERE session_id = :sessionId")
    suspend fun getBySession(sessionId: Int): List<SessionCrewMemberEntity>
}
