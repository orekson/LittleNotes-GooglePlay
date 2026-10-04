package tw.local.memonote

import android.accounts.Account
import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.SystemClock
import android.view.*
import android.view.inspector.WindowInspector
import android.widget.*
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.Status
import com.google.android.gms.common.internal.safeparcel.SafeParcelableSerializer
import org.junit.Assert.*
import org.junit.Test
import tw.local.memonote.cloud.CloudBackupState
import tw.local.memonote.ui.AppLanguage

@android.annotation.TargetApi(29)
@androidx.test.filters.SdkSuppress(minSdkVersion=29)
class CloudAndNavigationRegressionTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private fun views(v: View): List<View> = listOf(v) + if(v is ViewGroup)
        (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun dialogs()=WindowInspector.getGlobalWindowViews().filter {
        (it.layoutParams as? WindowManager.LayoutParams)?.type==WindowManager.LayoutParams.TYPE_APPLICATION
    }.flatMap { views(it) }
    private fun result(activity: CloudProfileActivity, data: Intent?) {
        CloudProfileActivity::class.java.getDeclaredMethod("onActivityResult",Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,Intent::class.java).apply { isAccessible=true }
            .invoke(activity,503,Activity.RESULT_CANCELED,data)
    }
    @Suppress("DEPRECATION")
    @Test fun successfulSdkPayloadIsParsedEvenWhenActivityCodeIsCanceled() {
        val lang=AppLanguage.code(context);AppLanguage.set(context,"zh-TW")
        val connected=CloudBackupState.connected(context)
        try {
            ActivityScenario.launch<CloudProfileActivity>(Intent(context,CloudProfileActivity::class.java)).use { scenario ->
                scenario.onActivity { activity ->
                    val data=Intent()
                    SafeParcelableSerializer.serializeToIntentExtra(Status.RESULT_SUCCESS,data,"status")
                    SafeParcelableSerializer.serializeToIntentExtra(AuthorizationResult(null,"fixture-token-never-sent",null,
                        listOf(CloudBackupState.DRIVE_SCOPE),GoogleSignInAccount.fromAccount(Account("fixture@example.invalid","com.google")),null),
                        data,"authorization_result")
                    result(activity,data)
                    assertNotNull("Successful Google data must continue to password setup",dialogs().filterIsInstance<TextView>().firstOrNull {
                        it.isShown && it.text.toString()=="設定雲端備份密碼"
                    })
                }
            }
            assertEquals(connected,CloudBackupState.connected(context))
        } finally { AppLanguage.set(context,lang) }
    }
    @Test fun oauthConfigurationFailureIsExplainedInsteadOfReportedAsUserCancellation() {
        val lang=AppLanguage.code(context);AppLanguage.set(context,"zh-TW")
        val connected=CloudBackupState.connected(context)
        try {
            ActivityScenario.launch<CloudProfileActivity>(Intent(context,CloudProfileActivity::class.java)).use { scenario ->
                scenario.onActivity { activity ->
                    val data=Intent();SafeParcelableSerializer.serializeToIntentExtra(Status(10),data,"status")
                    result(activity,data)
                    assertNotNull("OAuth setup error must be distinguished from cancellation",dialogs().filterIsInstance<TextView>().firstOrNull {
                        it.isShown && it.text.toString()=="Google 登入設定尚未完成"
                    })
                }
            }
            assertEquals(connected,CloudBackupState.connected(context))
        } finally { AppLanguage.set(context,lang) }
    }
    @Test fun tabsSwitchWithoutActivityAnimationAndReturnToTheSameNotesScreen() {
        val requests=mutableListOf<Intent>()
        val monitor=object: Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                requests.add(Intent(intent));return null
            }
        }
        instrumentation.addMonitor(monitor)
        val lang=AppLanguage.code(context);AppLanguage.set(context,"zh-TW")
        try {
            ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java)).use { scenario ->
                var original: MainActivity?=null
                scenario.onActivity { activity ->
                    original=activity
                    views(activity.window.decorView).filterIsInstance<Button>().first { it.contentDescription=="日期頁" }.performClick()
                    assertTrue("Tab navigation must disable transition animations",requests.last().flags and Intent.FLAG_ACTIVITY_NO_ANIMATION !=0)
                }
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync {
                    val page=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<DateScheduleActivity>().single()
                    views(page.window.decorView).filterIsInstance<Button>().first { it.contentDescription=="個人與設定頁" }.performClick()
                }
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync {
                    val page=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<CloudProfileActivity>().single()
                    views(page.window.decorView).filterIsInstance<Button>().first { it.contentDescription=="筆記頁" }.performClick()
                }
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync {
                    assertSame(original,ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single())
                    val tabs=requests.filter { it.component?.className in listOf(DateScheduleActivity::class.java.name,CloudProfileActivity::class.java.name) }
                    assertEquals(2,tabs.size)
                    tabs.forEach { assertTrue("Tab navigation must disable transition animations",it.flags and Intent.FLAG_ACTIVITY_NO_ANIMATION !=0) }
                }
            }
        } finally { instrumentation.removeMonitor(monitor);AppLanguage.set(context,lang) }
    }
    @Test fun pressedTabShrinksAndCanceledTouchReboundsWithoutNavigating() {
        val lang=AppLanguage.code(context);AppLanguage.set(context,"zh-TW")
        try {
            ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java)).use { scenario ->
                var button: Button?=null
                scenario.onActivity { activity ->
                    button=views(activity.window.decorView).filterIsInstance<Button>().first { it.contentDescription=="日期頁" }
                    val time=SystemClock.uptimeMillis()
                    button!!.dispatchTouchEvent(MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,button!!.width/2f,button!!.height/2f,0))
                }
                Thread.sleep(160)
                scenario.onActivity {
                    assertTrue("Touch down must visibly press the tab",button!!.scaleX<.99f)
                    val time=SystemClock.uptimeMillis()
                    button!!.dispatchTouchEvent(MotionEvent.obtain(time,time,MotionEvent.ACTION_CANCEL,0f,0f,0))
                }
                Thread.sleep(300)
                scenario.onActivity { activity ->
                    assertEquals(1f,button!!.scaleX,.01f);assertEquals(1f,button!!.scaleY,.01f)
                    assertTrue(activity is MainActivity);assertFalse(activity.isFinishing)
                }
            }
        } finally { AppLanguage.set(context,lang) }
    }
}
