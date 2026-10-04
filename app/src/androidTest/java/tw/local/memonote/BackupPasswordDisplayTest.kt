package tw.local.memonote

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import tw.local.memonote.ui.PasswordDialogs

@SdkSuppress(minSdkVersion = 29)
class BackupPasswordDisplayTest {
    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()

    @Test fun backupPasswordFieldsDoNotDisplayTheTypedPassword() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch<CloudProfileActivity>(Intent(context, CloudProfileActivity::class.java)).use { scenario ->
            scenario.onActivity { activity ->
                PasswordDialogs.ask(activity, "設定雲端備份密碼", "test", true) {}
                val fields = WindowInspector.getGlobalWindowViews().flatMap(::views)
                    .filterIsInstance<EditText>().filter { it.isShown }
                assertEquals(2, fields.size)
                for (field in fields) {
                    val sample = "test-password"
                    field.setText(sample)
                    val displayed = field.transformationMethod?.getTransformation(field.text, field)?.toString()
                        ?: field.text.toString()
                    assertNotEquals("Backup password must be masked on screen", sample, displayed)
                }
            }
        }
    }
}
