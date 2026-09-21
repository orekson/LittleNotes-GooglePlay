package tw.local.memonote

import android.app.*
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.text.*
import android.view.*
import android.widget.*
import tw.local.memonote.data.*
import tw.local.memonote.model.TextStyle
import tw.local.memonote.rich.*
import tw.local.memonote.ui.*
import tw.local.memonote.widget.NoteWidgetProvider

class EditorActivity: Activity() {
    private lateinit var titleInput: EditText
    private lateinit var body: EditText
    private lateinit var paper: LinearLayout
    private var original=Note()
    private var background="paper"
    private var fade=35
    private var pendingStart=0
    private var pendingEnd=0
    private var ready=false
    private var draftKey=java.util.UUID.randomUUID().toString()
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val restored=try { state?.getString("draftKey")?.let { draftKey=it; DraftFiles.read(this,it) } }
        catch(e: Exception) { Ui.toast(this,"無法還原草稿，請重新開啟筆記"); finish(); return }
        original=try { restored?.first ?: NoteStore(this).use { it.find(intent.getLongExtra("noteId",0)) } ?: Note() }
        catch(e: Exception) { Ui.toast(this,"無法開啟筆記"); finish(); return }
        val note=restored?.second ?: original
        background=note.background; fade=note.fade
        pendingStart=state?.getInt("pendingStart") ?: 0; pendingEnd=state?.getInt("pendingEnd") ?: 0
        val root=Ui.root(this)
        val bar=Ui.row(this); bar.setPadding(Ui.dp(this,12),0,Ui.dp(this,12),0)
        bar.addView(Ui.button(this,"返回") { leave() })
        bar.addView(Ui.label(this,if(original.id==0L) "新的日常" else "編輯筆記",18f,bold=true),LinearLayout.LayoutParams(0,-2,1f))
        bar.addView(Ui.button(this,"儲存",true) { save() }); root.addView(bar)
        val scroll=ScrollView(this).apply { isFillViewport=true }
        val content=Ui.column(this); Ui.pad(content,20); scroll.addView(content); root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        titleInput=EditText(this).apply { isSaveEnabled=false; hint="給這篇筆記一個名字"; textSize=24f; setTextColor(Ui.ink); backgroundTintList=android.content.res.ColorStateList.valueOf(Ui.purple); isSingleLine=true; filters=arrayOf(InputFilter.LengthFilter(160)); setText(note.title) }
        content.addView(titleInput); content.addView(Ui.space(this,10))
        val tools=Ui.row(this)
        val colors=listOf("墨" to 0xff302b3e.toInt(),"莓" to 0xffd43375.toInt(),"紫" to 0xff8440cc.toInt(),"藍" to 0xff2371c7.toInt(),"綠" to 0xff10846f.toInt(),"金" to 0xffb2730a.toInt(),"白" to 0xffffffff.toInt())
        colors.forEach { (name,color) -> tools.addView(Ui.button(this,name) { format { it.copy(color=color,rainbow=false) } }.apply { setTextColor(color); contentDescription="文字顏色：$name" },LinearLayout.LayoutParams(Ui.dp(this,54),-2)) }
        tools.addView(Ui.button(this,"彩虹") { format { it.copy(rainbow=true) } })
        tools.addView(Ui.button(this,"柔光") { format { it.copy(glow=true) } })
        tools.addView(Ui.button(this,"關閉柔光") { format { it.copy(glow=false) } })
        tools.addView(Ui.button(this,"清除樣式") { format { TextStyle() } })
        paper=Ui.column(this); Ui.pad(paper,8)
        body=EditText(this).apply {
            hint="今天，有什麼想留下的呢？"; textSize=16f; setTextColor(Ui.ink); setHintTextColor(Ui.muted)
            gravity=Gravity.TOP; minHeight=Ui.dp(this@EditorActivity,300); background=null
            inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters=arrayOf(InputFilter.LengthFilter(100000))
            isSaveEnabled=false
            setText(RichText.decode(this@EditorActivity,note.body,note.formatting,Ui.dp(this@EditorActivity,76)))
        }
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
                    if(photo!=null) { resizePhoto(photo); return@setOnTouchListener true }
                }
            }
            false
        }
        paper.addOnLayoutChangeListener { _,l,t,r,b,ol,ot,or,ob -> if(r-l!=or-ol || b-t!=ob-ot) refreshBackground() }
        content.addView(Ui.label(this,"選字上色；未選取時套用整篇",12f,Ui.muted))
        content.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false; addView(tools) })
        val actions=Ui.row(this)
        actions.addView(Ui.button(this,"✿ 貼圖") { rememberSelection(); StickerPicker.show(this,{ insertSticker(it) },{ pickImage(12) }) },LinearLayout.LayoutParams(0,-2,1f))
        actions.addView(Ui.button(this,"✧ 桌面預覽") { preview() },LinearLayout.LayoutParams(0,-2,1f)); content.addView(actions)
        val inserts=Ui.row(this)
        inserts.addView(Ui.button(this,"＋ 圖片") { rememberSelection(); pickImage(13) },LinearLayout.LayoutParams(0,-2,1f))
        inserts.addView(Ui.button(this,"☐ 勾選方框") { insertCheck() },LinearLayout.LayoutParams(0,-2,1f)); content.addView(inserts)
        content.addView(Ui.label(this,"點圖片可再縮放；點方框切換完成狀態。",12f,Ui.muted))
        content.addView(Ui.space(this,16)); content.addView(Ui.label(this,"背景，換一種心情",18f,bold=true))
        val backgrounds=Ui.row(this)
        listOf("奶油" to "paper","櫻花" to "sakura","海風" to "ocean","星夜" to "night").forEach { (label,ref) -> backgrounds.addView(Ui.button(this,label) { background=ref; refreshBackground() },LinearLayout.LayoutParams(0,-2,1f)) }
        content.addView(backgrounds)
        content.addView(Ui.button(this,"從手機選擇背景圖片") { pickImage(11) })
        val fadeText=Ui.label(this,"背景淡化  $fade%",13f,Ui.muted); content.addView(fadeText)
        content.addView(SeekBar(this).apply {
            max=100; progress=fade; contentDescription="背景淡化程度"
            setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?,p: Int,user: Boolean) { fade=p; fadeText.text="背景淡化  $fade%"; if(user) refreshBackground() }
                override fun onStartTrackingTouch(s: SeekBar?)=Unit
                override fun onStopTrackingTouch(s: SeekBar?)=Unit
            })
        })
        content.addView(Ui.label(this,"0% 保留原圖  ·  100% 淡至底色\n文字與貼圖不會一起變淡。",12f,Ui.muted))
        if(original.id!=0L) { content.addView(Ui.space(this,20)); content.addView(Ui.button(this,"刪除這篇筆記") { delete() }) }
        ready=true
        body.setSelection((state?.getInt("cursor") ?: body.length()).coerceIn(0,body.length()))
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
    }
    private fun draft()=original.copy(title=titleInput.text.toString(),body=body.text.toString(),formatting=RichText.encode(body.text),background=background,fade=fade)
    private fun changed(): Boolean { val n=draft(); return n.title!=original.title || n.body!=original.body || n.background!=original.background || n.fade!=original.fade || n.formatting!=RichText.encode(RichText.decode(this,original.body,original.formatting,Ui.dp(this,76))) }
    private fun format(change: (TextStyle)->TextStyle) {
        val a=body.selectionStart.coerceAtLeast(0); val b=body.selectionEnd.coerceAtLeast(0)
        RichText.format(body.text,if(a==b) 0 else minOf(a,b),if(a==b) body.length() else maxOf(a,b),change); body.invalidate()
        if(body.length()==0) Ui.toast(this,"先寫一些文字，再套用喜歡的效果")
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
    private fun pickImage(request: Int) {
        try { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="image/*"; addCategory(Intent.CATEGORY_OPENABLE) },request) }
        catch(e: Exception) { Ui.toast(this,"這台裝置沒有可用的圖片選擇器") }
    }
    override fun onActivityResult(request: Int,result: Int,data: Intent?) {
        super.onActivityResult(request,result,data)
        if(result!=RESULT_OK || data?.data==null) return
        try { val ref=ImageFiles.import(this,data.data!!,request==12); when(request) { 12->insertSticker(ref); 13->ImageSizeDialog.show(this,ref,220) { insertPhoto(ref,it) }; else->{ background=ref; refreshBackground() } } }
        catch(e: Exception) { Ui.toast(this,"圖片匯入失敗：${e.message ?: "請換一張圖片"}") }
    }
    private fun refreshBackground() {
        if(paper.width<=0) return
        val w=paper.width.coerceAtMost(600); val h=(paper.height.toFloat()/paper.width*w).toInt().coerceIn(100,1000)
        paper.background=BitmapDrawable(resources,NoteRenderer.background(this,background,fade,w,h))
    }
    private fun preview() {
        try { val file=java.io.File(cacheDir,"preview.json"); file.writeText(draft().toJson()); startActivity(Intent(this,PreviewActivity::class.java)) }
        catch(e: Exception) { Ui.toast(this,"無法產生預覽，請重試") }
    }
    private fun save() {
        val note=draft()
        if(note.title.isBlank() && note.body.isBlank() && note.id==0L) { Ui.toast(this,"先寫下標題或內容吧"); return }
        try { val id=NoteStore(this).use { it.saveFromEditor(original,note) }; original=note.copy(id=id); NoteWidgetProvider.updateAll(this); setResult(RESULT_OK,Intent().putExtra("noteId",id)); Ui.toast(this,"已儲存，桌面筆記已更新"); finish() }
        catch(e: Exception) { Ui.toast(this,"儲存失敗，輸入內容已保留，請重試") }
    }
    private fun delete() { AlertDialog.Builder(this).setTitle("刪除這篇筆記？").setMessage("桌面上顯示這篇的小工具會提示重新選擇筆記。").setNegativeButton("保留",null).setPositiveButton("刪除") { _,_->
        try { NoteStore(this).use { it.delete(original.id) }; NoteWidgetProvider.updateAll(this); finish() } catch(e: Exception) { Ui.toast(this,"刪除失敗，請重試") }
    }.show() }
    private fun leave() {
        if(!ready || !changed()) { finish(); return }
        AlertDialog.Builder(this).setTitle("要儲存這次修改嗎？").setPositiveButton("儲存") { _,_-> save() }.setNegativeButton("捨棄") { _,_-> finish() }.setNeutralButton("繼續編輯",null).show()
    }
    override fun onBackPressed()=leave()
    override fun onSaveInstanceState(out: Bundle) {
        if(ready) {
            try { DraftFiles.write(this,draftKey,original,draft()); out.putString("draftKey",draftKey) }
            catch(e: Exception) { Ui.toast(this,"草稿暫存失敗，請先儲存筆記") }
            out.putInt("cursor",body.selectionStart); out.putInt("pendingStart",pendingStart); out.putInt("pendingEnd",pendingEnd)
        }
        super.onSaveInstanceState(out)
    }
    override fun onDestroy() { if(isFinishing) DraftFiles.delete(this,draftKey); super.onDestroy() }
}
