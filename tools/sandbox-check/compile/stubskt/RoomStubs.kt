@file:Suppress("unused", "UNUSED_PARAMETER")

package androidx.room

import kotlin.reflect.KClass

annotation class Entity(val tableName: String = "")
annotation class PrimaryKey(val autoGenerate: Boolean = false)
annotation class Dao
annotation class Query(val value: String)
annotation class Insert(val onConflict: Int = OnConflictStrategy.ABORT)
annotation class Update
annotation class Delete
annotation class Transaction
annotation class TypeConverter
annotation class TypeConverters(vararg val value: KClass<*>)
annotation class Database(val entities: Array<KClass<*>>, val version: Int, val exportSchema: Boolean = true)

object OnConflictStrategy {
    const val ABORT = 3
    const val REPLACE = 1
    const val IGNORE = 5
}

abstract class RoomDatabase {
    class Builder<T : RoomDatabase> {
        fun fallbackToDestructiveMigration(): Builder<T> = this
        fun build(): T = error("stub")
    }
}

object Room {
    fun <T : RoomDatabase> databaseBuilder(context: android.content.Context, klass: Class<T>, name: String): RoomDatabase.Builder<T> = RoomDatabase.Builder()
}

suspend fun <R> RoomDatabase.withTransaction(block: suspend () -> R): R = block()
