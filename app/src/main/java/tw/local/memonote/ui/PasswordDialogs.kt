package tw.local.memonote.ui

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout

object PasswordDialogs {
    private fun input(activity: Activity, hint: String): EditText = EditText(activity).apply {
        this.hint = AppLanguage.text(activity, hint)
        isSingleLine = true
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        filters = arrayOf(android.text.InputFilter.LengthFilter(128))
    }

    fun ask(
        activity: Activity,
        title: String,
        warning: String,
        confirm: Boolean,
        onPassword: (CharArray) -> Unit
    ) {
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val padding = Ui.dp(activity, 24)
            setPadding(padding, 0, padding, 0)
        }
        val first = input(activity, "密碼")
        container.addView(first)
        val second = if (confirm) input(activity, "再次輸入密碼").also { container.addView(it) } else null
        val dialog = AlertDialog.Builder(activity)
            .setTitle(AppLanguage.text(activity, title))
            .setMessage(AppLanguage.text(activity, warning))
            .setView(container)
            .setNegativeButton(AppLanguage.text(activity, "取消"), null)
            .setPositiveButton(AppLanguage.text(activity, "確定"), null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val password = first.text.toString().toCharArray()
                val minimum = if (confirm) 5 else 1
                if (password.size < minimum) {
                    first.error = AppLanguage.text(activity, if (confirm) "請設定至少 5 個字元" else "請輸入密碼")
                    password.fill('\u0000')
                    return@setOnClickListener
                }
                if (second != null && !password.contentEquals(second.text.toString().toCharArray())) {
                    second.error = AppLanguage.text(activity, "兩次密碼不同")
                    password.fill('\u0000')
                    return@setOnClickListener
                }
                first.text.clear()
                second?.text?.clear()
                dialog.dismiss()
                onPassword(password)
            }
        }
        dialog.show()
    }
}
