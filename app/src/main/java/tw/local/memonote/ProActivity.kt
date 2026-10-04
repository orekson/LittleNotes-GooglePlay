package tw.local.memonote

import android.os.Bundle
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import tw.local.memonote.entitlement.EntitlementManager
import tw.local.memonote.ui.LocalizedActivity
import tw.local.memonote.ui.ProFeatureDemoView
import tw.local.memonote.ui.Ui

/** One honest overview of available Pro features and planned additions. */
class ProActivity : LocalizedActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        showPage()
    }

    private fun showPage() {
        val root = Ui.root(this)
        val scroll = ScrollView(this)
        val content = Ui.column(this)
        Ui.pad(content, 24)
        scroll.addView(content)
        root.addView(scroll)
        content.addView(Ui.button(this, "返回") { finish() })
        content.addView(Ui.space(this, 12))
        val hero = Ui.column(this).apply {
            val inset = Ui.dp(this@ProActivity, 20)
            setPadding(inset, inset, inset, inset)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xffeee4ff.toInt(), 0xfffbf8ff.toInt(), 0xffffffff.toInt())).apply {
                cornerRadius = Ui.dp(this@ProActivity, 24).toFloat()
                setStroke(Ui.dp(this@ProActivity, 1), 0xffe5d9f6.toInt())
            }
            elevation = Ui.dp(this@ProActivity, 3).toFloat()
        }
        hero.addView(Ui.label(this, "LittleNotes Pro", 30f, bold = true))
        hero.addView(Ui.space(this, 8))
        val status = Ui.label(this,
            if (EntitlementManager.snapshot(this).isPro) "目前已解鎖 Pro" else "目前使用 Free",
            14f, Ui.purple, true)
        status.setPadding(Ui.dp(this, 12), Ui.dp(this, 7), Ui.dp(this, 12), Ui.dp(this, 7))
        status.background = Ui.rounded(0xffe8dcfa.toInt(), 18f)
        hero.addView(status)
        hero.addView(Ui.space(this, 12))
        hero.addView(Ui.label(this, "用動態預覽認識每項功能", 14f, Ui.muted))
        content.addView(hero)
        content.addView(Ui.space(this, 18))

        section(content, "Widget Pro", ProFeatureDemoView.Demo.WIDGET,
            "Free：不限數量的桌面 Widget、自訂 Widget 背景顏色、日期排程。",
            "Pro：透明度、圓角、內距、行距、字型、字級與圖片顯示選項。")
        section(content, "Cloud Pro", ProFeatureDemoView.Demo.CLOUD,
            "Free：Google Drive 自動備份。",
            "將解鎖：背景同步、多裝置同步與自動恢復。")
        section(content, "Reminder Pro", ProFeatureDemoView.Demo.REMINDER,
            "Free：單次日期與時間提醒，可編輯與取消。",
            "Pro：每天、平日、每週、每月重複；自訂間隔、結束日期、提前通知與稍後提醒。")
        section(content, "Appearance Pro", ProFeatureDemoView.Demo.APPEARANCE,
            "Free：自訂筆記背景、彩虹與柔光文字。",
            "將解鎖：更多主題、OLED 黑色與自訂字體。")
        section(content, "Version History", ProFeatureDemoView.Demo.HISTORY,
            "Pro：最近 50 個儲存版本、版本預覽與還原；回收桶保留 30 天，可還原誤刪筆記。")

        if (!EntitlementManager.snapshot(this).isPro) {
            content.addView(Ui.label(this, "付費解鎖尚未開放；目前不會收費。", 14f, Ui.muted))
            content.addView(Ui.space(this, 10))
        }
    }

    private fun section(content: LinearLayout, title: String, demo: ProFeatureDemoView.Demo,
                        vararg lines: String) {
        val card = Ui.column(this)
        Ui.pad(card, 16)
        card.background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xfff1eaff.toInt(), 0xffffffff.toInt(), 0xfffdfbff.toInt())).apply {
            cornerRadius = Ui.dp(this@ProActivity, 22).toFloat()
            setStroke(Ui.dp(this@ProActivity, 1), 0xffe9e1f3.toInt())
        }
        card.elevation = Ui.dp(this, 2).toFloat()
        val heading = Ui.row(this)
        val mark = Ui.label(this, "✦", 15f, Ui.purple, true)
        mark.gravity = Gravity.CENTER
        mark.background = Ui.rounded(0xffe8dcfa.toInt(), 18f)
        val markSize = Ui.dp(this, 30)
        heading.addView(mark, LinearLayout.LayoutParams(markSize, markSize))
        val titleLabel = Ui.label(this, title, 19f, Ui.purple, true)
        val titleParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        titleParams.leftMargin = Ui.dp(this, 10)
        heading.addView(titleLabel, titleParams)
        card.addView(heading)
        card.addView(Ui.space(this, 14))
        card.addView(ProFeatureDemoView(this, demo),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 94)))
        lines.forEach { line -> addFeatureLine(card, line) }
        content.addView(card)
        content.addView(Ui.space(this, 12))
    }

    private fun addFeatureLine(card: LinearLayout, line: String) {
        card.addView(Ui.space(this, 10))
        val separator = line.indexOf('：')
        if (separator <= 0) {
            card.addView(Ui.label(this, line, 14f, Ui.ink))
            return
        }
        val tagText = line.substring(0, separator)
        val isUnlock = tagText == "將解鎖" || tagText == "Pro"
        val tag = Ui.label(this, tagText, 10f,
            if (isUnlock) Ui.purple else 0xff527d6b.toInt(), true)
        tag.setPadding(Ui.dp(this, 9), Ui.dp(this, 5), Ui.dp(this, 9), Ui.dp(this, 5))
        tag.background = Ui.rounded(if (isUnlock) 0xffeee5fc.toInt() else 0xffe6f3ed.toInt(), 16f)
        val row = Ui.row(this)
        row.gravity = Gravity.TOP
        row.addView(tag)
        row.addView(android.view.View(this), LinearLayout.LayoutParams(Ui.dp(this, 8), 1))
        row.addView(Ui.label(this, line.substring(separator + 1), 13f, Ui.ink),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(row)
    }
}
