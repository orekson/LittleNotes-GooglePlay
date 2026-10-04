package tw.local.memonote

import android.app.*
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import tw.local.memonote.ui.LocalizedActivity
import tw.local.memonote.ui.AppLanguage
import android.os.Bundle
import android.text.*
import android.view.*
import android.widget.*
import tw.local.memonote.data.*
import tw.local.memonote.model.OrderedListEditor
import tw.local.memonote.model.TextStyle
import tw.local.memonote.model.NoteColors
import tw.local.memonote.rich.*
import tw.local.memonote.ui.*
import tw.local.memonote.widget.NoteWidgetProvider
import tw.local.memonote.entitlement.*

class EditorActivity: LocalizedActivity() {
    private lateinit var categoryInput: EditText
    private var vaultPassword: CharArray? = null
    private var vaultSession: String? = null
    private lateinit var titleInput: EditText
    private lateinit var body: EditText
    private lateinit var paper: LinearLayout
    private var original=Note()
    private var background="paper"
    private var fade=35
    private var fadeControls: LinearLayout? = null
    private var pendingStart=0
    private var pendingEnd=0
    private var ready=false
    private var draftKey=java.util.UUID.randomUUID().toString()
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        draftKey = state?.getString("draftKey") ?: draftKey
        val stored = try {
            NoteStore(this).use { it.find(intent.getLongExtra("noteId",0)) } ?: Note()
        } catch(e: Exception) {
            Ui.toast(this,"無法開啟筆記"); finish(); return
        }
        if(stored.isLocked) {
            lockedGate(stored,state)
            return
        }
        val restored = try { state?.getString("draftKey")?.let { DraftFiles.read(this,it) } }
        catch(e: Exception) { Ui.toast(this,"無法還原草稿，請重新開啟筆記"); finish(); return }
        original = restored?.first ?: stored
        render(restored?.second ?: original,state)
    }

    override fun onResume() {
        super.onResume()
        tw.local.memonote.reminder.ReminderScheduler.safeSync(this)
    }

    private fun lockedGate(stored: Note,state: Bundle?) {
        val root = Ui.root(this)
        val content = Ui.column(this); Ui.pad(content,24); root.addView(content)
        content.addView(Ui.label(this,"🔒 加密筆記",26f,bold=true))
        content.addView(Ui.space(this,12))
        content.addView(Ui.label(this,"輸入密碼後才能檢視和編輯。忘記密碼就無法還原。",15f,Ui.muted))
        content.addView(Ui.space(this,20))
        content.addView(Ui.button(this,"輸入密碼",true) { askUnlock(stored,state) })
        content.addView(Ui.button(this,"返回筆記列表") { finish() })
        askUnlock(stored,state)
    }

    private fun askUnlock(stored: Note,state: Bundle?) {
        PasswordDialogs.ask(this,"解鎖筆記","密碼只用於解鎖這篇筆記；忘記密碼就無法還原。",false) { password ->
            val session = java.util.UUID.randomUUID().toString()
            val progress = AlertDialog.Builder(this).setTitle(AppLanguage.text(this, "正在解鎖"))
                .setView(ProgressBar(this)).setCancelable(false).create()
            progress.show()
            Thread {
                val result = runCatching {
                    val saved = VaultRepository.open(this,stored,password,session)
                    val restored = try {
                        state?.getString("draftKey")?.takeIf { DraftFiles.hasProtected(this,it) }
                            ?.let { DraftFiles.readProtected(this,it,stored.id,password,session) }
                    } catch (_: Exception) { null }
                    restored ?: (saved to saved)
                }
                runOnUiThread {
                    progress.dismiss()
                    result.onSuccess { pair ->
                        vaultPassword = password
                        vaultSession = session
                        original = pair.first
                        render(pair.second,state)
                    }.onFailure {
                        password.fill('\u0000')
                        VaultMedia.clear(session)
                        Ui.toast(this,"解鎖失敗：密碼錯誤或資料損壞")
                    }
                }
            }.start()
        }
    }

    private fun render(note: Note,state: Bundle?) {
        background=note.background; fade=note.fade
        pendingStart=state?.getInt("pendingStart") ?: 0; pendingEnd=state?.getInt("pendingEnd") ?: 0
        val root=Ui.root(this)
        val bar=Ui.row(this); bar.setPadding(Ui.dp(this,12),0,Ui.dp(this,12),0)
        bar.addView(Ui.button(this,"返回") { leave() })
        bar.addView(Ui.label(this,if(original.id==0L) "新的日常" else "編輯筆記",18f,bold=true),LinearLayout.LayoutParams(0,-2,1f))
        bar.addView(Ui.button(this,"儲存",true) { save() }); root.addView(bar)
        val scroll=ScrollView(this).apply { isFillViewport=true }
        val content=Ui.column(this); Ui.pad(content,20); scroll.addView(content); root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        titleInput=EditText(this).apply { isSaveEnabled=false; hint=AppLanguage.text(this@EditorActivity,"給這篇筆記一個名字"); textSize=24f; setTextColor(Ui.ink); backgroundTintList=android.content.res.ColorStateList.valueOf(Ui.purple); isSingleLine=true; filters=arrayOf(InputFilter.LengthFilter(160)); setText(note.title) }
        content.addView(titleInput)
        categoryInput=EditText(this).apply {
            isSaveEnabled=false; hint=AppLanguage.text(this@EditorActivity,"分類（可留空）"); textSize=15f; setTextColor(Ui.ink)
            isSingleLine=true; filters=arrayOf(InputFilter.LengthFilter(80)); setText(note.category)
        }
        content.addView(categoryInput); content.addView(Ui.space(this,10))
        val tools=Ui.row(this)
        val colors=NoteColors.palette
        colors.forEach { (name,color) -> tools.addView(Ui.button(this,name) { applyColor(color) }.apply { setTextColor(color); contentDescription=AppLanguage.format(this@EditorActivity,"文字顏色：%1\$s",AppLanguage.text(this@EditorActivity,name)) },LinearLayout.LayoutParams(Ui.dp(this,54),-2)) }
        tools.addView(ProUi.button(this,"彩虹",PremiumFeature.ADVANCED_TEXT) { format { it.copy(rainbow=true) } })
        tools.addView(ProUi.button(this,"柔光",PremiumFeature.ADVANCED_TEXT) { format { it.copy(glow=true) } })
        tools.addView(Ui.button(this,"關閉柔光") { format { it.copy(glow=false) } })
        tools.addView(Ui.button(this,"清除樣式") { format { TextStyle() } })
        paper=Ui.column(this); Ui.pad(paper,8)
        body=EditText(this).apply {
            hint=AppLanguage.text(this@EditorActivity,"今天，有什麼想留下的呢？"); textSize=16f; setTextColor(Ui.ink); setHintTextColor(Ui.muted)
            gravity=Gravity.TOP; minHeight=Ui.dp(this@EditorActivity,300); background=null
            inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters=arrayOf(InputFilter.LengthFilter(100000))
            isSaveEnabled=false
            setText(RichText.decode(this@EditorActivity,note.body,note.formatting,Ui.dp(this@EditorActivity,76)))
        }
        var numberingBeforeText = ""
        var numberingChangeStart = 0
        var numberingRemovedCount = 0
        var numberingInsertedCount = 0
        var numberingInsertedText = ""
        var adjustingNumbering = false
        body.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (adjustingNumbering) return
                numberingBeforeText = if (count > 0) s?.toString().orEmpty() else ""
                numberingChangeStart = start
                numberingRemovedCount = count
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (adjustingNumbering || s == null) return
                numberingChangeStart = start
                numberingInsertedCount = count
                numberingInsertedText = if (count > 0) s.subSequence(start, start + count).toString() else ""
            }

            override fun afterTextChanged(editable: Editable?) {
                if (adjustingNumbering || editable == null) return
                adjustingNumbering = true
                try { PremiumTextEdits.sanitizeInsertion(this@EditorActivity, editable,
                    numberingChangeStart, numberingInsertedCount) }
                finally { adjustingNumbering = false }
                val enterAt = if (numberingInsertedCount == 1 && numberingInsertedText == "\n") {
                    numberingChangeStart
                } else {
                    null
                }
                val plan = OrderedListEditor.plan(
                    editable.toString(),
                    body.selectionStart.coerceAtLeast(0),
                    enterAt,
                    OrderedListEditor.UserEdit(
                        beforeText = numberingBeforeText,
                        start = numberingChangeStart,
                        removedCount = numberingRemovedCount,
                        insertedText = numberingInsertedText
                    )
                )
                if (plan.stages.isEmpty()) return

                adjustingNumbering = true
                try {
                    plan.stages.forEach { stage ->
                        stage.forEach { edit -> editable.replace(edit.start, edit.end, edit.text) }
                    }
                    body.setSelection(plan.selection.coerceIn(0, editable.length))
                } finally {
                    adjustingNumbering = false
                }
            }
        })
        paper.addView(body,LinearLayout.LayoutParams(-1,-2)); content.addView(paper)
        body.addOnLayoutChangeListener { _,l,_,r,_,ol,_,or,_ -> if(r-l!=or-ol) fitMedia() }
        var touchX=0f; var touchY=0f
        body.setOnTouchListener { _,event ->
            if(event.actionMasked==MotionEvent.ACTION_DOWN) { touchX=event.x; touchY=event.y }
            if(event.actionMasked==MotionEvent.ACTION_UP && kotlin.math.abs(event.x-touchX)<Ui.dp(this,8) && kotlin.math.abs(event.y-touchY)<Ui.dp(this,8)) {
                val offset=body.getOffsetForPosition(event.x,event.y)
                val candidates=listOf(offset,offset-1).filter { it in 0 until body.length() }
                val hit=candidates.firstOrNull { at ->
                    val bounds=SpanGeometry.bounds(body.layout,body.text,at)
                    body.text[at]=='\uFFFC' && bounds?.contains(event.x+body.scrollX-body.totalPaddingLeft,event.y+body.scrollY-body.totalPaddingTop)==true
                }
                if(hit!=null) {
                    val check=body.text.getSpans(hit,hit+1,CheckSpan::class.java).firstOrNull()
                    val photo=body.text.getSpans(hit,hit+1,StickerSpan::class.java).firstOrNull { it.isPhoto }
                    if(check!=null) { check.checked=!check.checked; body.invalidate(); return@setOnTouchListener true }
                    val reminder=body.text.getSpans(hit,hit+1,ReminderSpan::class.java).firstOrNull()
                    if(reminder!=null) { editReminder(reminder); return@setOnTouchListener true }
                    if(photo!=null) { resizePhoto(photo); return@setOnTouchListener true }
                }
            }
            false
        }
        paper.addOnLayoutChangeListener { _,l,t,r,b,ol,ot,or,ob -> if(r-l!=or-ol || b-t!=ob-ot) refreshBackground() }
        content.addView(Ui.label(this,"選字上色；未選取時套用整篇",12f,Ui.muted))
        content.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false; addView(tools) })
        val pager = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
        }
        val pages = Ui.row(this)
        fun actionPage(): LinearLayout = Ui.column(this).also { pages.addView(it) }
        val more = actionPage()
        val moreTop = Ui.row(this)
        moreTop.addView(Ui.button(this,"✧ 桌面預覽") { preview() },
            LinearLayout.LayoutParams(0,-2,1f))
        moreTop.addView(Ui.button(this,"＋ 預留功能") {}.apply { isEnabled=false },
            LinearLayout.LayoutParams(0,-2,1f))
        more.addView(moreTop)
        val moreBottom = Ui.row(this)
        repeat(2) {
            moreBottom.addView(Ui.button(this,"＋ 預留功能") {}.apply { isEnabled=false },
                LinearLayout.LayoutParams(0,-2,1f))
        }
        more.addView(moreBottom)
        val primary = actionPage()
        val actions = Ui.row(this)
        actions.addView(Ui.button(this,"✿ 貼圖") {
            rememberSelection(); StickerPicker.show(this,{ insertSticker(it) },{ pickImage(12) })
        },LinearLayout.LayoutParams(0,-2,1f))
        actions.addView(Ui.button(this,"⏰ 時間提醒") { insertReminder() },
            LinearLayout.LayoutParams(0,-2,1f))
        primary.addView(actions)
        val inserts = Ui.row(this)
        inserts.addView(Ui.button(this,"＋ 圖片") {
            rememberSelection(); pickImage(13)
        },LinearLayout.LayoutParams(0,-2,1f))
        inserts.addView(Ui.button(this,"☐ 勾選方框") { insertCheck() },
            LinearLayout.LayoutParams(0,-2,1f))
        primary.addView(inserts)
        pager.addView(pages)
        content.addView(pager)
        pager.post {
            val width = pager.width
            more.layoutParams = more.layoutParams.apply { this.width = width }
            primary.layoutParams = primary.layoutParams.apply { this.width = width }
            pager.post { pager.scrollTo(width, 0) }
        }
        content.addView(Ui.label(this,"向右滑可看到桌面預覽與更多欄位。",12f,Ui.muted))
        content.addView(Ui.label(this,"點圖片可再縮放；點方框切換完成狀態；點提醒時間可修改。",12f,Ui.muted))
        content.addView(Ui.space(this,16)); content.addView(Ui.label(this,"背景，換一種心情",18f,bold=true))
        val backgrounds=Ui.row(this)
        listOf("奶油" to "paper","櫻花" to "sakura","海風" to "ocean","星夜" to "night").forEach { (label,ref) -> backgrounds.addView(Ui.button(this,label) { background=ref; refreshBackground() },LinearLayout.LayoutParams(0,-2,1f)) }
        content.addView(backgrounds)
        content.addView(ProUi.button(this,"從手機選擇背景圖片",PremiumFeature.ADVANCED_BACKGROUND, showBadge = false) { pickImage(11) })
        content.addView(ProUi.button(this,"背景顏色",PremiumFeature.ADVANCED_BACKGROUND, showBadge = false) {
            chooseBackgroundColor()
        })
        val fadePanel=Ui.column(this); fadeControls=fadePanel; content.addView(fadePanel)
        val fadeText=Ui.label(this,AppLanguage.format(this,"背景淡化  %1\$d%%",fade),13f,Ui.muted); fadePanel.addView(fadeText)
        val fadeSlider=SeekBar(this).apply {
            max=100; progress=fade; contentDescription=AppLanguage.text(this@EditorActivity,"背景淡化程度")
            setOnTouchListener { _, event ->
                if (!EntitlementManager.allows(this@EditorActivity,PremiumFeature.ADVANCED_BACKGROUND,FeatureOperation.MODIFY)) {
                    if (event.actionMasked == MotionEvent.ACTION_DOWN)
                        ProUi.show(this@EditorActivity,PremiumFeature.ADVANCED_BACKGROUND)
                    true
                } else false
            }
            setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?,p: Int,user: Boolean) {
                    if (user && !ProUi.require(this@EditorActivity,PremiumFeature.ADVANCED_BACKGROUND,FeatureOperation.MODIFY)) {
                        s?.progress=fade; return
                    }
                    fade=p; fadeText.text=AppLanguage.format(this@EditorActivity,"背景淡化  %1\$d%%",fade)
                    if(user) refreshBackground()
                }
                override fun onStartTrackingTouch(s: SeekBar?)=Unit
                override fun onStopTrackingTouch(s: SeekBar?)=Unit
            })
        }
        fadePanel.addView(fadeSlider)
        fadePanel.addView(Ui.button(this,"重設背景淡化") {
            fade=35; fadeSlider.progress=35; refreshBackground()
        })
        fadePanel.addView(Ui.label(this,"0% 保留原圖  ·  100% 淡至底色\n文字與貼圖不會一起變淡。",12f,Ui.muted))
        if(original.id!=0L) {
            content.addView(Ui.space(this,20))
            content.addView(ProUi.button(this,"版本紀錄",PremiumFeature.VERSION_HISTORY) {
                if(changed()) Ui.toast(this,"請先儲存或捨棄目前修改，再查看版本紀錄")
                else startActivityForResult(Intent(this,HistoryActivity::class.java).putExtra("noteId",original.id),930)
            })
            content.addView(Ui.button(this,"刪除這篇筆記") { delete() })
        }
        ready=true
        body.setSelection((state?.getInt("cursor") ?: body.length()).coerceIn(0,body.length()))
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
    }
    private fun draft()=original.copy(title=titleInput.text.toString(),body=body.text.toString(),formatting=RichText.encode(body.text),background=background,fade=fade,category=categoryInput.text.toString())
    private fun changed(): Boolean { val n=draft(); return n.title!=original.title || n.body!=original.body || n.background!=original.background || n.fade!=original.fade || n.category!=original.category || n.formatting!=RichText.encode(RichText.decode(this,original.body,original.formatting,Ui.dp(this,76))) }
    private fun format(change: (TextStyle)->TextStyle) {
        val a=body.selectionStart.coerceAtLeast(0); val b=body.selectionEnd.coerceAtLeast(0)
        if (!PremiumTextEdits.format(this,body.text,if(a==b) 0 else minOf(a,b),if(a==b) body.length() else maxOf(a,b),change)) {
            ProUi.show(this,PremiumFeature.ADVANCED_TEXT); return
        }
        body.invalidate()
        if(body.length()==0) Ui.toast(this,"先寫一些文字，再套用喜歡的效果")
    }
    private fun applyColor(color: Int) {
        val a=body.selectionStart.coerceAtLeast(0); val b=body.selectionEnd.coerceAtLeast(0)
        PremiumTextEdits.color(this,body.text,if(a==b) 0 else minOf(a,b),if(a==b) body.length() else maxOf(a,b),color)
        body.invalidate()
    }
    private fun rememberSelection() { pendingStart=body.selectionStart.coerceAtLeast(0); pendingEnd=body.selectionEnd.coerceAtLeast(pendingStart) }
    private fun insertSticker(ref: String) {
        val a=pendingStart.coerceIn(0,body.length()); val b=pendingEnd.coerceIn(a,body.length())
        if(body.length()-(b-a)>=100000) { Ui.toast(this,"這篇筆記已達字數上限"); return }
        body.text.replace(a,b,"\uFFFC"); body.text.setSpan(RichText.sticker(this,ref,Ui.dp(this,76)),a,a+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); body.setSelection(a+1)
    }
    private fun fitMedia() {
        val max=(body.width-body.totalPaddingLeft-body.totalPaddingRight).coerceAtLeast(24)
        body.text.getSpans(0,body.length(),StickerSpan::class.java).forEach { old ->
            val at=body.text.getSpanStart(old)
            val fresh=RichText.sticker(this,old.ref,Ui.dp(this,old.sizeDp).coerceAtMost(max),old.sizeDp,old.isPhoto)
            body.text.removeSpan(old); body.text.setSpan(fresh,at,at+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
    private fun resizePhoto(span: StickerSpan) {
        ImageSizeDialog.show(this,span.ref,span.sizeDp) { size ->
            val at=body.text.getSpanStart(span)
            if(at>=0) { body.text.removeSpan(span); body.text.setSpan(RichText.sticker(this,span.ref,Ui.dp(this,size),size,true),at,at+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); fitMedia() }
        }
    }
    private fun insertPhoto(ref: String,size: Int) {
        val a=pendingStart.coerceIn(0,body.length()); val b=pendingEnd.coerceIn(a,body.length())
        if(body.length()-(b-a)>=100000) { Ui.toast(this,"這篇筆記已達字數上限"); return }
        body.text.replace(a,b,"\uFFFC"); body.text.setSpan(RichText.sticker(this,ref,Ui.dp(this,size),size,true),a,a+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); body.setSelection(a+1); fitMedia()
    }
    private fun insertCheck() {
        rememberSelection(); val a=pendingStart; val b=pendingEnd
        val prefix=if(a>0 && body.text[a-1]!='\n') "\n" else ""
        val inserted=prefix+"\uFFFC "
        if(body.length()-(b-a)+inserted.length>100000) { Ui.toast(this,"這篇筆記已達字數上限"); return }
        body.text.replace(a,b,inserted); val at=a+prefix.length
        body.text.setSpan(CheckSpan(java.util.UUID.randomUUID().toString(),false,Ui.dp(this,36)),at,at+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); body.setSelection(at+2)
    }
    private fun insertReminder() {
        if(vaultPassword!=null) {
            AlertDialog.Builder(this)
                .setTitle(AppLanguage.text(this,"加密筆記的提醒"))
                .setMessage(AppLanguage.text(this,
                    "為保護加密內容，鎖定期間無法在背景顯示提醒。請先解除加密再設定提醒。"))
                .setPositiveButton(AppLanguage.text(this,"知道了"),null).show()
            return
        }
        rememberSelection()
        chooseReminderTime(null)
    }
    private fun editReminder(span: ReminderSpan) {
        chooseReminderTime(span)
    }
    private fun deleteReminder(span: ReminderSpan) {
        val at = body.text.getSpanStart(span)
        if (at >= 0) {
            val end = if (at + 1 < body.length() && body.text[at + 1] == ' ') at + 2 else at + 1
            body.text.delete(at, end)
        }
    }
    private fun chooseReminderTime(existing: ReminderSpan?) {
        val at=(existing?.let { body.text.getSpanStart(it) } ?: pendingStart).coerceIn(0,body.length())
        val text=body.text.toString()
        val lineStart=if(at==0) 0 else text.lastIndexOf('\n',at-1).let { if(it<0) 0 else it+1 }
        val lineEnd=text.indexOf('\n',at).let { if(it<0) text.length else it }
        val message=text.substring(lineStart,lineEnd).replace('\uFFFC',' ').trim().ifBlank { titleInput.text.toString().trim() }
        tw.local.memonote.reminder.ReminderDialog.show(this,existing,message,{ chosen,rule ->
            if(existing==null) {
                if(body.length()+2>100000) { Ui.toast(this,"這篇筆記已達字數上限"); return@show }
                body.text.insert(at,"\uFFFC ")
                body.text.setSpan(ReminderSpan(java.util.UUID.randomUUID().toString(),chosen,rule),
                    at,at+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                body.setSelection(at+2)
            } else {
                val position=body.text.getSpanStart(existing)
                if(position<0) return@show
                body.text.removeSpan(existing)
                body.text.setSpan(ReminderSpan(existing.id,chosen,rule),position,position+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                body.invalidate()
            }
            askReminderPermissions()
            Ui.toast(this,"儲存筆記後啟用提醒")
        },{ existing?.let { deleteReminder(it) }; Ui.toast(this,"儲存筆記後取消提醒") })
    }
    private fun askReminderPermissions() {
        if(android.os.Build.VERSION.SDK_INT>=33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),910)
            return
        }
        askExactAlarmPermission()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>,
                                            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode==910) {
            if(grantResults.firstOrNull()==android.content.pm.PackageManager.PERMISSION_GRANTED)
                askExactAlarmPermission()
            else Ui.toast(this,"未允許通知，提醒不會顯示；可到系統設定開啟。")
        }
    }
    private fun askExactAlarmPermission() {
        if(!tw.local.memonote.reminder.ReminderScheduler.exactAllowed(this)) {
            AlertDialog.Builder(this)
                .setTitle(AppLanguage.text(this,"允許準時提醒"))
                .setMessage(AppLanguage.text(this,
                    "請在系統設定允許精準鬧鐘；若不允許，提醒仍會排程，但可能較晚出現。"))
                .setPositiveButton(AppLanguage.text(this,"前往設定")) { _,_ ->
                    runCatching {
                        startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            android.net.Uri.parse("package:$packageName")))
                    }
                }
                .setNegativeButton(AppLanguage.text(this,"稍後"),null).show()
        }
    }
    private fun pickImage(request: Int) {
        if (request==11 && !ProUi.require(this,PremiumFeature.ADVANCED_BACKGROUND)) return
        try { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="image/*"; addCategory(Intent.CATEGORY_OPENABLE) },request) }
        catch(e: Exception) { Ui.toast(this,"這台裝置沒有可用的圖片選擇器") }
    }
    override fun onActivityResult(request: Int,result: Int,data: Intent?) {
        super.onActivityResult(request,result,data)
        if(request==930) {
            if(result==RESULT_OK) { startActivity(Intent(this,EditorActivity::class.java).putExtra("noteId",original.id)); finish() }
            return
        }
        if(result!=RESULT_OK || data?.data==null) return
        if(request==11 && !ProUi.require(this,PremiumFeature.ADVANCED_BACKGROUND)) return
        try { val ref=ImageFiles.import(this,data.data!!,request==12,vaultSession); when(request) { 12->insertSticker(ref); 13->ImageSizeDialog.show(this,ref,220) { insertPhoto(ref,it) }; else->{ background=ref; refreshBackground() } } }
        catch(e: Exception) { Ui.toast(this,AppLanguage.format(this,"圖片匯入失敗：%1\$s",AppLanguage.text(this,e.message ?: "請換一張圖片"))) }
    }
    private fun chooseBackgroundColor() {
        if (!ProUi.require(this,PremiumFeature.ADVANCED_BACKGROUND,FeatureOperation.MODIFY)) return
        val content=Ui.column(this); Ui.pad(content,12)
        content.addView(Ui.label(this,"選擇純色背景；不會改變文字顏色。",13f,Ui.muted))
        val row=Ui.row(this)
        val dialog=AlertDialog.Builder(this).setTitle(AppLanguage.text(this,"背景顏色"))
            .setView(content).setNegativeButton(AppLanguage.text(this,"取消"),null).create()
        NoteColors.palette.forEach { (name,color) ->
            row.addView(Ui.button(this,name) {
                if (ProUi.require(this,PremiumFeature.ADVANCED_BACKGROUND,FeatureOperation.MODIFY)) {
                    background=NoteColors.backgroundRef(color); refreshBackground(); dialog.dismiss()
                }
            }.apply {
                backgroundTintList=android.content.res.ColorStateList.valueOf(color)
                setTextColor(if(android.graphics.Color.luminance(color)>.45f) Ui.ink else android.graphics.Color.WHITE)
                contentDescription=AppLanguage.format(this@EditorActivity,"背景顏色：%1\$s",AppLanguage.text(this@EditorActivity,name))
            },LinearLayout.LayoutParams(Ui.dp(this,54),-2))
        }
        content.addView(HorizontalScrollView(this).apply { addView(row) })
        dialog.show()
    }
    private fun refreshBackground() {
        fadeControls?.visibility=if(NoteColors.backgroundColor(background)!=null) View.GONE else View.VISIBLE
        if(paper.width<=0) return
        val w=paper.width.coerceAtMost(600); val h=(paper.height.toFloat()/paper.width*w).toInt().coerceIn(100,1000)
        paper.background=BitmapDrawable(resources,NoteRenderer.background(this,background,fade,w,h))
    }
    private fun preview() {
        try {
            val intent=Intent(this,PreviewActivity::class.java)
            if(vaultSession!=null) {
                intent.putExtra("previewToken",PreviewCache.put(draft()))
            } else {
                java.io.File(cacheDir,"preview.json").writeText(draft().toJson())
            }
            startActivity(intent)
        } catch(e: Exception) { Ui.toast(this,"無法產生預覽，請重試") }
    }
    private fun save() {
        val note=draft()
        PremiumEdits.deniedFeature(EntitlementManager.policy(this),original,note)?.let {
            ProUi.show(this,it); return
        }
        if(note.title.isBlank() && note.body.isBlank() && note.id==0L) { Ui.toast(this,"先寫下標題或內容吧"); return }
        val password=vaultPassword
        if(password!=null) {
            if(!changed()) { setResult(RESULT_OK,Intent().putExtra("noteId",note.id)); finish(); return }
            val progress=AlertDialog.Builder(this).setTitle(AppLanguage.text(this, "正在儲存加密筆記"))
                .setView(ProgressBar(this)).setCancelable(false).create()
            progress.show()
            Thread {
                val result=runCatching { VaultRepository.saveEdited(this,note,password) }
                runOnUiThread {
                    progress.dismiss()
                    result.onSuccess {
                        original=note
                        NoteWidgetProvider.updateAll(this)
                        setResult(RESULT_OK,Intent().putExtra("noteId",note.id))
                        Ui.toast(this,"加密筆記已儲存")
                        finish()
                    }.onFailure { Ui.toast(this,"儲存失敗，輸入內容已保留") }
                }
            }.start()
            return
        }
        try { val id=NoteStore(this).use { it.saveFromEditor(original,note) }; original=note.copy(id=id); NoteWidgetProvider.updateAll(this); setResult(RESULT_OK,Intent().putExtra("noteId",id)); Ui.toast(this,"已儲存，桌面筆記已更新"); finish() }
        catch(e: Exception) { Ui.toast(this,"儲存失敗，輸入內容已保留，請重試") }
    }
    private fun delete() { AlertDialog.Builder(this).setTitle(AppLanguage.text(this, "刪除這篇筆記？")).setMessage(AppLanguage.text(this, if(EntitlementManager.snapshot(this).isPro) "筆記會放入回收桶，保留 30 天；桌面小工具會暫停顯示。" else "Free 刪除後無法復原。Pro 可將刪除的筆記保留在回收桶 30 天。")).setNegativeButton(AppLanguage.text(this, "保留"),null).setPositiveButton(AppLanguage.text(this, "刪除")) { _,_->
        try { NoteStore(this).use { it.delete(original.id) }; NoteWidgetProvider.updateAll(this); finish() } catch(e: Exception) { Ui.toast(this,"刪除失敗，請重試") }
    }.show() }
    private fun leave() {
        if(!ready || !changed()) { finish(); return }
        AlertDialog.Builder(this).setTitle(AppLanguage.text(this, "要儲存這次修改嗎？")).setPositiveButton(AppLanguage.text(this, "儲存")) { _,_-> save() }.setNegativeButton(AppLanguage.text(this, "捨棄")) { _,_-> finish() }.setNeutralButton(AppLanguage.text(this, "繼續編輯"),null).show()
    }
    override fun onBackPressed()=leave()
    override fun onSaveInstanceState(out: Bundle) {
        out.putString("draftKey",draftKey)
        if(ready) {
            try {
                val password=vaultPassword
                if(password==null) DraftFiles.write(this,draftKey,original,draft())
                else DraftFiles.writeProtected(this,draftKey,original,draft(),password)
                out.putString("draftKey",draftKey)
            }
            catch(e: Exception) { Ui.toast(this,"草稿暫存失敗，請先儲存筆記") }
            out.putInt("cursor",body.selectionStart); out.putInt("pendingStart",pendingStart); out.putInt("pendingEnd",pendingEnd)
        }
        super.onSaveInstanceState(out)
    }
    override fun onDestroy() {
        if(isFinishing) DraftFiles.delete(this,draftKey)
        vaultSession?.let { VaultMedia.clear(it) }
        vaultPassword?.fill('\u0000')
        super.onDestroy()
    }
}
