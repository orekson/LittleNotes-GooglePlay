package tw.local.memonote

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test
import tw.local.memonote.cloud.CloudBackupJob

/** Checks the shipped app's permission/service boundary on Android 14+. */
@SdkSuppress(minSdkVersion = 34)
class CloudJobPermissionTest {
    @Test fun appCanScheduleItsNetworkConstrainedBackupService() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scheduler = context.getSystemService(JobScheduler::class.java)
        val testJobId = 51349
        val job = JobInfo.Builder(testJobId, ComponentName(context, CloudBackupJob::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            // This test verifies scheduling without starting an upload or touching account state.
            .setMinimumLatency(86_400_000L)
            .build()
        try {
            val result = try { scheduler.schedule(job) }
            catch (error: SecurityException) {
                fail("App must declare the permission needed for network backup jobs: ${error.message}")
                return
            }
            assertEquals(JobScheduler.RESULT_SUCCESS, result)
            assertNotNull(scheduler.getPendingJob(testJobId))
        } finally {
            scheduler.cancel(testJobId)
        }
    }
}
