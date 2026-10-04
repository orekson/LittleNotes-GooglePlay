package tw.local.memonote

import android.content.Context
import android.content.ContextWrapper
import android.app.job.JobScheduler
import android.text.SpannableStringBuilder
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import tw.local.memonote.entitlement.*
import tw.local.memonote.data.*
import tw.local.memonote.cloud.*
import tw.local.memonote.rich.*
import tw.local.memonote.model.TextStyle
import tw.local.memonote.widget.*
import tw.local.memonote.reminder.ReminderCodec
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class PlayEntitlementTest {
    private val real get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun isolated(): Context = object : ContextWrapper(real) {
        override fun getApplicationContext(): Context = this
        override fun getPackageName() = real.packageName + ".entitlementtest"
        override fun getSharedPreferences(name: String, mode: Int) = real.getSharedPreferences("premiumtest-" + name, mode)
    }
    @Test fun freeWidgetAppearanceKeepsPresetsAndGrandfathersProColors() {
        val c = isolated()
        val widgetId = 52424
        val custom = tw.local.memonote.model.NoteColors.backgroundRef(0xffabcdef.toInt())
        try {
            PlayEntitlementSource.setSnapshot(c, EntitlementSnapshot())
            WidgetAppearance.set(c, widgetId, "ocean")
            assertEquals("ocean", WidgetAppearance.background(c, widgetId, "paper"))
            assertEquals(android.graphics.Color.WHITE, WidgetAppearance.textColor("night"))
            assertEquals(0xff302b3e.toInt(), WidgetAppearance.textColor("paper"))
            var blocked = false
            try { WidgetAppearance.set(c, widgetId, custom) }
            catch (_: ProRequiredException) { blocked = true }
            assertTrue(blocked)
            PlayEntitlementSource.setSnapshot(c, EntitlementSnapshot(subscriptionActive = true))
            WidgetAppearance.set(c, widgetId, custom)
            PlayEntitlementSource.setSnapshot(c, EntitlementSnapshot())
            assertEquals(custom, WidgetAppearance.background(c, widgetId, "paper"))
            WidgetAppearance.set(c, widgetId, custom)
            WidgetAppearance.set(c, widgetId, "paper")
            assertEquals("paper", WidgetAppearance.background(c, widgetId, "night"))
        } finally {
            WidgetAppearance.forget(c, intArrayOf(widgetId))
            PlayEntitlementSource.setSnapshot(c, EntitlementSnapshot())
        }
    }
    @Test fun thirdWidgetShowsProPromptWhileExistingWidgetsStayConfigurable() {
        val c = real
        val previous = EntitlementManager.snapshot(c)
        val language = tw.local.memonote.ui.AppLanguage.code(c)
        tw.local.memonote.ui.AppLanguage.set(c, "zh-TW")
        val host = android.appwidget.AppWidgetHost(c, 52425)
        val manager = android.appwidget.AppWidgetManager.getInstance(c)
        val provider = android.content.ComponentName(c, NoteWidgetProvider::class.java)
        val ids = mutableListOf<Int>()
        val noteId = NoteStore(c).use { it.save(Note(title = "widget quota test")) }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        try {
            automation.adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET")
            PlayEntitlementSource.setSnapshot(c, EntitlementSnapshot(lifetimePro = true))
            repeat(3) {
                val id = host.allocateAppWidgetId()
                ids += id
                assertTrue(manager.bindAppWidgetIdIfAllowed(id, provider))
            }
            DateWidgetSchedule.manualBind(c, ids[0], noteId)
            DateWidgetSchedule.manualBind(c, ids[1], noteId)
            PlayEntitlementSource.setSnapshot(c, EntitlementSnapshot())
            assertTrue(WidgetAccess.canConfigure(c, ids[0]))
            assertFalse(WidgetAccess.canConfigure(c, ids[2]))
            androidx.test.core.app.ActivityScenario.launch<WidgetConfigActivity>(
                android.content.Intent(c, WidgetConfigActivity::class.java)
                    .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, ids[2])
            ).use { scenario ->
                scenario.onActivity { activity ->
                    fun all(view: android.view.View): List<android.view.View> = listOf(view) +
                        if (view is android.view.ViewGroup)
                            (0 until view.childCount).flatMap { all(view.getChildAt(it)) }
                        else emptyList()
                    val labels = all(activity.window.decorView).filterIsInstance<android.widget.TextView>()
                        .map { it.text.toString() }
                    assertTrue(labels.any { it.contains("免費版最多可建立 2 個") })
                    assertTrue(labels.any { it == "查看 Pro" })
                }
            }
            assertEquals(noteId, NoteWidgetProvider.noteId(c, ids[0]))
            assertEquals(noteId, NoteWidgetProvider.noteId(c, ids[1]))
            PlayEntitlementSource.setSnapshot(c, EntitlementSnapshot(subscriptionActive = true))
            assertTrue(WidgetAccess.canConfigure(c, ids[2]))
            DateWidgetSchedule.manualBind(c, ids[2], noteId)
            PlayEntitlementSource.setSnapshot(c, EntitlementSnapshot())
            assertEquals(noteId, NoteWidgetProvider.noteId(c, ids[2]))
        } finally {
            ids.forEach { id -> host.deleteAppWidgetId(id) }
            automation.dropShellPermissionIdentity()
            NoteStore(c).use { it.delete(noteId) }
            PlayEntitlementSource.setSnapshot(c, previous)
            tw.local.memonote.ui.AppLanguage.set(c, language)
        }
    }
    @Test fun freeProPageDescribesRealAndComingSoonFeaturesWithoutPurchaseAction() {
        val c = real
        val previous = EntitlementManager.snapshot(c)
        val language = tw.local.memonote.ui.AppLanguage.code(c)
        tw.local.memonote.ui.AppLanguage.set(c, "zh-TW")
        PlayEntitlementSource.setSnapshot(c, EntitlementSnapshot())
        try {
            androidx.test.core.app.ActivityScenario.launch<ProActivity>(
                android.content.Intent(c, ProActivity::class.java)
            ).use { scenario ->
                scenario.onActivity { activity ->
                    fun all(view: android.view.View): List<android.view.View> = listOf(view) +
                        if (view is android.view.ViewGroup)
                            (0 until view.childCount).flatMap { all(view.getChildAt(it)) }
                        else emptyList()
                    val texts = all(activity.window.decorView).filterIsInstance<android.widget.TextView>()
                        .map { it.text.toString() }
                    assertTrue(texts.contains("Widget Pro"))
                    assertTrue(texts.contains("Reminder Pro"))
                    assertTrue(texts.any { it.contains("將解鎖") })
                    assertTrue(texts.any { it=="Free" })
                    assertTrue(texts.any { it=="Pro" })
                    assertTrue(texts.any { it.contains("付費解鎖尚未開放") })
                    assertFalse(texts.any { it.contains("購買成功") })
                }
            }
        } finally {
            PlayEntitlementSource.setSnapshot(c, previous)
            tw.local.memonote.ui.AppLanguage.set(c, language)
        }
    }
    @Test fun proToFreePreservesNotesBackgroundsRemindersAndDateSchedules() {
        val c = isolated()
        val body = "\uFFFC pay bill"
        val reminderId=java.util.UUID.randomUUID().toString()
        val raw = """{"styles":[{"start":2,"end":5,"rainbow":true,"glow":true}],"reminders":[{"id":"$reminderId","at":0,"time":${System.currentTimeMillis()+3600000}}]}"""
        PlayEntitlementSource.setSnapshot(c, EntitlementSnapshot(subscriptionActive = true))
        val id = NoteStore(c).use { it.save(Note(title="pro", body=body, formatting=raw, background="file:existing.png", fade=67)) }
        val date = LocalDate.now().plusDays(1)
        try {
            DateWidgetSchedule.assign(c, 424242, date, id)
            PlayEntitlementSource.setSnapshot(c, EntitlementSnapshot())
            val old = NoteStore(c).use { it.find(id)!! }
            assertEquals(raw, old.formatting)
            assertEquals("file:existing.png", old.background)
            assertEquals(id, DateWidgetSchedule.assignments(c,424242)[date])
            assertNotNull(DateWidgetSchedule.dueAssignment(mapOf(date to id),Long.MIN_VALUE,date))
            assertTrue(ReminderCodec.all(old).isNotEmpty())
            NoteStore(c).use { it.saveFromEditor(old,old.copy(title="free edit",body=body+" more")) }
            val text = RichText.decode(c, old.body, old.formatting,76)
            assertTrue(text.getSpans(0,text.length,PaintSpan::class.java).single().style.rainbow)
            var blocked = false
            try { NoteStore(c).use { it.saveFromEditor(old,old.copy(fade=68)) } }
            catch (_: ProRequiredException) { blocked=true }
            assertTrue("free may not modify photo settings",blocked)
            blocked=false
            try { DateWidgetSchedule.assign(c,424242,date,id) }
            catch (_: ProRequiredException) { blocked=true }
            assertTrue("free may not modify a schedule",blocked)
            DateWidgetSchedule.remove(c,424242,date)
            assertTrue(DateWidgetSchedule.assignments(c,424242).isEmpty())
            NoteStore(c).use { it.saveFromEditor(old,old.copy(background="paper",fade=35,formatting="{}",body="plain")) }
            assertEquals("plain", NoteStore(c).use { it.find(id)!!.body })
        } finally {
            DateWidgetSchedule.remove(c,424242,date)
            NoteStore(c).use { it.delete(id) }
            PlayEntitlementSource.setSnapshot(c,EntitlementSnapshot())
        }
    }
    @Test fun realWidgetRefreshExecutesExistingScheduleAfterExpiry() {
        val c=real
        val previous=EntitlementManager.snapshot(c)
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val host=android.appwidget.AppWidgetHost(c,424243)
        var widget=-1
        val first=NoteStore(c).use { it.save(Note(title="first",body="one")) }
        val second=NoteStore(c).use { it.save(Note(title="second",body="two")) }
        try {
            instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET")
            widget=host.allocateAppWidgetId()
            assertTrue(android.appwidget.AppWidgetManager.getInstance(c).bindAppWidgetIdIfAllowed(widget,
                android.content.ComponentName(c,NoteWidgetProvider::class.java)))
            PlayEntitlementSource.setSnapshot(c,EntitlementSnapshot(subscriptionActive=true))
            DateWidgetSchedule.assign(c,widget,LocalDate.now(),second)
            // Simulate a persisted, not-yet-applied assignment from before restart.
            NoteWidgetProvider.bind(c,widget,first)
            c.getSharedPreferences("widget_dates",0).edit().putLong("applied:$widget",LocalDate.now().minusDays(1).toEpochDay()).commit()
            PlayEntitlementSource.setSnapshot(c,EntitlementSnapshot())
            DateWidgetSchedule.refresh(c)
            assertEquals(second,NoteWidgetProvider.noteId(c,widget))
        } finally {
            if(widget>=0) { DateWidgetSchedule.forget(c,intArrayOf(widget)); host.deleteAppWidgetId(widget) }
            instrumentation.uiAutomation.dropShellPermissionIdentity()
            NoteStore(c).use { it.delete(first); it.delete(second) }
            PlayEntitlementSource.setSnapshot(c,previous)
        }
    }
    @Test fun premiumReminderStillNotifiesAfterSubscriptionExpires() {
        val c=real
        val previous=EntitlementManager.snapshot(c)
        val marker=java.util.UUID.randomUUID().toString()
        val time=System.currentTimeMillis()+5000L
        PlayEntitlementSource.setSnapshot(c,EntitlementSnapshot(subscriptionActive=true))
        val id=NoteStore(c).use { it.save(Note(body="\uFFFC expired reminder",
            formatting="""{"reminders":[{"at":0,"id":"$marker","time":$time}]}""")) }
        val manager=c.getSystemService(android.app.NotificationManager::class.java)
        try {
            tw.local.memonote.reminder.ReminderScheduler.forceReschedule(c)
            PlayEntitlementSource.setSnapshot(c,EntitlementSnapshot())
            var posted: android.app.Notification?=null
            val deadline=System.currentTimeMillis()+20000L
            while(posted==null && System.currentTimeMillis()<deadline) {
                Thread.sleep(250)
                posted=manager.activeNotifications.firstOrNull { it.id==tw.local.memonote.reminder.ReminderScheduler.notificationId(id,marker) }?.notification
            }
            assertNotNull("existing reminder must fire after expiry",posted)
            assertEquals("expired reminder",posted!!.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString().trim())
        } finally {
            NoteStore(c).use { it.delete(id) }
            manager.cancel(tw.local.memonote.reminder.ReminderScheduler.notificationId(id,marker))
            PlayEntitlementSource.setSnapshot(c,previous)
        }
    }
    @Test fun freeEditorKeepsContentEditableAndShowsProButtons() {
        val c=real
        val previous=EntitlementManager.snapshot(c)
        val language=tw.local.memonote.ui.AppLanguage.code(c)
        tw.local.memonote.ui.AppLanguage.set(c,"zh-TW")
        PlayEntitlementSource.setSnapshot(c,EntitlementSnapshot())
        val id=NoteStore(c).use { it.save(Note(title="free editor",body="ordinary text")) }
        try {
            androidx.test.core.app.ActivityScenario.launch<EditorActivity>(android.content.Intent(c,EditorActivity::class.java).putExtra("noteId",id)).use { scenario ->
                scenario.onActivity { activity ->
                    fun views(view: android.view.View): List<android.view.View> = listOf(view) +
                        if(view is android.view.ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
                    val buttons=views(activity.window.decorView).filterIsInstance<android.widget.Button>()
                    val rainbow=buttons.first { it.text.toString().contains("彩虹") || it.text.toString().contains("Rainbow") }
                    assertTrue(rainbow.text.toString().contains("Pro"))
                    val reminder=buttons.first { it.text.toString().contains("時間提醒") }
                    assertFalse(reminder.text.toString().contains("Pro"))
                    val field=EditorActivity::class.java.getDeclaredField("body").apply { isAccessible=true }
                    val body=field.get(activity) as android.widget.EditText
                    assertTrue(body.isEnabled)
                    body.append(" edit")
                    assertEquals("ordinary text edit",body.text.toString())
                    rainbow.performClick()
                    assertTrue(body.text.getSpans(0,body.length(),PaintSpan::class.java).isEmpty())
                }
                InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                scenario.onActivity { activity ->
                    fun all(view: android.view.View): List<android.view.View> = listOf(view) +
                        if(view is android.view.ViewGroup) (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()
                    all(activity.window.decorView).filterIsInstance<android.widget.Button>()
                        .first { it.text.toString()=="儲存" }.performClick()
                }
            }
            assertEquals("ordinary text edit",NoteStore(c).use { it.find(id)!!.body })
        } finally { NoteStore(c).use { it.delete(id) }; PlayEntitlementSource.setSnapshot(c,previous); tw.local.memonote.ui.AppLanguage.set(c,language) }
    }
    @Test fun subscriptionAndLifetimeEditorsCanApplyAndSavePremiumSettings() {
        val c=real
        val previous=EntitlementManager.snapshot(c)
        val language=tw.local.memonote.ui.AppLanguage.code(c)
        tw.local.memonote.ui.AppLanguage.set(c,"zh-TW")
        try {
            for(state in listOf(EntitlementSnapshot(subscriptionActive=true),EntitlementSnapshot(lifetimePro=true))) {
                PlayEntitlementSource.setSnapshot(c,state)
                val id=NoteStore(c).use { it.save(Note(body="pro editor")) }
                val image=java.io.File.createTempFile("pro-background-",".png",c.cacheDir)
                var imported=""
                try {
                    val bitmap=android.graphics.Bitmap.createBitmap(10,10,android.graphics.Bitmap.Config.ARGB_8888)
                    image.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
                    androidx.test.core.app.ActivityScenario.launch<EditorActivity>(android.content.Intent(c,EditorActivity::class.java).putExtra("noteId",id)).use { scenario ->
                        scenario.onActivity { activity ->
                            fun all(view: android.view.View): List<android.view.View> = listOf(view) +
                                if(view is android.view.ViewGroup) (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()
                            val buttons=all(activity.window.decorView).filterIsInstance<android.widget.Button>()
                            assertFalse(buttons.any { it.text.toString().contains("Pro") })
                            buttons.first { it.text.toString()=="彩虹" }.performClick()
                            buttons.first { it.text.toString()=="柔光" }.performClick()
                            val method=EditorActivity::class.java.getDeclaredMethod("onActivityResult",Int::class.javaPrimitiveType,Int::class.javaPrimitiveType,android.content.Intent::class.java).apply { isAccessible=true }
                            method.invoke(activity,11,android.app.Activity.RESULT_OK,android.content.Intent().setData(android.net.Uri.fromFile(image)))
                            imported=EditorActivity::class.java.getDeclaredField("background").apply { isAccessible=true }.get(activity) as String
                            assertTrue(imported.startsWith("file:"))
                            EditorActivity::class.java.getDeclaredField("fade").apply { isAccessible=true }.setInt(activity,72)
                            buttons.first { it.text.toString()=="儲存" }.performClick()
                        }
                    }
                    val saved=NoteStore(c).use { it.find(id)!! }
                    assertEquals(imported,saved.background)
                    assertEquals(72,saved.fade)
                    val decoded=RichText.decode(c,saved.body,saved.formatting,76)
                    assertTrue(decoded.getSpans(0,decoded.length,PaintSpan::class.java).single().style.glow)
                    assertTrue(decoded.getSpans(0,decoded.length,PaintSpan::class.java).single().style.rainbow)
                } finally {
                    NoteStore(c).use { it.delete(id) }; image.delete()
                    if(imported.startsWith("file:")) java.io.File(c.filesDir,imported.removePrefix("file:")).delete()
                }
            }
        } finally { PlayEntitlementSource.setSnapshot(c,previous); tw.local.memonote.ui.AppLanguage.set(c,language) }
    }
    @Test fun freeStopsAutomaticJobsButPreservesManualBackupAndCredentials() {
        val c=isolated()
        try {
            PlayEntitlementSource.setSnapshot(c,EntitlementSnapshot(lifetimePro=true))
            CloudBackupState.connect(c,"test@example.com","abcde".toCharArray())
            CloudBackupJob.schedule(c)
            val scheduler=c.getSystemService(JobScheduler::class.java)
            scheduler.schedule(android.app.job.JobInfo.Builder(51341,
                android.content.ComponentName(real,CloudBackupJob::class.java))
                .setRequiresDeviceIdle(true).setExtras(android.os.PersistableBundle().apply {
                    putBoolean("manual",true)
                }).build())
            assertTrue(scheduler.allPendingJobs.any { !it.extras.getBoolean("manual") })
            PlayEntitlementSource.setSnapshot(c,EntitlementSnapshot())
            assertFalse(scheduler.allPendingJobs.any { !it.extras.getBoolean("manual") })
            assertTrue(scheduler.allPendingJobs.any { it.extras.getBoolean("manual") })
            assertFalse(CloudBackupState.enabled(c))
            assertTrue(CloudBackupState.connected(c))
            assertEquals("test@example.com",CloudBackupState.account(c))
            CloudBackupJob.schedule(c)
            assertFalse(scheduler.allPendingJobs.any { !it.extras.getBoolean("manual") })
            assertTrue(CloudBackupJob.permitted(c,manual=true))
            assertTrue(scheduler.allPendingJobs.any { it.extras.getBoolean("manual") })
        } finally { CloudBackupState.disconnect(c); PlayEntitlementSource.setSnapshot(c,EntitlementSnapshot()) }
    }
    @Test fun freeFormattingCanRemoveEffectsAndEditOrdinaryTextButCannotAddEffects() {
        val c=isolated()
        PlayEntitlementSource.setSnapshot(c,EntitlementSnapshot())
        val text=SpannableStringBuilder("existing ordinary")
        RichText.format(text,0,8) { TextStyle(rainbow=true,glow=true) }
        assertFalse(PremiumTextEdits.format(c,text,9,text.length) { it.copy(glow=true) })
        assertTrue(PremiumTextEdits.format(c,text,0,8) { TextStyle() })
        assertTrue(text.getSpans(0,text.length,PaintSpan::class.java).isEmpty())
        assertTrue(PremiumTextEdits.format(c,text,9,text.length) { it.copy(color=7) })
        assertEquals(7,text.getSpans(0,text.length,PaintSpan::class.java).single().style.color)
        PlayEntitlementSource.setSnapshot(c,EntitlementSnapshot(lifetimePro=true))
        assertTrue(PremiumTextEdits.format(c,text,0,8) { it.copy(rainbow=true,glow=true) })
        PlayEntitlementSource.setSnapshot(c,EntitlementSnapshot())
        text.insert(3,"new")
        PremiumTextEdits.sanitizeInsertion(c,text,3,3)
        val spans=text.getSpans(3,6,PaintSpan::class.java)
        assertTrue(spans.none { it.style.rainbow || it.style.glow })
        assertTrue(text.getSpans(0,3,PaintSpan::class.java).any { it.style.rainbow })
    }
}
