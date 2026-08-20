package com.yumiru11.githubapp.core.githubrest.api

import com.yumiru11.githubapp.core.githubrest.auth.GuestTokenProvider
import com.yumiru11.githubapp.core.githubrest.http.InMemoryEtagStore
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [PullRequestApi] 集成测试（T15，MockWebServer 模拟 GitHub API，零真实网络）。
 *
 * 覆盖：listPullRequests 默认/自定义查询参数、getPullRequest 详情字段（mergeable/head/base/draft）、
 * listCommits 提交与文件摘要、listFiles 文件变更、listTimeline PR 事件（reviewed/commented/committed）、
 * listCheckRuns 状态/结论/输出、getCombinedStatus 摘要。
 */
class PullRequestApiTest {
    private lateinit var server: MockWebServer
    private lateinit var pullRequestApi: PullRequestApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val retrofit =
            GitHubRestClient.createRetrofit(
                baseUrl = server.url("/"),
                client =
                    GitHubRestClient.createOkHttpClient(
                        tokenProvider = GuestTokenProvider(),
                        etagStore = InMemoryEtagStore(),
                        debugLogging = false,
                    ),
                json = GitHubRestClient.createJson(),
            )
        pullRequestApi = retrofit.create(PullRequestApi::class.java)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun listPullRequests_defaultParams_sendsStatePagePerPageDefaults() =
        runTest {
            server.enqueue(jsonResponse("[]"))

            val pulls = pullRequestApi.listPullRequests("octocat", "Hello-World")

            assertEquals(0, pulls.size)
            val request = server.takeRequest()
            assertEquals("/repos/octocat/Hello-World/pulls", request.url.encodedPath)
            assertEquals("open", request.url.queryParameter("state"))
            assertEquals("1", request.url.queryParameter("page"))
            assertEquals("30", request.url.queryParameter("per_page"))
        }

    @Test
    fun listPullRequests_customParamsAndDto_parsesNestedFields() =
        runTest {
            server.enqueue(
                jsonResponse(
                    """
                    [
                      {
                        "id": 1,
                        "number": 42,
                        "title": "Add feature",
                        "state": "open",
                        "body": "Description",
                        "user": { "login": "octocat", "id": 1 },
                        "labels": [ { "name": "enhancement", "color": "a2eeef" } ],
                        "comments": 3,
                        "review_comments": 2,
                        "commits": 5,
                        "additions": 100,
                        "deletions": 20,
                        "changed_files": 4,
                        "created_at": "2026-08-01T00:00:00Z",
                        "html_url": "https://github.com/octocat/Hello-World/pull/42",
                        "mergeable": true,
                        "mergeable_state": "clean",
                        "draft": false,
                        "head": { "label": "octocat:feature", "ref": "feature", "sha": "abc123" },
                        "base": { "label": "octocat:main", "ref": "main", "sha": "def456" },
                        "requested_reviewers": [ { "login": "torvalds", "id": 2 } ]
                      }
                    ]
                    """.trimIndent(),
                ),
            )

            val pulls = pullRequestApi.listPullRequests("octocat", "Hello-World", state = "closed", page = 2, perPage = 50)

            val pull = pulls.single()
            assertEquals(42, pull.number)
            assertEquals("Add feature", pull.title)
            assertEquals("octocat", pull.user?.login)
            assertEquals("enhancement", pull.labels.single().name)
            assertEquals(3, pull.comments)
            assertEquals(2, pull.reviewComments)
            assertEquals(5, pull.commits)
            assertEquals(100, pull.additions)
            assertEquals(20, pull.deletions)
            assertEquals(4, pull.changedFiles)
            assertEquals(true, pull.mergeable)
            assertEquals("clean", pull.mergeableState)
            assertEquals("feature", pull.head?.ref)
            assertEquals("main", pull.base?.ref)
            assertEquals("torvalds", pull.requestedReviewers.single().login)

            val request = server.takeRequest()
            assertEquals("closed", request.url.queryParameter("state"))
            assertEquals("2", request.url.queryParameter("page"))
            assertEquals("50", request.url.queryParameter("per_page"))
        }

    @Test
    fun getPullRequest_draftAndNullMergeable_parsesPendingState() =
        runTest {
            server.enqueue(
                jsonResponse(
                    """
                    {
                      "id": 2,
                      "number": 43,
                      "title": "Draft PR",
                      "state": "open",
                      "draft": true,
                      "mergeable": null,
                      "mergeable_state": "unknown"
                    }
                    """.trimIndent(),
                ),
            )

            val pull = pullRequestApi.getPullRequest("octocat", "Hello-World", number = 43)

            assertTrue("draft 字段应解析为 true", pull.draft)
            assertNull("mergeable null = 待检查", pull.mergeable)
            assertEquals("unknown", pull.mergeableState)
            assertEquals("/repos/octocat/Hello-World/pulls/43", server.takeRequest().url.encodedPath)
        }

    @Test
    fun listCommits_parsesCommitMetadataAndFileSummaries() =
        runTest {
            server.enqueue(
                jsonResponse(
                    """
                    [
                      {
                        "sha": "abc123def456",
                        "commit": {
                          "message": "Fix bug",
                          "author": { "name": "Octo", "email": "o@x.com", "date": "2026-08-01T00:00:00Z" }
                        },
                        "author": { "login": "octocat", "id": 1, "avatar_url": "https://avatars.example/1.png" },
                        "html_url": "https://github.com/o/r/commit/abc123def456",
                        "files": [
                          { "filename": "src/Main.kt", "status": "modified", "additions": 10, "deletions": 2, "changes": 12 }
                        ]
                      }
                    ]
                    """.trimIndent(),
                ),
            )

            val commits = pullRequestApi.listCommits("octocat", "Hello-World", number = 42)

            val commit = commits.single()
            assertEquals("abc123def456", commit.sha)
            assertEquals("Fix bug", commit.commit?.message)
            assertEquals("octocat", commit.author?.login)
            assertEquals("src/Main.kt", commit.files.single().filename)
            assertEquals(10, commit.files.single().additions)
            assertEquals(2, commit.files.single().deletions)
            assertEquals("/repos/octocat/Hello-World/pulls/42/commits", server.takeRequest().url.encodedPath)
        }

