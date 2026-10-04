package tw.local.memonote

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import tw.local.memonote.data.Note
import tw.local.memonote.ui.AppLanguage
import tw.local.memonote.ui.localizedDisplayTitle

@RunWith(AndroidJUnit4::class)
class AppLanguageTest {
    @Test fun languagePersistsAndTranslatesBothCommonAndFlavorText() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = AppLanguage.code(context)
        try {
            AppLanguage.set(context, "zh-TW")
            val flavorTitle = AppLanguage.wrap(context).getString(R.string.sticker_picker_title)
            val samples = mapOf(
                "en" to "Cancel",
                "ja" to "キャンセル",
                "ko" to "취소",
                "es" to "Cancelar"
            )
            val titles = mapOf("en" to "Little Notes", "ja" to "ちいさなノート",
                "ko" to "작은 노트", "es" to "Pequeñas notas")
            samples.forEach { (language, expected) ->
                AppLanguage.set(context, language)
                assertEquals(language, AppLanguage.code(context))
                assertEquals(expected, AppLanguage.text(context, "取消"))
                assertEquals(titles[language],
                    AppLanguage.wrap(context).getString(R.string.app_name))
                assertNotEquals("▦  日期", AppLanguage.text(context, "▦  日期"))
                assertEquals("取消", Note(title = "取消").localizedDisplayTitle(context))
                assertNotEquals("未命名筆記", Note().localizedDisplayTitle(context))
                assertNotEquals(flavorTitle,
                    AppLanguage.wrap(context).getString(R.string.sticker_picker_title))
            }
            AppLanguage.set(context, "zh-TW")
            assertEquals("取消", AppLanguage.text(context, "取消"))
        } finally {
            AppLanguage.set(context, original)
        }
    }
}