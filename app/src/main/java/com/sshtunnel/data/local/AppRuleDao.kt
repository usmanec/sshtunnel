package com.sshtunnel.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sshtunnel.data.model.AppRoutingRule
import kotlinx.coroutines.flow.Flow

@Dao
interface AppRuleDao {
    @Query("SELECT * FROM app_routing_rules ORDER BY appName ASC")
    fun getAllRules(): Flow<List<AppRoutingRule>>

    @Query("SELECT * FROM app_routing_rules WHERE isSelected = 1")
    suspend fun getSelectedRules(): List<AppRoutingRule>

    @Query("SELECT packageName FROM app_routing_rules WHERE isSelected = 1")
    suspend fun getSelectedPackageNames(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rules: List<AppRoutingRule>)

    @Query("UPDATE app_routing_rules SET isSelected = :selected WHERE packageName = :packageName")
    suspend fun setAppSelected(packageName: String, selected: Boolean)

    @Query("UPDATE app_routing_rules SET isSelected = :selected")
    suspend fun setAllSelected(selected: Boolean)

    @Query("SELECT COUNT(*) FROM app_routing_rules WHERE isSelected = 1")
    fun getSelectedCountFlow(): Flow<Int>
}
