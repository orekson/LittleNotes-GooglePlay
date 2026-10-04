package tw.local.memonote

import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import tw.local.memonote.ui.LocalizedActivity
import tw.local.memonote.ui.LanguageSwitcher
import tw.local.memonote.ui.localizedDisplayTitle
import tw.local.memonote.ui.AppLanguage
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import tw.local.memonote.data.BackupRepository
import tw.local.memonote.data.Note
import tw.local.memonote.data.NoteStore
import tw.local.memonote.data.VaultRepository
import tw.local.memonote.ui.BottomNavigation
import tw.local.memonote.ui.PasswordDialogs
import tw.local.memonote.ui.Ui
import tw.local.memonote.ui.ProUi
import tw.local.memonote.entitlement.PremiumFeature
import tw.local.memonote.widget.NoteWidgetProvider
import tw.local.memonote.widget.WidgetAccess
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : LocalizedActivity() {
    private companion object {
        const val CREATE_BACKUP = 501
        const val OPEN_BACKUP = 502
    }

    private var pendingBackupPassword: CharArray? = null

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState) }
    override fun onResume() { super.onResume(); tw.local.memonote.widget.DateWidgetSchedule.safeRefresh(this); tw.local.memonote.reminder.ReminderScheduler.safeSync(this); showNotes() }

    private fun showNotes() {
        val root = Ui.root(this)
        val header = Ui.column(this)
        Ui.pad(header, 24)
        header.addView(Ui.label(this, "✦  筆記／我的小天地", 11f, Ui.purple, true))
        header.addView(Ui.space(this, 8))
        val titleRow = Ui.row(this)
        titleRow.addView(Ui.label(this, getString(R.string.app_name), 32f, bold = true),
            LinearLayout.LayoutParams(0, -2, 1f))
        LanguageSwitcher.add(this, titleRow)
        header.addView(titleRow)
        header.addView(Ui.space(this, 6))
        header.addView(Ui.label(this, "把日常寫下，讓喜歡的陪在桌面。", 14f, Ui.muted))
        header.addView(Ui.space(this, 12))
        header.addView(Ui.button(this, "＋  寫一篇新筆記", true) {
            startActivity(Intent(this, EditorActivity::class.java))
        })
        root.addView(header)

        val scroll = ScrollView(this)
        val list = Ui.column(this)
        list.setPadding(Ui.dp(this, 24), 0, Ui.dp(this, 24), Ui.dp(this, 24))
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val notes = try { NoteStore(this).use { it.all() } } catch (e: Exception) {
            Ui.toast(this, "筆記讀取失敗，請重新開啟重試")
            emptyList()
        }
        list.addView(Ui.label(this, AppLanguage.format(this, "我的筆記  ·  %1\$d", notes.size), 13f, Ui.muted, true))
        list.addView(Ui.space(this, 12))
        if (notes.isEmpty()) {
            val empty = Ui.column(this)
            Ui.pad(empty, 24)
            empty.background = Ui.rounded(0xfff0e9fa.toInt(), 28f)
            empty.addView(Ui.label(this, "✧", 40f, Ui.purple))
            empty.addView(Ui.label(this, "這一頁，留給你", 21f, bold = true))
            empty.addView(Ui.space(this, 8))
            empty.addView(Ui.label(this, "寫下今天的小事，插入喜歡的小人貼圖，再放到桌面隨時看見。", 15f, Ui.muted))
            list.addView(empty)
        }
        val date = SimpleDateFormat("MM/dd  HH:mm", AppLanguage.locale(this))
        notes.forEach { note ->
            val card = Ui.column(this)
            Ui.pad(card, 20)
            card.background = Ui.rounded(0xffffffff.toInt(), 24f)
            card.elevation = Ui.dp(this, 1).toFloat()
            card.addView(Ui.rawLabel(this, note.localizedDisplayTitle(this), 20f, bold = true))
            if (!note.isLocked && note.category.isNotBlank()) {
                card.addView(Ui.rawLabel(this, AppLanguage.format(this, "分類：%1\$s", note.category), 12f, Ui.purple))
            }
            card.addView(Ui.space(this, 7))
            val excerpt = if (note.isLocked) {
                "輸入密碼後才能查看內容"
            } else {
                note.body.replace("\uFFFC", " ✿ ").take(120).ifBlank { "只有標題，也是一篇筆記。" }
            }
            card.addView(Ui.rawLabel(this, if (note.isLocked) AppLanguage.text(this, excerpt) else if (note.body.isBlank()) AppLanguage.text(this, excerpt) else excerpt, 15f, Ui.muted).apply { maxLines = 3 })
            card.addView(Ui.space(this, 12))
            card.addView(Ui.label(
                this,
                date.format(Date(note.updated)) + AppLanguage.text(this, if (note.isLocked) "   ·   點一下輸入密碼" else "   ·   點一下繼續編輯"),
                11f,
                Ui.purple
            ))
            card.setOnClickListener {
                startActivity(Intent(this, EditorActivity::class.java).putExtra("noteId", note.id))
            }
            card.setOnLongClickListener { showNoteMenu(note); true }
            list.addView(card)
            list.addView(Ui.space(this, 12))
        }
        list.addView(Ui.space(this, 12))
        list.addView(Ui.button(this, "✧  加到桌面小工具") { addWidget() })
        list.addView(Ui.space(this, 10))
        list.addView(Ui.button(this, "備份筆記到檔案") { chooseBackup() })
        list.addView(Ui.button(this, "從備份檔還原") { chooseRestore() })
        list.addView(ProUi.button(this,"回收桶",PremiumFeature.VERSION_HISTORY) {
            startActivity(Intent(this,HistoryActivity::class.java))
        })
        list.addView(Ui.label(this, "可在系統檔案選擇器選擇手機或已連接的 Google Drive 等儲存空間。", 12f, Ui.muted))
        list.addView(Ui.space(this, 10))
        list.addView(Ui.button(this, "貼圖來源與使用說明") {
            AlertDialog.Builder(this).setTitle(getString(R.string.sticker_source_title))
                .setMessage(getString(R.string.sticker_source_message))
                .setPositiveButton(AppLanguage.text(this, "知道了"), null)
                .setNeutralButton(getString(R.string.sticker_source_action)) { _, _ ->
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.sticker_source_url))))
                }.show()
        })
        BottomNavigation.add(this, root, BottomNavigation.Tab.NOTES)
    }

    private fun showNoteMenu(note: Note) {
        val first = if (note.isLocked) "解除加密" else "加密筆記"
        AlertDialog.Builder(this).setTitle(note.localizedDisplayTitle(this))
            .setItems(arrayOf(AppLanguage.text(this, first), AppLanguage.text(this, "刪除筆記"),ProUi.label(this,"版本紀錄",PremiumFeature.VERSION_HISTORY))) { _, which ->
                when (which) {
                    0 -> if (note.isLocked) removeLock(note) else lock(note)
                    1 -> delete(note)
                    2 -> startActivity(Intent(this,HistoryActivity::class.java).putExtra("noteId",note.id))
                }
            }.show()
    }

    private fun lock(note: Note) {
        PasswordDialogs.ask(
            this, "設定這篇筆記的密碼",
            "標題、內容、分類和附件會一起加密。忘記密碼就無法解鎖或還原這篇筆記，請妥善保存。",
            true
        ) { password ->
            work("正在加密筆記", {
                try { VaultRepository.lock(this, note.id, password) }
                finally { password.fill('\u0000') }
            }) { result ->
                result.onSuccess { NoteWidgetProvider.updateAll(this); showNotes(); Ui.toast(this, "筆記已加密") }
                    .onFailure { Ui.toast(this, AppLanguage.format(this, "加密失敗：%1\$s", AppLanguage.text(this, it.message ?: "請重試"))) }
            }
        }
    }

    private fun removeLock(note: Note) {
        PasswordDialogs.ask(
            this, "解除筆記加密", "解除後，筆記與附件會以一般方式保存在這台裝置。",
            false
        ) { password ->
            work("正在解除加密", {
                try { VaultRepository.removePassword(this, note, password) }
                finally { password.fill('\u0000') }
            }) { result ->
                result.onSuccess { NoteWidgetProvider.updateAll(this); showNotes(); Ui.toast(this, "已解除加密") }
                    .onFailure { Ui.toast(this, AppLanguage.format(this, "無法解除加密：%1\$s", AppLanguage.text(this, it.message ?: "密碼錯誤"))) }
            }
        }
    }

    private fun delete(note: Note) {
        AlertDialog.Builder(this).setTitle(AppLanguage.text(this, "刪除這篇筆記？"))
            .setMessage(AppLanguage.text(this, if(tw.local.memonote.entitlement.EntitlementManager.snapshot(this).isPro)
                "筆記會放入回收桶，保留 30 天；桌面小工具會暫停顯示。" else "Free 刪除後無法復原。Pro 可將刪除的筆記保留在回收桶 30 天。"))
            .setNegativeButton(AppLanguage.text(this, "保留"), null)
            .setPositiveButton(AppLanguage.text(this, "刪除")) { _, _ ->
                if (note.isLocked) {
                    PasswordDialogs.ask(this, "輸入密碼以刪除", "加密筆記需要密碼才能刪除。", false) { password ->
                        work("正在刪除筆記", {
                            try {
                                VaultRepository.verify(note, password)
                                NoteStore(this).use { it.delete(note.id) }
                            } finally { password.fill('\u0000') }
                        }) { result ->
                            result.onSuccess { NoteWidgetProvider.updateAll(this); showNotes() }
                                .onFailure { Ui.toast(this, AppLanguage.format(this, "刪除失敗：%1\$s", AppLanguage.text(this, it.message ?: "密碼錯誤"))) }
                        }
                    }
                } else {
                    try {
                        NoteStore(this).use { it.delete(note.id) }
                        NoteWidgetProvider.updateAll(this)
                        showNotes()
                    } catch (e: Exception) {
                        Ui.toast(this, "刪除失敗，請重試")
                    }
                }
            }.show()
    }

    private fun chooseBackup() {
        PasswordDialogs.ask(
            this, "設定備份密碼",
            "備份檔會加密所有筆記和附件。忘記這個密碼，就無法從備份還原；請與單篇筆記密碼分別保存。",
            true
        ) { password ->
            pendingBackupPassword?.fill('\u0000')
            pendingBackupPassword = password
            val name = "LittleNotes-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".lnbackup"
            try {
                startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/octet-stream"
                    putExtra(Intent.EXTRA_TITLE, name)
                }, CREATE_BACKUP)
            } catch (e: Exception) {
                pendingBackupPassword?.fill('\u0000')
                pendingBackupPassword = null
                Ui.toast(this, "無法開啟檔案選擇器")
            }
        }
    }

    private fun chooseRestore() {
        AlertDialog.Builder(this).setTitle(AppLanguage.text(this, "從備份還原"))
            .setMessage(AppLanguage.text(this, "備份中的筆記會新增到這台裝置，現有筆記不會被覆蓋。重複還原會新增重複筆記。"))
            .setNegativeButton(AppLanguage.text(this, "取消"), null)
            .setPositiveButton(AppLanguage.text(this, "選擇備份檔")) { _, _ ->
                try {
                    startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }, OPEN_BACKUP)
                } catch (e: Exception) {
                    Ui.toast(this, "無法開啟檔案選擇器")
                }
            }.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CREATE_BACKUP) {
            val password = pendingBackupPassword
            pendingBackupPassword = null
            if (resultCode != RESULT_OK || data?.data == null) {
                password?.fill('\u0000')
                return
            }
            val uri = data.data!!
            if (password == null) {
                PasswordDialogs.ask(this, "重新設定備份密碼", "請設定至少 5 個字元。", true) {
                    performBackup(uri, it)
                }
            } else {
                performBackup(uri, password)
            }
        } else if (requestCode == OPEN_BACKUP && resultCode == RESULT_OK && data?.data != null) {
            val uri = data.data!!
            PasswordDialogs.ask(this, "輸入備份密碼", "密碼錯誤或檔案損壞時，現有筆記不會改動。", false) {
                performRestore(uri, it)
            }
        }
    }

    private fun performBackup(uri: Uri, password: CharArray) {
        work("正在建立備份", {
            try {
                contentResolver.openOutputStream(uri)?.use { BackupRepository.write(this, it, password) }
                    ?: error("無法寫入選擇的檔案")
            } finally { password.fill('\u0000') }
        }) { result ->
            result.onSuccess { Ui.toast(this, AppLanguage.format(this, "已備份 %1\$d 篇筆記", it)) }
                .onFailure { Ui.toast(this, AppLanguage.format(this, "備份失敗：%1\$s", AppLanguage.text(this, it.message ?: "請重試"))) }
        }
    }

    private fun performRestore(uri: Uri, password: CharArray) {
        work("正在驗證並還原備份", {
            try {
                contentResolver.openInputStream(uri)?.use { BackupRepository.restore(this, it, password) }
                    ?: error("無法讀取備份檔")
            } finally { password.fill('\u0000') }
        }) { result ->
            result.onSuccess { showNotes(); Ui.toast(this, AppLanguage.format(this, "已還原 %1\$d 篇筆記", it)) }
                .onFailure { Ui.toast(this, AppLanguage.format(this, "還原失敗：%1\$s", AppLanguage.text(this, it.message ?: "密碼錯誤或檔案損壞"))) }
        }
    }

    private fun <T> work(label: String, action: () -> T, finish: (Result<T>) -> Unit) {
        val progress = AlertDialog.Builder(this).setTitle(AppLanguage.text(this, label))
            .setView(ProgressBar(this)).setCancelable(false).create()
        progress.show()
        Thread {
            val result = runCatching(action)
            runOnUiThread {
                progress.dismiss()
                if (!isDestroyed) finish(result)
            }
        }.start()
    }

    private fun addWidget() {
        if (!WidgetAccess.canRequestNew(this)) {
            ProUi.show(this, PremiumFeature.UNLIMITED_WIDGETS)
            return
        }
        val manager = AppWidgetManager.getInstance(this)
        if (manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(ComponentName(this, NoteWidgetProvider::class.java), null, null)
        } else {
            AlertDialog.Builder(this).setTitle(AppLanguage.text(this, "加入桌面"))
                .setMessage(AppLanguage.text(this, "長按桌面空白處 → 小工具 → 小小筆記。放到空白桌面頁，再長按調整大小。"))
                .setPositiveButton(AppLanguage.text(this, "知道了"), null).show()
        }
    }

    override fun onDestroy() {
        pendingBackupPassword?.fill('\u0000')
        pendingBackupPassword = null
        super.onDestroy()
    }
}
