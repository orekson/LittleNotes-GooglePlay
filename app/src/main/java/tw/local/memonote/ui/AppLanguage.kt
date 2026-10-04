package tw.local.memonote.ui

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/** App language is independent of Android's system language and of note data. */
object AppLanguage {
    private const val PREFS = "appearance"
    private const val KEY = "language"
    private const val DEFAULT = "zh-TW"
    val codes = listOf(DEFAULT, "en", "ja", "ko", "es")
    val names = arrayOf("繁體中文", "English", "日本語", "한국어", "Español")
    @Volatile private var catalog: Map<String, List<String>>? = null

    fun code(context: Context): String {
        val value = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, DEFAULT) ?: DEFAULT
        return if (value in codes) value else DEFAULT
    }

    fun set(context: Context, value: String) {
        require(value in codes)
        check(context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, value).commit()) { "Unable to save language" }
    }

    fun locale(context: Context): Locale = Locale.forLanguageTag(code(context))

    fun wrap(context: Context): Context {
        val selected = locale(context)
        val config = Configuration(context.resources.configuration)
        config.setLocale(selected)
        config.setLocales(LocaleList(selected))
        config.setLayoutDirection(selected)
        return context.createConfigurationContext(config)
    }

    fun text(context: Context, source: String): String {
        val index = codes.indexOf(code(context)) - 1
        if (index < 0) return source
        val map = catalog ?: synchronized(this) {
            catalog ?: load(context).also { catalog = it }
        }
        return map[source]?.getOrNull(index)?.ifBlank { source } ?: source
    }

    private fun load(context: Context): Map<String, List<String>> =
        context.applicationContext.assets.open("i18n/catalog.txt")
            .bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.filter { it.isNotBlank() && !it.startsWith("#") }
                    .map { line ->
                        val columns = line.split('|', limit = 5)
                        require(columns.size == 5) { "Invalid language catalog" }
                        columns.map { it.replace("\\n", "\n") }
                    }.associate { it[0] to it.drop(1) }
            }

    fun format(context: Context, source: String, vararg args: Any): String =
        String.format(locale(context), text(context, source), *args)
}