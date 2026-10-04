package tw.local.memonote

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.*
import tw.local.memonote.data.*
import tw.local.memonote.entitlement.*
import tw.local.memonote.rich.RichText
import tw.local.memonote.ui.*
import tw.local.memonote.widget.NoteWidgetProvider
import java.text.SimpleDateFormat
import java.util.*

class HistoryActivity: LocalizedActivity() {
    private val noteId get()=intent.getLongExtra("noteId",0)
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState) }
    override fun onResume() { super.onResume(); showPage() }
    private fun date(time: Long)=SimpleDateFormat("yyyy/MM/dd HH:mm:ss",AppLanguage.locale(this)).format(Date(time))
    private fun showPage() {
        val root=Ui.root(this); val column=Ui.column(this).apply { Ui.pad(this,20) }
        root.addView(ScrollView(this).apply { addView(column) })
        column.addView(Ui.button(this,"返回") { finish() })
        column.addView(Ui.label(this,if(noteId>0) "筆記版本紀錄 · Pro" else "回收桶 · Pro",25f,bold=true))
        if(!EntitlementManager.allows(this,PremiumFeature.VERSION_HISTORY,FeatureOperation.VIEW)) {
            column.addView(Ui.label(this,"Pro 可查看及還原版本紀錄與回收桶。切回 Free 時，已保留的資料會依原期限保存。",15f))
            column.addView(Ui.button(this,"查看 Pro") { ProUi.openPage(this) }); return
        }
        try {
            NoteStore(this).use { store ->
                if(noteId>0) {
                    column.addView(Ui.label(this,"每次儲存內容變更時保留上一版，最多 50 版。還原前會先保存目前版本。",14f,Ui.muted))
                    store.find(noteId)?.let { current -> column.addView(Ui.rawLabel(this,"目前版本：${current.localizedDisplayTitle(this)}\n${date(current.updated)}",15f)) }
                    val versions=store.versions(noteId)
                    if(versions.isEmpty()) column.addView(Ui.label(this,"還沒有舊版本。使用 Pro 修改並儲存筆記後，先前內容會出現在這裡。",15f))
                    versions.forEach { version ->
                        column.addView(Ui.rawButton(this,"${date(version.savedAt)}\n${version.note.localizedDisplayTitle(this)}") {
                            preview(version.note,"還原這個版本") { password ->
                                val secret=password?.copyOf()
                                restore {
                                    try {
                                        if(secret!=null) VaultRepository.restoreVersion(this,noteId,version.versionId,secret)
                                        else it.restoreVersion(noteId,version.versionId)
                                        noteId
                                    } finally { secret?.fill('\u0000') }
                                }
                            }
                        })
                    }
                } else {
                    column.addView(Ui.label(this,"Pro 刪除的筆記保留 30 天。到期或永久刪除後無法復原；升級前已永久刪除的筆記不會出現在這裡。",14f,Ui.muted))
                    val trash=store.trash()
                    if(trash.isEmpty()) column.addView(Ui.label(this,"回收桶是空的。",16f))
                    trash.forEach { item ->
                        val card=Ui.column(this).apply { Ui.pad(this,12); background=Ui.rounded(0xffffffff.toInt(),18f) }
                        card.addView(Ui.rawLabel(this,item.note.localizedDisplayTitle(this),18f,bold=true))
                        card.addView(Ui.rawLabel(this,"刪除：${date(item.deletedAt)}\n保留至：${date(item.expiresAt)}",12f,Ui.muted))
                        card.addView(Ui.button(this,"查看並還原") { preview(item.note,"還原筆記") { restore { it.restoreTrash(item.noteId) } } })
                        card.addView(Ui.button(this,"永久刪除") {
                            AlertDialog.Builder(this).setTitle("永久刪除？").setMessage("筆記與版本紀錄將無法復原。")
                                .setNegativeButton("保留",null).setPositiveButton("永久刪除") { _,_ ->
                                    runCatching { NoteStore(this).use { it.purgeTrash(item.noteId) } }
                                        .onSuccess { showPage() }.onFailure { Ui.toast(this,"刪除失敗，請重試") }
                                }.show()
                        })
                        column.addView(card); column.addView(Ui.space(this,10))
                    }
                }
            }
        } catch(e: ProRequiredException) { ProUi.show(this,e.feature) }
        catch(e: Exception) { Ui.toast(this,"紀錄讀取失敗，請重試") }
    }
    private fun preview(note: Note,action: String,restore: (CharArray?)->Unit) {
        if(!ProUi.require(this,PremiumFeature.VERSION_HISTORY,FeatureOperation.VIEW)) return
        if(!note.isLocked) { previewPlain(note,action,null,null,restore); return }
        PasswordDialogs.ask(this,"輸入此版本的密碼","請使用儲存當時的密碼。版本還原沿用目前筆記的加密狀態；回收桶還原保持原加密。",false) { password ->
            val session=UUID.randomUUID().toString()
            val progress=AlertDialog.Builder(this).setTitle("正在解鎖版本").setView(ProgressBar(this)).setCancelable(false).show()
            Thread {
                val result=runCatching { VaultRepository.open(this,note,password,session) }
                runOnUiThread {
                    progress.dismiss()
                    if(isFinishing || isDestroyed) { VaultMedia.clear(session); password.fill('\u0000'); return@runOnUiThread }
                    result.onSuccess {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        previewPlain(it,action,session,password,restore)
                    }.onFailure { VaultMedia.clear(session); password.fill('\u0000'); Ui.toast(this,"密碼錯誤或版本無法讀取") }
                }
            }.start()
        }
    }
    private fun previewPlain(note: Note,action: String,session: String?,password: CharArray?,restore: (CharArray?)->Unit) {
        var confirming=false
        fun clear() { session?.let(VaultMedia::clear); password?.fill('\u0000') }
        val content=Ui.column(this).apply { Ui.pad(this,16) }
        content.addView(Ui.rawLabel(this,note.title.ifBlank { "未命名筆記" },21f,bold=true))
        content.addView(Ui.rawLabel(this,note.category,13f,Ui.muted))
        content.addView(TextView(this).apply {
            text=RichText.decode(this@HistoryActivity,note.body,note.formatting,Ui.dp(this@HistoryActivity,76),resources.displayMetrics.widthPixels-Ui.dp(this@HistoryActivity,90))
            textSize=16f; setTextColor(Ui.ink)
        })
        AlertDialog.Builder(this).setTitle("版本預覽")
            .setView(ScrollView(this).apply { addView(content) }).setNegativeButton("取消",null)
            .setPositiveButton(action) { _,_ ->
                confirming=true
                AlertDialog.Builder(this).setTitle(action+"？")
                    .setMessage("完整還原內容、圖片、格式與提醒設定。過去的單次提醒不會重新通知。")
                    .setNegativeButton("取消",null).setPositiveButton("還原") { _,_ -> restore(password) }
                    .setOnDismissListener { clear() }.show()
            }.setOnDismissListener { if(!confirming) clear() }.show()
    }
    private fun restore(action: (NoteStore)->Long) {
        if(!ProUi.require(this,PremiumFeature.VERSION_HISTORY)) return
        val progress=AlertDialog.Builder(this).setTitle("正在還原").setView(ProgressBar(this)).setCancelable(false).show()
        Thread {
            val result=runCatching { NoteStore(this).use(action) }
            runOnUiThread {
                progress.dismiss()
                if(isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { id ->
                    NoteWidgetProvider.updateAll(this)
                    setResult(RESULT_OK,Intent().putExtra("noteId",id)); Ui.toast(this,"已還原筆記"); finish()
                }.onFailure { if(it is ProRequiredException) ProUi.show(this,it.feature) else Ui.toast(this,it.message ?: "還原失敗，原筆記仍保留") }
            }
        }.start()
    }
}
