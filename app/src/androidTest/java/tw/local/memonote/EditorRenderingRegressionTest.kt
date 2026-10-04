package tw.local.memonote

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import tw.local.memonote.data.*
import tw.local.memonote.rich.*
import java.util.UUID

class EditorRenderingRegressionTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun reminderHitBoundsCoverTheEntireRenderedTimeAfterReload() {
        for (prefix in listOf("", "事項 ", "مرحبا ")) {
            val text = SpannableStringBuilder(prefix + "\uFFFC 提醒事項")
            val at = prefix.length
            text.setSpan(ReminderSpan(UUID.randomUUID().toString(), System.currentTimeMillis()+3600000),
                at,at+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val restored = RichText.decode(context,text.toString(),RichText.encode(text),76)
            val span = restored.getSpans(at,at+1,ReminderSpan::class.java).single()
            val paint = TextPaint().apply { textSize=32f }
            val layout = StaticLayout.Builder.obtain(restored,0,restored.length,paint,500).build()
            val rect = SpanGeometry.bounds(layout,restored,at)
            assertNotNull("Reminder must have a clickable region",rect)
            val width = span.getSize(paint,restored,at,at+1,null).toFloat()
            assertEquals(width,rect!!.width(),1f)
            assertTrue(rect.contains(rect.left+width*.1f,rect.centerY()))
            assertTrue(rect.contains(rect.left+width*.9f,rect.centerY()))
        }
    }
    @Test fun solidBackgroundSurvivesStorageAndPortableBackupAndRendersChosenColor() {
        val note = Note(body="背景測試",background="solid_FFD43375",fade=67)
        val id = NoteStore(context).use { it.save(note) }
        val session = UUID.randomUUID().toString()
        try {
            val saved = NoteStore(context).use { it.find(id)!! }
            val restored = PortableNotes.openInMemory(PortableNotes.pack(context,saved),id,session)
            assertEquals("solid_FFD43375",restored.background)
            assertTrue(PortableNotes.refs(restored).isEmpty())
            for (fade in listOf(0,67,100)) {
                val bitmap = NoteRenderer.background(context,restored.background,fade,40,60)
                try {
                    assertEquals(0xffd43375.toInt(),bitmap.getPixel(0,0))
                    assertEquals(0xffd43375.toInt(),bitmap.getPixel(20,30))
                } finally { bitmap.recycle() }
            }
        } finally { VaultMedia.clear(session); NoteStore(context).use { it.delete(id) } }
    }
}