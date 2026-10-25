package com.sshtunnel.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.sshtunnel.data.model.SshServerProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDao {
    @Query("SELECT * FROM ssh_servers ORDER BY createdAt DESC")
    fun getAllServers(): Flow<List<SshServerProfile>>

    @Query("SELECT * FROM ssh_servers WHERE isActive = 1 LIMIT 1")
    fun getActiveServer(): Flow<SshServerProfile?>

    @Query("SELECT * FROM ssh_servers WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveServerSync(): SshServerProfile?

    @Query("SELECT * FROM ssh_servers WHERE id = :id LIMIT 1")
    suspend fun getServerById(id: Long): SshServerProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(server: SshServerProfile): Long

    @Update
    suspend fun update(server: SshServerProfile)

    @Delete
    suspend fun delete(server: SshServerProfile)

    @Query("UPDATE ssh_servers SET isActive = 0")
    suspend fun resetAllActive()

    @Query("UPDATE ssh_servers SET isActive = 1 WHERE id = :id")
    suspend fun setActive(id: Long)

    @Transaction
    suspend fun setActiveServer(id: Long) {
        resetAllActive()
        setActive(id)
    }

    @Query("SELECT id FROM ssh_servers ORDER BY createdAt DESC LIMIT 1")
    suspend fun getNewestServerId(): Long?

    @Query("UPDATE ssh_servers SET lastPingMs = :pingMs WHERE id = :id")
    suspend fun updatePing(id: Long, pingMs: Long?)
}
