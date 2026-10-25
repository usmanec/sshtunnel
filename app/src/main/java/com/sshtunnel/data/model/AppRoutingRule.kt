package com.sshtunnel.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class RoutingPolicy {
    ROUTE_ONLY_SELECTED,   // Default: Bypass all, route ONLY selected apps through SSH
    ROUTE_ALL_EXCEPT       // Route everything through SSH, bypass selected apps
}

@Entity(tableName = "app_routing_rules")
data class AppRoutingRule(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val isSelected: Boolean = false,
    val isSystemApp: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
