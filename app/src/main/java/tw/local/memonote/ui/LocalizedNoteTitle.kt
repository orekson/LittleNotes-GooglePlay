package tw.local.memonote.ui

import android.content.Context
import tw.local.memonote.data.Note

fun Note.localizedDisplayTitle(context: Context): String =
    if (isLocked) AppLanguage.text(context, "🔒 加密筆記")
    else title.trim().ifEmpty { AppLanguage.text(context, "未命名筆記") }