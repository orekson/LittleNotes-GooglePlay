package tw.local.memonote.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.widget.Button
import android.widget.LinearLayout
import tw.local.memonote.ProActivity
import tw.local.memonote.entitlement.*

/** Shared presentation only. Personal's policy never invokes a restriction or membership prompt. */
object ProUi {
    fun featureName(feature: PremiumFeature): String = when (feature) {
        PremiumFeature.ADVANCED_TEXT -> "彩虹與柔光文字"
        PremiumFeature.ADVANCED_BACKGROUND -> "自訂背景與淡化"
        PremiumFeature.WIDGET_DATE_SCHEDULE -> "Widget 日期排程"
        PremiumFeature.CLOUD_AUTO_BACKUP -> "Google Drive 自動備份"
        PremiumFeature.UNLIMITED_WIDGETS -> "不限數量的桌面 Widget"
        PremiumFeature.ADVANCED_WIDGET_CUSTOMIZATION -> "Widget 完整客製化"
        PremiumFeature.WIDGET_BACKGROUND_COLOR -> "Widget 背景顏色"
        PremiumFeature.RECURRING_REMINDERS -> "重複提醒"
        PremiumFeature.CLOUD_AUTO_SYNC -> "自動雲端同步"
        PremiumFeature.PREMIUM_THEMES -> "完整主題與外觀"
        PremiumFeature.VERSION_HISTORY -> "筆記版本歷史"
    }
    fun label(activity: Activity, text: String, feature: PremiumFeature, showBadge: Boolean = true): String =
        AppLanguage.text(activity, text) + if (FlavorEntitlement.showsProUi &&
            showBadge && !EntitlementManager.allows(activity, feature, FeatureOperation.CREATE)) " · Pro" else ""
    fun button(activity: Activity, text: String, feature: PremiumFeature,
               showBadge: Boolean = true, action: () -> Unit): Button =
        Ui.rawButton(activity, label(activity, text, feature, showBadge), false, action)
    fun require(activity: Activity, feature: PremiumFeature,
                operation: FeatureOperation = FeatureOperation.CREATE): Boolean {
        if (EntitlementManager.allows(activity, feature, operation)) return true
        show(activity, feature)
        return false
    }
    fun show(activity: Activity, feature: PremiumFeature) {
        if (!FlavorEntitlement.showsProUi) return
        val description = when(feature) {
            PremiumFeature.UNLIMITED_WIDGETS ->
                "免費版最多可建立 2 個桌面筆記。\n升級 Pro 可建立不限數量的 Widget。"
            PremiumFeature.ADVANCED_WIDGET_CUSTOMIZATION ->
                "Pro 可設定 Widget 圓角、內距、行距、字型、字級、透明度與圖片顯示。Free 使用預設外觀。"
            PremiumFeature.RECURRING_REMINDERS ->
                "Free 可新增、編輯及取消單次提醒。Pro 可使用重複、提前通知與稍後提醒；切回 Free 後，進階提醒會暫停。"
            PremiumFeature.VERSION_HISTORY ->
                "Pro 可保留最近 50 個儲存版本，並在 30 天內還原回收桶的筆記。Free 不會新增版本紀錄或保留刪除的筆記。"
            else -> "此功能需要 Pro 才能新增或修改。既有內容會保留，仍可檢視、執行或移除。"
        }
        AlertDialog.Builder(activity)
            .setTitle(AppLanguage.text(activity, featureName(feature)))
            .setMessage(AppLanguage.text(activity, description))
            .setNegativeButton(AppLanguage.text(activity, "稍後"), null)
            .setPositiveButton(AppLanguage.text(activity, "查看 Pro")) { _, _ -> openPage(activity) }
            .show()
    }
    fun openPage(activity: Activity) {
        if (FlavorEntitlement.showsProUi) activity.startActivity(Intent(activity, ProActivity::class.java))
    }
    fun addFeatureHint(activity: Activity, column: LinearLayout, text: String) {
        if (FlavorEntitlement.showsProUi) column.addView(Ui.label(activity,text,13f,Ui.muted))
    }
    fun addStatus(activity: Activity, column: LinearLayout) {
        if (!FlavorEntitlement.showsProUi) return
        val text = when (EntitlementManager.snapshot(activity).level) {
            EntitlementLevel.FREE -> "目前方案：Free"
            EntitlementLevel.SUBSCRIPTION_ACTIVE -> "目前方案：Pro 訂閱"
            EntitlementLevel.LIFETIME_PRO -> "目前方案：Pro 永久版"
        }
        column.addView(Ui.label(activity, text, 16f, Ui.purple, true))
        if (!EntitlementManager.snapshot(activity).isPro)
            column.addView(Ui.label(activity, "Pro 付費解鎖尚未開放。", 13f, Ui.muted))
        column.addView(Ui.space(activity, 10))
    }
}
