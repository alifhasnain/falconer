package dev.metiscraft.falconer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The on-device store for captured transactions.
 *
 * Schema export is enabled in Phase 10 alongside migration scaffolding; v1 is
 * a single-version, single-table database.
 */
@Database(
    entities = [HttpTransactionEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class FalconerDatabase : RoomDatabase() {

    abstract fun transactionDao(): HttpTransactionDao

    companion object {
        @Volatile
        private var instance: FalconerDatabase? = null

        fun get(context: Context): FalconerDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FalconerDatabase::class.java,
                    "falconer.db",
                ).build().also { instance = it }
            }
    }
}
