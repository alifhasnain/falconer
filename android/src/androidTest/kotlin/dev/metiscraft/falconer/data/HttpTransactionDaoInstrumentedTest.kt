package dev.metiscraft.falconer.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room behavior on a real SQLite backend (requires a device/emulator).
 * Covers: insert-then-update merge, deleteBefore staleness, filtered LIKE,
 * and restart persistence.
 */
@RunWith(AndroidJUnit4::class)
class HttpTransactionDaoInstrumentedTest {

    private lateinit var db: FalconerDatabase
    private lateinit var dao: HttpTransactionDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FalconerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.transactionDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertThenUpdate_yieldsOneMergedRow() = runBlocking {
        dao.insert(testEntity("a", startedAt = 100))
        val merged = dao.findById("a")!!.copy(statusCode = 200, responseBody = "ok")
        dao.update(merged)

        val all = dao.observeAll().first()
        assertEquals(1, all.size)
        assertEquals(200, all[0].statusCode)
        assertEquals("ok", all[0].responseBody)
    }

    @Test
    fun deleteBefore_removesOnlyStaleRows() = runBlocking {
        dao.insert(testEntity("old", startedAt = 10))
        dao.insert(testEntity("new", startedAt = 100))

        dao.deleteBefore(50)

        assertEquals(setOf("new"), dao.observeAll().first().map { it.id }.toSet())
    }

    @Test
    fun observeFiltered_matchesUrlAndBodies() = runBlocking {
        dao.insert(testEntity("u", url = "https://x/users", requestBody = "needle-in-request"))
        dao.insert(testEntity("p", url = "https://x/posts", responseBody = "needle-in-response"))
        dao.insert(testEntity("z", url = "https://x/zzz"))

        assertEquals(setOf("u", "p"), dao.observeFiltered("%needle%").first().map { it.id }.toSet())
        assertEquals(setOf("p"), dao.observeFiltered("%posts%").first().map { it.id }.toSet())
    }

    @Test
    fun transactionsSurviveDatabaseReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "falconer-restart-test.db"
        context.deleteDatabase(name)

        var fileDb = Room.databaseBuilder(context, FalconerDatabase::class.java, name).build()
        fileDb.transactionDao().insert(testEntity("persist"))
        fileDb.close()

        fileDb = Room.databaseBuilder(context, FalconerDatabase::class.java, name).build()
        assertEquals(1, fileDb.transactionDao().observeAll().first().size)
        fileDb.close()
        context.deleteDatabase(name)
    }
}
