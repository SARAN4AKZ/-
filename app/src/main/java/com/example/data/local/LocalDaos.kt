package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUser(username: String): UserEntity?

    @Query("SELECT COUNT(*) FROM users")
    fun observeUserCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUser(user: UserEntity)
}

@Dao
interface OrganizationDao {
    @Query("SELECT * FROM organizations WHERE userId = :userId ORDER BY defaultProfile DESC, updatedAtMillis DESC")
    fun observeByUser(userId: String): Flow<List<OrganizationEntity>>

    @Query("UPDATE organizations SET defaultProfile = 0 WHERE userId = :userId AND id != :keepId")
    suspend fun clearDefaultExcept(userId: String, keepId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(org: OrganizationEntity)

    @Query("DELETE FROM organizations WHERE userId = :userId AND id = :id")
    suspend fun deleteById(userId: String, id: String)
}

@Dao
interface CounterpartyDao {
    @Query("SELECT * FROM counterparties WHERE userId = :userId ORDER BY updatedAtMillis DESC")
    fun observeByUser(userId: String): Flow<List<CounterpartyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(party: CounterpartyEntity)

    @Query("DELETE FROM counterparties WHERE userId = :userId AND id = :id")
    suspend fun deleteById(userId: String, id: String)
}

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents WHERE userId = :userId ORDER BY updatedAtMillis DESC")
    fun observeByUser(userId: String): Flow<List<DocumentEntity>>

    @Query("UPDATE documents SET status = :status, updatedAtMillis = :updatedAt WHERE userId = :userId AND id = :id")
    suspend fun updateStatus(userId: String, id: String, status: String, updatedAt: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(doc: DocumentEntity)

    @Query("DELETE FROM documents WHERE userId = :userId AND id = :id")
    suspend fun deleteById(userId: String, id: String)
}
