package com.yumiru11.githubapp.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yumiru11.githubapp.core.database.dao.CachedReadmeDao
import com.yumiru11.githubapp.core.database.entity.CachedReadmeEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * CachedReadmeDao 读写测试（Robolectric + inMemoryDatabaseBuilder）。
 *
 * 覆盖：upsert 插入/覆盖、按 owner/repo 查询、列表按 updatedAt 倒序、删除、清空。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CachedReadmeDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: CachedReadmeDao

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    AppDatabase::class.java,
                ).allowMainThreadQueries()
                .addMigrations(AppDatabase.MIGRATION_1_2)
                .build()
        dao = db.cachedReadmeDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun upsert_insertsEntity_thenQueriesBack() =
        runTest {
            val entity = cachedReadme()

            dao.upsert(entity)
            val loaded = dao.getByOwnerAndRepo("octocat", "Hello-World")

            assertEquals(entity, loaded)
        }

    @Test
    fun upsert_samePrimaryKey_overwritesHtml() =
        runTest {
            dao.upsert(cachedReadme(contentHash = "old", html = "<p>old</p>"))
            dao.upsert(cachedReadme(contentHash = "new", html = "<p>new</p>"))

            val loaded = dao.getByOwnerAndRepo("octocat", "Hello-World")

            assertEquals("new", loaded?.contentHash)
            assertEquals("<p>new</p>", loaded?.html)
            assertEquals(1, dao.getAll().size)
        }

    @Test
    fun getAll_returnsEntriesOrderedByUpdatedAtDescending() =
        runTest {
            dao.upsert(cachedReadme(owner = "a", repo = "older", updatedAt = 1_000L))
            dao.upsert(cachedReadme(owner = "b", repo = "newer", updatedAt = 2_000L))

            val all = dao.getAll()

            assertEquals(listOf("b/newer", "a/older"), all.map { "${it.owner}/${it.repo}" })
        }

    @Test
    fun delete_removesOnlyMatchingEntry() =
        runTest {
            dao.upsert(cachedReadme(owner = "octocat", repo = "Hello-World"))
            dao.upsert(cachedReadme(owner = "octocat", repo = "Other-Repo"))

            dao.delete("octocat", "Hello-World")

            assertNull(dao.getByOwnerAndRepo("octocat", "Hello-World"))
            assertEquals("Other-Repo", dao.getByOwnerAndRepo("octocat", "Other-Repo")?.repo)
        }

    @Test
    fun clear_removesAllEntries() =
        runTest {
            dao.upsert(cachedReadme(owner = "octocat", repo = "Hello-World"))
            dao.upsert(cachedReadme(owner = "octocat", repo = "Other-Repo"))

            dao.clear()

            assertEquals(0, dao.getAll().size)
        }

    private fun cachedReadme(
        owner: String = "octocat",
        repo: String = "Hello-World",
        contentHash: String = "sha256",
        themeVersion: String = "v1",
        html: String = "<h1>Hello</h1>",
        updatedAt: Long = 1_700_000_000_000L,
    ): CachedReadmeEntity =
        CachedReadmeEntity(
            owner = owner,
            repo = repo,
            contentHash = contentHash,
            themeVersion = themeVersion,
            html = html,
            updatedAt = updatedAt,
        )
}
