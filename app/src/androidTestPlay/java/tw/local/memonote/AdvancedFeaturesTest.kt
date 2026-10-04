package tw.local.memonote

import android.content.*
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.text.SpannableStringBuilder
import android.text.Spanned
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import tw.local.memonote.data.*
import tw.local.memonote.entitlement.*
import tw.local.memonote.reminder.*
import tw.local.memonote.rich.*
import tw.local.memonote.widget.*
import java.io.File
import java.util.UUID

class AdvancedFeaturesTest {
    private val real=InstrumentationRegistry.getInstrumentation().targetContext
    private val token="advanced-${UUID.randomUUID()}"
    private val directory=File(real.cacheDir,token).apply { mkdirs() }
    private val preferenceNames=java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val c=object: ContextWrapper(real) {
        override fun getApplicationContext(): Context=this
        override fun getPackageName()=real.packageName+".advancedtest"
        override fun getFilesDir()=directory
        override fun getCacheDir()=directory
        override fun getDatabasePath(name: String)=File(directory,name)
        override fun openOrCreateDatabase(name: String,mode: Int,factory: SQLiteDatabase.CursorFactory?)=
            SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name),factory)
        override fun openOrCreateDatabase(name: String,mode: Int,factory: SQLiteDatabase.CursorFactory?,handler: DatabaseErrorHandler?)=
            SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).path,factory,handler)
        override fun getSharedPreferences(name: String,mode: Int): SharedPreferences {
            val key="$token-$name"; preferenceNames+=key; return real.getSharedPreferences(key,mode)
        }
    }
    private fun pro(value: Boolean)=PlayEntitlementSource.setSnapshot(c,EntitlementSnapshot(subscriptionActive=value))
    private fun denied(action: ()->Unit) {
        try { action(); fail("Free must not access this Pro operation") } catch(_: ProRequiredException) { }
    }
    @After fun cleanup() {
        NoteStore(c).use { store -> store.all().forEach { store.delete(it.id) } }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        ReminderScheduler.forceReschedule(c)
        preferenceNames.forEach { real.deleteSharedPreferences(it) }
        directory.deleteRecursively()
    }
    @Test fun widgetProStyleSurvivesStorageButStopsApplyingInFree() {
        pro(false)
        val style=WidgetStyle(corner=36,padding=28,lineGap=12,font="serif",fontSize=22,opacity=55,showImages=false)
        denied { WidgetStyle.set(c,99,style) }
        pro(true); WidgetStyle.set(c,99,style)
        assertEquals(style,WidgetStyle.effective(c,99))
        pro(false)
        assertEquals(WidgetStyle(),WidgetStyle.effective(c,99))
        assertEquals(style,WidgetStyle.stored(c,99))
        denied { WidgetStyle.set(c,99,style.copy(fontSize=24)) }
        WidgetStyle.set(c,99,WidgetStyle())
        assertEquals(WidgetStyle(),WidgetStyle.stored(c,99))
    }
    @Test fun widgetRemoteViewsActuallyApplyProStyleAndFreeDefaults() {
        val previous=EntitlementManager.snapshot(real)
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val host=android.appwidget.AppWidgetHost(real,52601)
        val manager=android.appwidget.AppWidgetManager.getInstance(real)
        var widget=-1
        var noteId=0L
        try {
            instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET")
            PlayEntitlementSource.setSnapshot(real,EntitlementSnapshot(lifetimePro=true))
            noteId=NoteStore(real).use { it.save(Note(body="Widget style test")) }
            widget=host.allocateAppWidgetId()
            assertTrue(manager.bindAppWidgetIdIfAllowed(widget,ComponentName(real,NoteWidgetProvider::class.java)))
            DateWidgetSchedule.manualBind(real,widget,noteId)
            val style=WidgetStyle(corner=36,padding=28,fontSize=22,opacity=55)
            WidgetStyle.set(real,widget,style)
            fun checkViews(expected: WidgetStyle) {
                NoteWidgetProvider.update(real,widget)
                instrumentation.runOnMainSync {
                    val view=host.createView(real,widget,manager.getAppWidgetInfo(widget))
                    val density=real.resources.displayMetrics.density
                    val root=view.findViewById<android.view.View>(R.id.widget_root)
                    assertNotNull("RemoteViews must inflate successfully",root)
                    val shape=root.background as android.graphics.drawable.GradientDrawable
                    assertEquals("XML dimensions round to physical pixels",expected.corner*density,shape.cornerRadius,1f)
                    assertEquals(expected.opacity*255/100,view.findViewById<android.widget.ImageView>(R.id.widget_background).imageAlpha)
                    assertEquals((expected.padding*density).toInt(),view.findViewById<android.view.View>(R.id.widget_header).paddingLeft)
                    assertEquals((expected.fontSize+2)*real.resources.displayMetrics.scaledDensity,
                        view.findViewById<android.widget.TextView>(R.id.widget_title).textSize,.1f)
                }
            }
            checkViews(style)
            PlayEntitlementSource.setSnapshot(real,EntitlementSnapshot())
            checkViews(WidgetStyle())
        } finally {
            if(widget>=0) { WidgetStyle.forget(real,intArrayOf(widget)); DateWidgetSchedule.forget(real,intArrayOf(widget)); host.deleteAppWidgetId(widget) }
            if(noteId>0) NoteStore(real).use { it.delete(noteId); it.purgeTrash(noteId) }
            instrumentation.uiAutomation.dropShellPermissionIdentity()
            PlayEntitlementSource.setSnapshot(real,previous)
        }
    }
    @Test fun freeCannotReadOrRestoreHistoryAndDoesNotKeepDeletedNotes() {
        pro(false)
        NoteStore(c).use { store ->
            val id=store.save(Note(body="free first"))
            store.save(store.find(id)!!.copy(body="free second"))
            denied { store.versions(id) }; denied { store.trash() }
            store.delete(id)
            pro(true)
            assertTrue(store.trash().isEmpty())
            assertTrue(store.versions(id).isEmpty())
        }
    }
    @Test fun versionsAndTrashRoundTripFullContentAndKeepCurrentVersion() {
        pro(true)
        val image=File(directory,"history.png").apply { writeBytes(byteArrayOf(1,2,3,4)) }
        NoteStore(c).use { store ->
            val initial=Note(title="old",body="old content",formatting="{\"styles\":[]}",background="file:${image.name}",fade=63,category="work")
            val id=store.save(initial)
            store.save(store.find(id)!!.copy(title="new",body="new content"))
            val version=store.versions(id).single()
            store.restoreVersion(id,version.versionId)
            val restored=store.find(id)!!
            assertEquals(initial.copy(id=id,updated=restored.updated),restored)
            assertEquals("new content",store.versions(id).first().note.body)
            store.delete(id); assertNull(store.find(id))
            pro(false); denied { store.restoreTrash(id) }; denied { store.restoreVersion(id,version.versionId) }
            pro(true); assertEquals(id,store.restoreTrash(id))
            assertEquals("old content",store.find(id)!!.body)
            assertArrayEquals(byteArrayOf(1,2,3,4),ImageFiles.readBytes(c,store.find(id)!!.background))
            assertTrue(store.trash().isEmpty())
        }
    }
    @Test fun versionLimitExpiryAndPermanentDeleteAreEnforced() {
        pro(true)
        NoteStore(c).use { store ->
            val id=store.save(Note(body="0"))
            for(i in 1..55) store.save(store.find(id)!!.copy(body=i.toString()))
            assertEquals(50,store.versions(id).size)
            store.delete(id)
            store.writableDatabase.execSQL("UPDATE note_trash SET deleted_at=? WHERE note_id=?",arrayOf(System.currentTimeMillis()-31L*86400000,id))
            assertTrue(store.trash().isEmpty()); assertTrue(store.versions(id).isEmpty())
            val other=store.save(Note(body="remove")); store.delete(other); store.purgeTrash(other)
            assertTrue(store.trash().isEmpty())
        }
    }
    @Test fun lockingEncryptsEveryHistoricalVersionAndRestorePreservesLockState() {
        pro(true)
        val password="history-safe-pass".toCharArray()
        val id=NoteStore(c).use { store ->
            val id=store.save(Note(title="secret",body="secret old"))
            store.save(store.find(id)!!.copy(body="secret new")); id
        }
        VaultRepository.lock(c,id,password)
        val old=NoteStore(c).use { store ->
            val version=store.versions(id).single()
            assertTrue(version.note.isLocked); assertEquals("",version.note.body); assertEquals("",version.note.title)
            store.readableDatabase.rawQuery("SELECT snapshot FROM note_versions",null).use { cursor ->
                while(cursor.moveToNext()) assertFalse(cursor.getString(0).contains("secret"))
            }
            version
        }
        VaultRepository.restoreVersion(c,id,old.versionId,password)
        val locked=NoteStore(c).use { it.find(id)!! }
        assertTrue(locked.isLocked)
        val session=UUID.randomUUID().toString()
        try { assertEquals("secret old",VaultRepository.open(c,locked,password,session).body) }
        finally { VaultMedia.clear(session) }
        VaultRepository.removePassword(c,locked,password)
        VaultRepository.restoreVersion(c,id,old.versionId,password)
        val plain=NoteStore(c).use { it.find(id)!! }
        assertFalse(plain.isLocked); assertEquals("secret old",plain.body)
        password.fill('\u0000')
    }
    @Test fun unchangedEncryptedSavesDoNotPushOutMeaningfulHistory() {
        pro(true)
        val password="history-unchanged-pass".toCharArray()
        val image=File(directory,"unchanged.png").apply { writeBytes(byteArrayOf(1,2,3,4)) }
        val id=NoteStore(c).use { store ->
            val id=store.save(Note(body="previous",background="file:${image.name}"))
            store.save(store.find(id)!!.copy(body="current")); id
        }
        VaultRepository.lock(c,id,password)
        val locked=NoteStore(c).use { it.find(id)!! }
        val session=UUID.randomUUID().toString()
        try {
            val draft=VaultRepository.open(c,locked,password,session)
            repeat(2) { VaultRepository.saveEdited(c,draft.copy(updated=System.currentTimeMillis()),password) }
            assertEquals("Unchanged content must not consume version slots",1,NoteStore(c).use { it.versions(id).size })
            VaultRepository.saveEdited(c,draft.copy(body="changed"),password)
            assertEquals(2,NoteStore(c).use { it.versions(id).size })
        } finally { VaultMedia.clear(session); password.fill('\u0000') }
    }
    @Test fun existingVersionTwoDatabaseMigratesWithoutChangingNotes() {
        SQLiteDatabase.openOrCreateDatabase(File(directory,"notes.db"),null).use { db ->
            db.execSQL("CREATE TABLE notes (id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,body TEXT NOT NULL,formatting TEXT NOT NULL,background TEXT NOT NULL,fade INTEGER NOT NULL,updated INTEGER NOT NULL,category TEXT NOT NULL DEFAULT '',sealed TEXT NOT NULL DEFAULT '')")
            db.execSQL("INSERT INTO notes VALUES (1,'legacy','keep','{}','paper',35,123,'work','')")
            db.version=2
        }
        NoteStore(c).use { store ->
            assertEquals("keep",store.find(1)!!.body)
            assertEquals(3,store.readableDatabase.version)
        }
    }
    @Test fun advancedRulesSurviveRichTextAndFreeCannotSaveThem() {
        pro(true)
        val marker=UUID.randomUUID().toString()
        val rule=ReminderRule(Repeat.WEEKLY,2,10,"2028-12-31","Asia/Taipei")
        val text=SpannableStringBuilder("\uFFFC task").apply { setSpan(ReminderSpan(marker,1800000000000,rule),0,1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        val note=Note(body=text.toString(),formatting=RichText.encode(text))
        val decoded=RichText.decode(c,note.body,note.formatting,76)
        assertEquals(rule,decoded.getSpans(0,decoded.length,ReminderSpan::class.java).single().rule)
        pro(false); denied { NoteStore(c).use { it.saveFromEditor(Note(),note) } }
    }
    @Test fun forcedRescheduleRecalculatesRecurringWallTimeAfterTimezoneChange() {
        val previous=EntitlementManager.snapshot(real)
        val previousZone=java.util.TimeZone.getDefault()
        val marker=UUID.randomUUID().toString()
        var id=0L
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
            PlayEntitlementSource.setSnapshot(real,EntitlementSnapshot(lifetimePro=true))
            val start=System.currentTimeMillis()+48L*3600000
            val rule=ReminderRule(Repeat.DAILY,zone="UTC")
            val raw=JSONObject().put("reminders",JSONArray().put(rule.write(JSONObject().put("id",marker).put("at",0).put("time",start)))).toString()
            id=NoteStore(real).use { it.save(Note(body="\uFFFC timezone test",formatting=raw)) }
            ReminderScheduler.forceReschedule(real)
            fun fire(): Long {
                val records=JSONArray(real.getSharedPreferences("reminder_v2",0).getString("alarms","[]"))
                return (0 until records.length()).map { records.getJSONObject(it) }.single { it.optString("id")==marker }.getLong("fire")
            }
            val old=fire()
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Tokyo"))
            ReminderScheduler.forceReschedule(real)
            val expected=rule.next(start,System.currentTimeMillis())!!.fireTime
            assertNotEquals(old,expected)
            assertEquals("Reschedule must recompute future wall time",expected,fire())
        } finally {
            java.util.TimeZone.setDefault(previousZone)
            if(id>0) NoteStore(real).use { it.delete(id); it.purgeTrash(id) }
            PlayEntitlementSource.setSnapshot(real,previous)
        }
    }
    @Test fun recurringDeliveryIsClaimedOnceAndPausedAfterExpiry() {
        // AlarmManager and PendingIntent require a real package identity. Keep only this
        // integration case in the real store, using a unique reminder and removing it.
        val previous=EntitlementManager.snapshot(real)
        val marker=UUID.randomUUID().toString()
        val time=System.currentTimeMillis()+5000L
        val rule=ReminderRule(Repeat.DAILY)
        var id=0L
        try {
            PlayEntitlementSource.setSnapshot(real,EntitlementSnapshot(subscriptionActive=true))
            val raw=JSONObject().put("reminders",JSONArray().put(rule.write(JSONObject().put("id",marker).put("at",0).put("time",time)))).toString()
            id=NoteStore(real).use { it.save(Note(body="\uFFFC repeat test",formatting=raw)) }
            ReminderScheduler.forceReschedule(real)
            val intent=Intent(real,ReminderReceiver::class.java).setAction(ReminderScheduler.ACTION_FIRE)
                .putExtra("noteId",id).putExtra("reminderId",marker).putExtra("fire",time)
                .putExtra("signature",rule.fingerprint(time)).putExtra("snooze",false)
            assertNull(ReminderAlarms.consume(real,intent))
            val manager=real.getSystemService(android.app.NotificationManager::class.java)
            val deadline=System.currentTimeMillis()+20000L
            while(manager.activeNotifications.none { it.id==ReminderScheduler.notificationId(id,marker) } && System.currentTimeMillis()<deadline) Thread.sleep(100)
            val notification=manager.activeNotifications.firstOrNull { it.id==ReminderScheduler.notificationId(id,marker) }?.notification
            assertNotNull("Recurring notification must actually fire",notification)
            assertEquals(2,notification!!.actions.size)
            assertNull("Delivery must not repeat",ReminderAlarms.consume(real,intent))
            fun scheduled()=JSONArray(real.getSharedPreferences("reminder_v2",0).getString("alarms","[]"))
            fun matching(): List<JSONObject> = scheduled().let { a -> (0 until a.length()).map { a.getJSONObject(it) }.filter { it.optString("id")==marker } }
            assertEquals(1,matching().size); assertTrue(matching().single().getLong("fire")>time)
            notification.actions[0].actionIntent.send()
            val snoozeDeadline=System.currentTimeMillis()+5000
            while(matching().none { it.optBoolean("snooze") } && System.currentTimeMillis()<snoozeDeadline) Thread.sleep(50)
            assertEquals(2,matching().size)
            PlayEntitlementSource.setSnapshot(real,EntitlementSnapshot())
            assertTrue("Free must stop advanced and snooze alarms",matching().isEmpty())
            assertNull(ReminderAlarms.consume(real,intent))
        } finally {
            if(id>0) NoteStore(real).use { it.delete(id); it.purgeTrash(id) }
            real.getSystemService(android.app.NotificationManager::class.java).cancel(ReminderScheduler.notificationId(id,marker))
            PlayEntitlementSource.setSnapshot(real,previous)
        }
    }
}
