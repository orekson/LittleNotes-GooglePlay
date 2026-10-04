package tw.local.memonote

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.local.memonote.cloud.CloudDriveClient
import tw.local.memonote.cloud.DriveHttpException
import java.io.File

class CloudDriveClientTest {
    @Test fun driveFailurePreservesTheServerReasonWithoutCredentials() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(403).setBody(
            """{"error":{"errors":[{"reason":"storageQuotaExceeded"}],"code":403}}"""))
        server.start()
        try {
            try {
                CloudDriveClient("never-log-this-token", server.url("/").toString().trimEnd('/'))
                    .backups("tw.local.memonote.play")
                org.junit.Assert.fail("The failed Drive request must throw")
            } catch (error: DriveHttpException) {
                assertEquals(403, error.status)
                assertEquals("storageQuotaExceeded", error.reason)
                assertEquals(false, error.message.orEmpty().contains("never-log-this-token"))
            }
        } finally { server.shutdown() }
    }

    @Test fun fullDriveStorageIsExplainedAsAStorageProblem() {
        val error = DriveHttpException(403, "storageQuotaExceeded")
        assertEquals("Google Drive 儲存空間已滿，請釋放空間後重試", error.message)
    }

    @Test fun manualBackupsNeverDeleteExistingCloudFiles() = retention(null,true,0)
    @Test fun expiredAutomaticUploadNeverDeletesOldCloudFiles() = retention(3,false,0)
    @Test fun proAutomaticUploadRetainsThreeRecentSnapshots() = retention(3,true,3)
    private fun retention(keep: Int?, allowed: Boolean, expectedDeletes: Int) {
        val server=MockWebServer()
        val deletes=java.util.concurrent.atomic.AtomicInteger()
        server.dispatcher=object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path=request.path!!.substringBefore('?')
                return when {
                    request.method=="DELETE" -> { deletes.incrementAndGet(); MockResponse().setResponseCode(204) }
                    path=="/drive/v3/files" -> {
                        val items=JSONArray()
                        if(request.path!!.contains("mimeType")) items.put(JSONObject().put("id","folder").put("name","小小筆記備份"))
                        else repeat(6) { i -> items.put(JSONObject().put("id","backup-$i")
                            .put("name","LittleNotes-tw.local.memonote-$i.lnbackup").put("createdTime","2026-01-0${i+1}T00:00:00Z")) }
                        MockResponse().setBody(JSONObject().put("files",items).toString())
                    }
                    path=="/upload/drive/v3/files" -> MockResponse().addHeader("Location",server.url("/session"))
                    path=="/session" -> MockResponse().setBody("""{"id":"new"}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        val file=File.createTempFile("retention-",".lnbackup")
        try {
            file.writeText("encrypted backup")
            CloudDriveClient("token",server.url("/").toString().trimEnd('/')).upload("tw.local.memonote",file,keep) { allowed }
            assertEquals(expectedDeletes,deletes.get())
        } finally { file.delete(); server.shutdown() }
    }
    @Test fun createsVisibleFolderUploadsEncryptedFileAndDownloadsTheSameBytes() {
        val bytes = "encrypted-lnbackup-data".toByteArray()
        var folderCreated = false
        var uploaded: ByteArray? = null
        var uploadedName = ""
        var authorizationMissing = false
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.getHeader("Authorization") != "Bearer test-token") {
                    authorizationMissing = true
                }
                val path = request.path?.substringBefore('?')
                return when {
                    path == "/drive/v3/files" && request.method == "GET" -> {
                        val isFolderQuery = request.path?.contains("mimeType") == true
                        val items = JSONArray()
                        if (isFolderQuery && folderCreated) {
                            items.put(JSONObject().put("id", "folder-1")
                                .put("name", "小小筆記備份").put("createdTime", "2026-01-01T00:00:00Z"))
                        }
                        if (!isFolderQuery && uploaded != null) {
                            items.put(JSONObject().put("id", "backup-1")
                                .put("name", uploadedName)
                                .put("createdTime", "2026-01-02T00:00:00Z"))
                        }
                        MockResponse().setBody(JSONObject().put("files", items).toString())
                    }
                    path == "/drive/v3/files" && request.method == "POST" -> {
                        val metadata = JSONObject(request.body.readUtf8())
                        if (metadata.getString("mimeType") !=
                            "application/vnd.google-apps.folder") {
                            return MockResponse().setResponseCode(400)
                        }
                        folderCreated = true
                        MockResponse().setBody("""{"id":"folder-1","name":"小小筆記備份"}""")
                    }
                    path == "/upload/drive/v3/files" && request.method == "POST" -> {
                        val metadata = JSONObject(request.body.readUtf8())
                        uploadedName = metadata.getString("name")
                        if (metadata.getJSONArray("parents").getString(0) != "folder-1" ||
                            request.getHeader("X-Upload-Content-Type") != "application/octet-stream") {
                            return MockResponse().setResponseCode(400)
                        }
                        MockResponse().addHeader(
                            "Location", server.url("/upload/session-1").toString()
                        )
                    }
                    path == "/upload/session-1" && request.method == "PUT" -> {
                        uploaded = request.body.readByteArray()
                        MockResponse().setBody(JSONObject().put("id", "backup-1")
                            .put("name", uploadedName)
                            .put("createdTime", "2026-01-02T00:00:00Z").toString())
                    }
                    path == "/drive/v3/files/backup-1" && request.method == "GET" -> {
                        MockResponse().setBody(Buffer().write(uploaded ?: ByteArray(0)))
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        val local = File.createTempFile("drive-test-", ".lnbackup")
        try {
            local.writeBytes(bytes)
            val client = CloudDriveClient("test-token",
                server.url("/").toString().removeSuffix("/"))
            val remote = client.upload("tw.local.memonote", local)
            assertTrue(folderCreated)
            assertEquals("backup-1", remote.id)
            assertTrue(remote.name.endsWith(".lnbackup"))
            assertArrayEquals(bytes, uploaded)
            assertEquals(listOf(remote.id), client.backups("tw.local.memonote").map { it.id })
            assertArrayEquals(bytes, client.readBackup(remote.id) { it.readBytes() })
            assertEquals(false, authorizationMissing)
        } finally {
            local.delete()
            server.shutdown()
        }
    }
}
