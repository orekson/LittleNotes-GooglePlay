package tw.local.memonote.cloud

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.PersistableBundle
import tw.local.memonote.data.BackupRepository
import java.io.File
import java.io.IOException

/** A persisted, network-constrained job; note writes only enqueue it. */
class CloudBackupJob : JobService() {
    private val running = java.util.concurrent.ConcurrentHashMap<Int,Thread>()

    override fun onStartJob(params: JobParameters): Boolean {
        if (!permitted(this,params.extras.getBoolean("manual"))) return false
        val worker = Thread {
            var password: CharArray? = null
            var encrypted: File? = null
            var retry = false
            try {
                password = CloudBackupState.password(this) ?: throw AuthorizationNeeded()
                val token = CloudAuth.token(this)
                encrypted = File.createTempFile("cloud-backup-", ".lnbackup", cacheDir)
                encrypted.outputStream().use { BackupRepository.write(this, it, password) }
                // Recheck before uploading: an expiry during preparation must stop automatic work.
                if (!params.extras.getBoolean("manual") && !CloudBackupState.enabled(this)) {
                    return@Thread
                }
                CloudDriveClient(token).upload(packageName, encrypted,
                    retainedVersions = if (params.extras.getBoolean("manual")) null else 3,
                    mayPrune = { CloudBackupState.enabled(this) })
                CloudBackupState.markSuccess(this)
            } catch (e: AuthorizationNeeded) {
                CloudBackupState.markProblem(this, "請重新連接 Google 帳號並確認備份密碼")
            } catch (e: DriveHttpException) {
                if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0)
                    android.util.Log.w("LittleNotesCloud", e.message.orEmpty())
                retry = e.status == 401 || e.status == 408 || e.status == 429 || e.status >= 500
                CloudBackupState.markProblem(this,
                    if (e.reason == "storageQuotaExceeded") e.message.orEmpty()
                    else if (e.status == 403) "Google Drive 權限不足，請重新登入"
                    else "Google Drive 備份失敗（" + e.status + "），請稍後重試")
            } catch (e: Exception) {
                retry = e is IOException || e is java.util.concurrent.ExecutionException
                CloudBackupState.markProblem(this, "備份未完成，請確認網路與 Google 帳號狀態")
            } finally {
                running.remove(params.jobId,Thread.currentThread())
                password?.fill('\u0000')
                encrypted?.delete()
                jobFinished(params, retry && permitted(this,params.extras.getBoolean("manual")))
            }
        }
        running[params.jobId] = worker
        worker.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running.remove(params.jobId)?.interrupt()
        return permitted(this,params.extras.getBoolean("manual"))
    }

    companion object {
        private const val JOB_ID = 51340
        private const val MANUAL_JOB_ID = 51341

        fun permitted(context: Context, manual: Boolean) =
            CloudBackupState.connected(context) && (manual || CloudBackupState.enabled(context))

        fun schedule(context: Context, manual: Boolean = false) {
            if (!manual && !CloudBackupState.enabled(context)) {
                cancelAutomatic(context)
                return
            }
            if (!permitted(context,manual)) return
            val scheduler = context.getSystemService(JobScheduler::class.java)
            val extras = PersistableBundle().apply { putBoolean("manual", manual) }
            val job = JobInfo.Builder(if (manual) MANUAL_JOB_ID else JOB_ID, ComponentName(context.applicationInfo.packageName, CloudBackupJob::class.java.name))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setMinimumLatency(if (manual) 0L else 15_000L)
                .setBackoffCriteria(30_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .setExtras(extras)
                .build()
            if (scheduler.schedule(job) != JobScheduler.RESULT_SUCCESS) {
                CloudBackupState.markProblem(context, "系統無法排入自動備份")
            }
        }

        fun cancelAutomatic(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            // Older versions used JOB_ID for manual jobs too: preserve a queued manual request.
            if (scheduler.getPendingJob(JOB_ID)?.extras?.getBoolean("manual") != true)
                scheduler.cancel(JOB_ID)
        }
        fun cancel(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            scheduler.cancel(JOB_ID)
            scheduler.cancel(MANUAL_JOB_ID)
        }
    }
}
