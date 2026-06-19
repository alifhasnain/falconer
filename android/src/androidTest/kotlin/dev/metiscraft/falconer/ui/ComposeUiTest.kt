package dev.metiscraft.falconer.ui

import android.content.Context
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.metiscraft.falconer.data.FalconerDatabase
import dev.metiscraft.falconer.data.testEntity
import dev.metiscraft.falconer.ui.detail.BodySection
import dev.metiscraft.falconer.ui.list.TransactionListScreen
import dev.metiscraft.falconer.ui.list.TransactionListViewModel
import dev.metiscraft.falconer.ui.theme.FalconerTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
class ComposeUiTest {

    @get:Rule
    val rule = createComposeRule()

    private fun inMemoryDb(): FalconerDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, FalconerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @Test
    fun list_rendersSeededTransactionAndFilters() {
        val db = inMemoryDb()
        runBlocking {
            db.transactionDao().insert(testEntity("a", path = "/users", url = "https://x/users"))
            db.transactionDao().insert(testEntity("b", path = "/posts", url = "https://x/posts"))
        }
        val vm = TransactionListViewModel(db.transactionDao())

        rule.setContent { FalconerTheme { TransactionListScreen(vm, onOpen = {}) } }

        rule.onNodeWithText("/users").assertIsDisplayed()
        rule.onNodeWithText("/posts").assertIsDisplayed()

        rule.onNodeWithText("Search url, request, response").performTextInput("posts")
        rule.waitForIdle()
        rule.onNodeWithText("/posts").assertIsDisplayed()

        db.close()
    }

    @Test
    fun bodySection_marksEmptyBody() {
        rule.setContent {
            FalconerTheme {
                BodySection(body = null, bodyKind = "none", contentType = null, imageBytes = null)
            }
        }
        rule.onNodeWithText("(no body)").assertIsDisplayed()
    }

    @Test
    fun bodySection_showsJsonText() {
        rule.setContent {
            FalconerTheme {
                BodySection(
                    body = "{\"hello\":\"world\"}",
                    bodyKind = "json",
                    contentType = "application/json",
                    imageBytes = null,
                )
            }
        }
        rule.onNodeWithText("hello", substring = true).assertIsDisplayed()
    }
}
