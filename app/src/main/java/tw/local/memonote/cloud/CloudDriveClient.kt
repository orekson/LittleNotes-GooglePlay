package tw.local.memonote.cloud

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

data class RemoteBackup(val id: String, val name: String, val createdTime: String)

class DriveHttpException(val status: Int, val reason: String = "") :
    IOException(if (reason == "storageQuotaExceeded")
        "Google Drive 儲存空間已滿，請釋放空間後重試"
    else "Google Drive 回應錯誤：" + status + if (reason.isBlank()) "" else " ($reason)")

/** Uses only files created by this app in a visible My Drive folder. */
class CloudDriveClient(
    private val token: String,
    private val baseUrl: String = "https://www.googleapis.com"
) {
    private val folderName = "小小筆記備份"
    private val folderMime = "application/vnd.google-apps.folder"

    fun backups(packageName: String): List<RemoteBackup> {
        val folder = findFolder() ?: return emptyList()
        val prefix = filePrefix(packageName)
        return list("'" + quote(folder.id) + "' in parents and trashed = false")
            .filter { it.name.startsWith(prefix) && it.name.endsWith(".lnbackup") }
            .sortedByDescending { it.createdTime }
    }

    fun upload(packageName: String, encryptedFile: File, retainedVersions: Int? = 3,
               mayPrune: () -> Boolean = { true }): RemoteBackup {
        require(encryptedFile.isFile && encryptedFile.length() > 0L)
        val folder = findFolder() ?: createFolder()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        val name = filePrefix(packageName) + stamp + "-" +
            UUID.randomUUID().toString().take(8) + ".lnbackup"
        val metadata = JSONObject()
            .put("name", name)
            .put("mimeType", "application/octet-stream")
            .put("parents", JSONArray().put(folder.id))
        val start = connection(
            baseUrl + "/upload/drive/v3/files?uploadType=resumable&fields=id,name,createdTime",
            "POST"
        )
        val session: String
        try {
            start.doOutput = true
            start.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            start.setRequestProperty("X-Upload-Content-Type", "application/octet-stream")
            start.setRequestProperty("X-Upload-Content-Length", encryptedFile.length().toString())
            start.outputStream.use { it.write(metadata.toString().toByteArray(Charsets.UTF_8)) }
            requireSuccess(start)
            session = start.getHeaderField("Location")
                ?: throw IOException("Google Drive 未提供上傳位置")
            require(URL(session).protocol == URL(baseUrl).protocol &&
                URL(session).host.equals(URL(baseUrl).host, ignoreCase = true) &&
                URL(session).port == URL(baseUrl).port) {
                "Google Drive 回傳無效上傳位置"
            }
        } finally {
            start.disconnect()
        }
        val put = connection(session, "PUT")
        val uploaded: RemoteBackup
        try {
            put.doOutput = true
            put.setRequestProperty("Content-Type", "application/octet-stream")
            put.setFixedLengthStreamingMode(encryptedFile.length())
            put.outputStream.use { out -> encryptedFile.inputStream().use { it.copyTo(out, 64 * 1024) } }
            requireSuccess(put)
            val result = JSONObject(put.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            uploaded = RemoteBackup(
                result.getString("id"), result.optString("name", name),
                result.optString("createdTime", "")
            )
        } finally {
            put.disconnect()
        }
        // A successful new upload is kept before older versions are removed.
        // Manual backup never removes old files, including after a Pro subscription expires.
        retainedVersions?.let { keep ->
            require(keep > 0)
            if (mayPrune()) runCatching {
                backups(packageName).drop(keep).forEach { if (mayPrune()) delete(it.id) }
            }
        }
        return uploaded
    }

    fun <T> readBackup(id: String, consume: (InputStream) -> T): T {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,256}"))) { "備份檔 ID 無效" }
        val request = connection(
            baseUrl + "/drive/v3/files/" + id + "?alt=media", "GET"
        )
        try {
            requireSuccess(request)
            return request.inputStream.use(consume)
        } finally {
            request.disconnect()
        }
    }

    private fun filePrefix(packageName: String) = "LittleNotes-" + packageName + "-"

    private fun findFolder(): RemoteBackup? = list(
        "name = '" + quote(folderName) + "' and mimeType = '" + folderMime +
            "' and trashed = false"
    ).firstOrNull()

    private fun createFolder(): RemoteBackup {
        val metadata = JSONObject().put("name", folderName)
            .put("mimeType", folderMime).put("parents", JSONArray().put("root"))
        val request = connection(baseUrl + "/drive/v3/files?fields=id,name,createdTime", "POST")
        try {
            request.doOutput = true
            request.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            request.outputStream.use { it.write(metadata.toString().toByteArray(Charsets.UTF_8)) }
            requireSuccess(request)
            val result = JSONObject(request.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            return RemoteBackup(result.getString("id"), result.getString("name"),
                result.optString("createdTime", ""))
        } finally {
            request.disconnect()
        }
    }

    private fun list(query: String): List<RemoteBackup> {
        val files = mutableListOf<RemoteBackup>()
        var next: String? = null
        do {
            val url = baseUrl + "/drive/v3/files?q=" + encode(query) +
                "&fields=" + encode("nextPageToken,files(id,name,createdTime)") +
                "&pageSize=1000&spaces=drive" +
                if (next == null) "" else "&pageToken=" + encode(next)
            val request = connection(url, "GET")
            try {
                requireSuccess(request)
                val response = JSONObject(request.inputStream.bufferedReader(Charsets.UTF_8)
                    .use { it.readText() })
                val items = response.optJSONArray("files") ?: JSONArray()
                for (index in 0 until items.length()) {
                    val item = items.getJSONObject(index)
                    files += RemoteBackup(item.getString("id"), item.getString("name"),
                        item.optString("createdTime", ""))
                }
                next = response.optString("nextPageToken", "").ifBlank { null }
            } finally {
                request.disconnect()
            }
        } while (next != null && files.size < 5000)
        return files
    }

    private fun delete(id: String) {
        val request = connection(baseUrl + "/drive/v3/files/" + id, "DELETE")
        try { requireSuccess(request) } finally { request.disconnect() }
    }

    private fun quote(raw: String) = raw.replace("\\", "\\\\").replace("'", "\\'")
    private fun encode(raw: String) = URLEncoder.encode(raw, "UTF-8")

    private fun connection(url: String, method: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 30_000
            readTimeout = 180_000
            setRequestProperty("Authorization", "Bearer " + token)
        }

    private fun requireSuccess(connection: HttpURLConnection) {
        if (connection.responseCode !in 200..299) {
            val reason = runCatching {
                connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                    val buffer = CharArray(8192)
                    var length = 0
                    while (length < buffer.size) {
                        val count = reader.read(buffer, length, buffer.size - length)
                        if (count < 0) break
                        length += count
                    }
                    JSONObject(String(buffer, 0, length)).optJSONObject("error")
                        ?.optJSONArray("errors")?.optJSONObject(0)?.optString("reason")
                        ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,128}")) }.orEmpty()
                }.orEmpty()
            }.getOrDefault("")
            throw DriveHttpException(connection.responseCode, reason)
        }
    }
}
