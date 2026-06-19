package dev.metiscraft.falconer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HttpTransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: HttpTransactionEntity)

    @Update
    suspend fun update(transaction: HttpTransactionEntity)

    /** One-shot read used by the insert-then-update merge path. */
    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun findById(id: String): HttpTransactionEntity?

    @Query("SELECT * FROM transactions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<HttpTransactionEntity>>

    @Query(
        """SELECT * FROM transactions
           WHERE url LIKE :query OR requestBody LIKE :query OR responseBody LIKE :query
           ORDER BY startedAt DESC""",
    )
    fun observeFiltered(query: String): Flow<List<HttpTransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observeById(id: String): Flow<HttpTransactionEntity?>

    @Query("SELECT COUNT(*) FROM transactions")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM transactions")
    suspend fun clear()

    @Query("DELETE FROM transactions WHERE startedAt < :threshold")
    suspend fun deleteBefore(threshold: Long)
}