    @Test
    fun listFiles_parsesStatusAndPatch() =
        runTest {
            server.enqueue(
                jsonResponse(
                    """
                    [
                      {
                        "filename": "README.md",
                        "status": "added",
                        "additions": 5,
                        "deletions": 0,
                        "changes": 5,
                        "patch": "@@ -0,0 +1,5 @@\n+Hello"
                      }
                    ]
                    """.trimIndent(),
                ),
            )

            val files = pullRequestApi.listFiles("octocat", "Hello-World", number = 42)

            val file = files.single()
            assertEquals("README.md", file.filename)
            assertEquals("added", file.status)
            assertEquals(5, file.additions)
            assertEquals(0, file.deletions)
            assertTrue(file.patch?.contains("+Hello") == true)
            assertEquals("/repos/octocat/Hello-World/pulls/42/files", server.takeRequest().url.encodedPath)
        }

    @Test
    fun listTimeline_prEvents_parsesReviewedCommentedCommitted() =
        runTest {
            server.enqueue(
                jsonResponse(
                    """
                    [
                      {
                        "id": 1,
                        "event": "reviewed",
                        "actor": { "login": "reviewer", "id": 2 },
                        "body": "LGTM",
                        "state": "APPROVED",
                        "submitted_at": "2026-08-02T00:00:00Z"
                      },
                      {
                        "id": 2,
                        "event": "commented",
                        "actor": { "login": "dev", "id": 3 },
                        "body": "Inline note",
                        "path": "src/Main.kt",
                        "line": 10,
                        "position": 5,
                        "created_at": "2026-08-02T01:00:00Z"
                      },
                      {
                        "id": 3,
                        "event": "committed",
                        "actor": { "login": "octocat", "id": 1 },
                        "sha": "abc123",
                        "message": "WIP",
                        "created_at": "2026-08-02T02:00:00Z"
                      }
                    ]
                    """.trimIndent(),
                ),
            )

            val events = pullRequestApi.listTimeline("octocat", "Hello-World", number = 42)

            assertEquals(3, events.size)
            assertEquals("reviewed", events[0].event)
            assertEquals("APPROVED", events[0].state)
            assertEquals("LGTM", events[0].body)
            assertEquals("commented", events[1].event)
            assertEquals("src/Main.kt", events[1].path)
            assertEquals(10, events[1].line)
            assertEquals("committed", events[2].event)
            assertEquals("abc123", events[2].sha)
            assertEquals("WIP", events[2].message)
            assertEquals("/repos/octocat/Hello-World/issues/42/timeline", server.takeRequest().url.encodedPath)
        }

    @Test
    fun listCheckRuns_parsesStatusConclusionAndOutput() =
        runTest {
            server.enqueue(
                jsonResponse(
                    """
                    {
                      "total_count": 2,
                      "check_runs": [
                        {
                          "id": 10,
                          "name": "CI",
                          "status": "completed",
                          "conclusion": "success",
                          "started_at": "2026-08-02T00:00:00Z",
                          "completed_at": "2026-08-02T00:05:00Z",
                          "output": { "title": "All green", "summary": "3 passed", "text": "details" },
                          "app": { "name": "GitHub Actions" },
                          "html_url": "https://github.com/o/r/actions/runs/10"
                        },
                        {
                          "id": 11,
                          "name": "Lint",
                          "status": "in_progress",
                          "conclusion": null,
                          "output": { "title": "Running", "summary": "", "text": "" },
                          "app": { "name": "GitHub Actions" }
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
            )

            val response = pullRequestApi.listCheckRuns("octocat", "Hello-World", ref = "abc123")

            assertEquals(2, response.totalCount)
            assertEquals(2, response.checkRuns.size)
            val success = response.checkRuns[0]
            assertEquals("CI", success.name)
            assertEquals("completed", success.status)
            assertEquals("success", success.conclusion)
            assertEquals("All green", success.output?.title)
            assertEquals("GitHub Actions", success.app?.name)
            val inProgress = response.checkRuns[1]
            assertEquals("in_progress", inProgress.status)
            assertNull(inProgress.conclusion)
            assertEquals("/repos/octocat/Hello-World/commits/abc123/check-runs", server.takeRequest().url.encodedPath)
        }

    @Test
    fun getCombinedStatus_parsesStateAndCount() =
        runTest {
            server.enqueue(
                jsonResponse(
                    """
                    {
                      "state": "failure",
                      "total_count": 2,
                      "statuses": [
                        { "state": "success", "context": "CI", "description": "ok" },
                        { "state": "failure", "context": "Lint", "description": "failed" }
                      ]
                    }
                    """.trimIndent(),
                ),
            )

            val status = pullRequestApi.getCombinedStatus("octocat", "Hello-World", ref = "abc123")

            assertEquals("failure", status.state)
            assertEquals(2, status.totalCount)
            assertEquals(2, status.statuses.size)
            assertEquals("Lint", status.statuses[1].context)
            assertEquals("/repos/octocat/Hello-World/commits/abc123/status", server.takeRequest().url.encodedPath)
        }

    private fun jsonResponse(body: String): MockResponse =
        MockResponse
            .Builder()
            .body(body)
            .addHeader("Content-Type", "application/json")
            .build()
}
