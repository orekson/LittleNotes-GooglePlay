package tw.local.memonote

import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.Spanned
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import tw.local.memonote.data.*
import tw.local.memonote.rich.*
import tw.local.memonote.model.*
import tw.local.memonote.widget.NoteWidgetProvider
import java.io.File

@RunWith(AndroidJUnit4::class)
class NoteIntegrationTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun richTextAndStickerSurviveDatabaseReload() {
        val text=SpannableStringBuilder("今天好開心 ✨\n\uFFFC\n最後一行")
        RichText.format(text,0,5){TextStyle(0xffaa3399.toInt(),true,true)}
        val at=text.indexOf('\uFFFC'); text.setSpan(RichText.sticker(context,"asset:stickers/legacy_1.png",76),at,at+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val id=NoteStore(context).use { it.save(Note(title="測試",body=text.toString(),formatting=RichText.encode(text),background="sakura",fade=65)) }
        try {
            val stored=NoteStore(context).use { it.find(id)!! }
            val restored=RichText.decode(context,stored.body,stored.formatting,76)
            assertEquals(text.toString(),restored.toString()); assertEquals(65,stored.fade)
            assertEquals(TextStyle(0xffaa3399.toInt(),true,true),restored.getSpans(0,5,PaintSpan::class.java).single().style)
            assertEquals("asset:stickers/legacy_1.png",restored.getSpans(at,at+1,StickerSpan::class.java).single().ref)
        } finally { NoteStore(context).use { it.delete(id) } }
    }
    @Test fun longParagraphTilesPreserveEveryCharacterAndBoundBitmapMemory() {
        val body="開頭\n\n"+"這是一段沒有換行的長篇筆記😀👩‍💻。".repeat(400)+"\n最末文字 END"
        val renderer=NoteRenderer(context,Note(body=body),450,1.5f)
        assertTrue(renderer.tiles.size>10)
        assertEquals(0,renderer.tiles.first().start); assertEquals(body.length,renderer.tiles.last().end)
        val collected=renderer.tiles.joinToString("") { body.substring(it.start,it.end) }
        assertEquals(body,collected)
        renderer.tiles.forEachIndexed { i,t ->
            if(i>0) assertEquals(renderer.tiles[i-1].end,t.start)
            val bitmap=renderer.render(i); assertTrue(bitmap.allocationByteCount<900000); bitmap.recycle()
        }
    }
    @Test fun fadingBackgroundDoesNotModifyOriginalImage() {
        val clear=NoteRenderer.background(context,"sakura",100,40,60)
        assertEquals(0xfffffbf6.toInt(),clear.getPixel(20,30)); clear.recycle()
        val strong=NoteRenderer.background(context,"sakura",0,40,60)
        assertNotEquals(0xfffffbf6.toInt(),strong.getPixel(20,30)); strong.recycle()
    }
    @Test fun widgetsKeepIndependentBindingsWhenNoteIsDeleted() {
        val first=NoteStore(context).use { it.save(Note(title="一",body="第一篇")) }
        val second=NoteStore(context).use { it.save(Note(title="二",body="第二篇")) }
        try {
            NoteWidgetProvider.bind(context,9901,first); NoteWidgetProvider.bind(context,9902,second)
            NoteStore(context).use { it.delete(first) }
            assertNull(NoteStore(context).use { it.find(NoteWidgetProvider.noteId(context,9901)) })
            assertEquals("第二篇",NoteStore(context).use { it.find(NoteWidgetProvider.noteId(context,9902))!!.body })
        } finally { NoteStore(context).use { it.delete(first); it.delete(second) }; context.getSharedPreferences("widgets",0).edit().remove("9901").remove("9902").commit() }
    }
    @Test fun importedPhotoHonorsExifRotation() {
        val source=File(context.cacheDir,"rotation-test.jpg")
        Bitmap.createBitmap(40,20,Bitmap.Config.ARGB_8888).also { b -> source.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG,95,it) }; b.recycle() }
        ExifInterface(source.path).apply { setAttribute(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_ROTATE_90.toString()); saveAttributes() }
        val ref=ImageFiles.import(context,Uri.fromFile(source),false)
        try { val image=ImageFiles.load(context,ref)!!; assertEquals(20,image.width); assertEquals(40,image.height); image.recycle() }
        finally { source.delete(); File(context.filesDir,ref.removePrefix("file:")).delete() }
    }
    @Test fun wideStickersFitWithinTheirAllocatedSquare() {
        val source=File(context.filesDir,"wide-test.png")
        Bitmap.createBitmap(200,20,Bitmap.Config.ARGB_8888).also { b -> source.outputStream().use { b.compress(Bitmap.CompressFormat.PNG,100,it) }; b.recycle() }
        try { val span=RichText.sticker(context,"file:wide-test.png",76); assertTrue(span.drawable.bounds.width()<=76); assertTrue(span.drawable.bounds.height()<=76) }
        finally { source.delete() }
    }
    @Test fun longEditorKeepsSavedStateSmallAndRestoresDraft() {
        val text="日常".repeat(4000)
        val styles=org.json.JSONArray()
        for(i in text.indices step 2) styles.put(org.json.JSONObject().put("start",i).put("end",i+1).put("color",0xffaa33aa.toInt()))
        val formatting=org.json.JSONObject().put("styles",styles).toString()
        val id=NoteStore(context).use { it.save(Note(title="長篇測試",body=text,formatting=formatting)) }
        try {
            androidx.test.core.app.ActivityScenario.launch<EditorActivity>(android.content.Intent(context,EditorActivity::class.java).putExtra("noteId",id)).use { scenario ->
                scenario.onActivity { activity ->
                    val state=android.os.Bundle()
                    val method=EditorActivity::class.java.getDeclaredMethod("onSaveInstanceState",android.os.Bundle::class.java); method.isAccessible=true; method.invoke(activity,state)
                    val parcel=android.os.Parcel.obtain(); parcel.writeBundle(state)
                    assertTrue("Saved state should contain draft reference, not full document: ${parcel.dataSize()}",parcel.dataSize()<65536); parcel.recycle()
                }
                scenario.recreate()
                scenario.onActivity { activity ->
                    val field=EditorActivity::class.java.getDeclaredField("body").apply { isAccessible=true }
                    val edit=field.get(activity) as android.widget.EditText
                    assertEquals(text,edit.text.toString()); assertEquals(4000,edit.text.getSpans(0,text.length,PaintSpan::class.java).size)
                }
            }
        } finally { NoteStore(context).use { it.delete(id) } }
    }
    @Test fun longEditorDoesNotAllocateAnUnboundedSoftwareLayer() {
        val id=NoteStore(context).use { it.save(Note(title="長文",body="很長的筆記\n".repeat(2500))) }
        try {
            androidx.test.core.app.ActivityScenario.launch<EditorActivity>(android.content.Intent(context,EditorActivity::class.java).putExtra("noteId",id)).use { scenario ->
                scenario.onActivity { activity ->
                    val field=EditorActivity::class.java.getDeclaredField("body").apply { isAccessible=true }
                    val edit=field.get(activity) as android.widget.EditText
                    assertTrue("Long editor should not cache its whole document as a software bitmap",edit.layerType!=android.view.View.LAYER_TYPE_SOFTWARE || edit.height<4096)
                }
            }
        } finally { NoteStore(context).use { it.delete(id) } }
    }}
