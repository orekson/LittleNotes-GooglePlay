package tw.local.memonote

import android.text.SpannableStringBuilder
import android.text.Spanned
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import tw.local.memonote.data.*
import tw.local.memonote.rich.*

@RunWith(AndroidJUnit4::class)
class PersonalizationTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun checklist(): Note {
        val text=SpannableStringBuilder("\uFFFC 喝水\n\uFFFC 散步")
        for((i,at) in listOf(0,5).withIndex()) text.setSpan(CheckSpan("check-$i",false,36),at,at+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return Note(title="測試清單",body=text.toString(),formatting=RichText.encode(text))
    }
    @Test fun resizedPhotoRoundTripAndLargeImageTilesStayBounded() {
        val text=SpannableStringBuilder("之前\n\uFFFC\n之後")
        text.setSpan(RichText.sticker(context,"asset:stickers/legacy_0.png",480,480,true),3,4,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val raw=RichText.encode(text)
        val decoded=RichText.decode(context,text.toString(),raw,76,200)
        val span=decoded.getSpans(0,decoded.length,StickerSpan::class.java).single()
        assertEquals(480,span.sizeDp); assertTrue(span.isPhoto); assertTrue(span.drawable.bounds.width()<=200)
        val renderer=NoteRenderer(context,Note(body=text.toString(),formatting=raw),540,1.5f)
        assertEquals(0,renderer.tiles.first().top)
        renderer.tiles.forEachIndexed { i,tile ->
            if(i>0) assertEquals(renderer.tiles[i-1].bottom,tile.top)
            val image=renderer.render(i); assertTrue(image.allocationByteCount<900000); image.recycle()
        }
        assertEquals(text.length,renderer.tiles.last().end)
    }
    @Test fun checkStatePersistsAndDoesNotChangeAnotherItem() {
        val id=NoteStore(context).use { it.save(checklist()) }
        try {
            assertTrue(NoteStore(context).use { it.toggleCheck(id,"check-0") })
            val note=NoteStore(context).use { it.find(id)!! }
            assertEquals(mapOf("check-0" to true,"check-1" to false),CheckState.states(note.formatting))
            assertFalse(NoteStore(context).use { it.toggleCheck(id,"missing") })
            val renderer=NoteRenderer(context,note,450,1.5f)
            val hits=renderer.tiles.indices.flatMap { renderer.checks(it) }
            assertEquals(2,hits.size); assertTrue(hits[0].checked); assertFalse(hits[1].checked)
            assertTrue(hits.all { it.rect.width()>0 && it.rect.height()>0 })
        } finally { NoteStore(context).use { it.delete(id) } }
    }
    @Test fun savingOpenEditorPreservesNewWidgetChecks() {
        val id=NoteStore(context).use { it.save(checklist()) }
        try {
            val original=NoteStore(context).use { it.find(id)!! }
            NoteStore(context).use { it.toggleCheck(id,"check-0") }
            NoteStore(context).use { it.saveFromEditor(original,original.copy(title="編輯中的新標題")) }
            val saved=NoteStore(context).use { it.find(id)!! }
            assertEquals("編輯中的新標題",saved.title); assertEquals(true,CheckState.states(saved.formatting)["check-0"])
            NoteStore(context).use { it.toggleCheck(id,"check-0") }
            assertEquals(false,CheckState.states(NoteStore(context).use { it.find(id)!! }.formatting)["check-0"])
        } finally { NoteStore(context).use { it.delete(id) } }
    }
    @Test fun entireStickerPackDecodesAndLegacyNotesStillOpen() {
        val files=context.assets.list("stickers")!!.filter { it.endsWith(".png") }
        assertEquals(31,files.size)
        files.forEach { file -> val bitmap=ImageFiles.load(context,"asset:stickers/$file"); assertNotNull(file,bitmap); bitmap?.recycle() }
        val old="""{"version":1,"stickers":[{"at":0,"ref":"asset:stickers/legacy_1.png"}]}"""
        val decoded=RichText.decode(context,"\uFFFC",old,76)
        val span=decoded.getSpans(0,1,StickerSpan::class.java).single()
        assertEquals(76,span.sizeDp); assertFalse(span.isPhoto)
    }
    @Test fun fullWidthPhotoHitRegionSurvivesFollowingTextWrap() {
        val text=SpannableStringBuilder("\uFFFC後面的文字")
        val span=RichText.sticker(context,"asset:stickers/legacy_0.png",180,180,true)
        text.setSpan(span,0,1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val paint=android.text.TextPaint().apply { textSize=20f }
        val layout=android.text.StaticLayout.Builder.obtain(text,0,text.length,paint,span.drawable.bounds.width()).build()
        assertTrue(layout.getLineForOffset(1)>layout.getLineForOffset(0))
        val bounds=SpanGeometry.bounds(layout,text,0)!!
        assertEquals(span.drawable.bounds.width().toFloat(),bounds.width(),.01f)
        assertTrue(bounds.contains(bounds.centerX(),bounds.centerY()))
    }
    @Test fun widgetGeometrySelectsActiveOrientationWidth() {
        val options=android.os.Bundle().apply {
            putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,300)
            putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,550)
        }
        assertEquals(300,tw.local.memonote.widget.NoteWidgetProvider.activeWidth(options,false))
        assertEquals(550,tw.local.memonote.widget.NoteWidgetProvider.activeWidth(options,true))
    }
    @Test fun denseCheckboxesAlwaysHaveIndividualWidgetTargets() {
        val text=SpannableStringBuilder("\uFFFC".repeat(100))
        for(at in text.indices) text.setSpan(CheckSpan("dense-$at",false,36),at,at+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val renderer=NoteRenderer(context,Note(body=text.toString(),formatting=RichText.encode(text)),540,540f/800)
        val hits=renderer.tiles.indices.flatMap { renderer.checks(it) }
        assertEquals(100,hits.map { it.id }.distinct().size)
        renderer.tiles.indices.forEach { assertTrue(renderer.checks(it).size<=24) }
    }
}
