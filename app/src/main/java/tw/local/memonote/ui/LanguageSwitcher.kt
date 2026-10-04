package tw.local.memonote.ui

import android.app.AlertDialog
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import tw.local.memonote.R
import tw.local.memonote.widget.NoteWidgetProvider

object LanguageSwitcher {
    fun add(activity: LocalizedActivity, row: LinearLayout) {
        val button = ImageButton(activity).apply {
            setImageResource(R.drawable.language_switch)
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = Ui.rounded(0xffffffff.toInt(), 12f)
            contentDescription = AppLanguage.text(activity, "切換語言")
            setPadding(Ui.dp(activity, 3), Ui.dp(activity, 3),
                Ui.dp(activity, 3), Ui.dp(activity, 3))
            setOnClickListener {
                val selected = AppLanguage.codes.indexOf(AppLanguage.code(activity))
                AlertDialog.Builder(activity)
                    .setTitle(AppLanguage.text(activity, "選擇語言"))
                    .setSingleChoiceItems(AppLanguage.names, selected) { dialog, index ->
                        dialog.dismiss()
                        val next = AppLanguage.codes[index]
                        if (next != AppLanguage.code(activity)) {
                            AppLanguage.set(activity, next)
                            NoteWidgetProvider.updateAll(activity)
                            activity.recreate()
                        }
                    }
                    .setNegativeButton(AppLanguage.text(activity, "取消"), null)
                    .show()
            }
        }
        row.addView(button, LinearLayout.LayoutParams(
            Ui.dp(activity, 52), Ui.dp(activity, 48)))
    }
}