package tw.local.memonote.ui

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.widget.LinearLayout
import tw.local.memonote.MainActivity
import tw.local.memonote.DateScheduleActivity
import tw.local.memonote.CloudProfileActivity

object BottomNavigation {
    enum class Tab { NOTES, DATE, PROFILE }
    private const val BOUNCE="bottom_navigation_bounce"

    @Suppress("DEPRECATION")
    private fun open(activity: Activity, tab: Tab) {
        val target=when(tab) {
            Tab.NOTES -> MainActivity::class.java
            Tab.DATE -> DateScheduleActivity::class.java
            Tab.PROFILE -> CloudProfileActivity::class.java
        }
        activity.startActivity(Intent(activity,target).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION).putExtra(BOUNCE,true),
            ActivityOptions.makeCustomAnimation(activity,0,0).toBundle())
        activity.overridePendingTransition(0,0)
    }
    fun add(activity: Activity, root: LinearLayout, selected: Tab) {
        val bar=Ui.row(activity).apply {
            setPadding(Ui.dp(activity,12),Ui.dp(activity,8),Ui.dp(activity,12),Ui.dp(activity,8))
            setBackgroundColor(0xffffffff.toInt());elevation=Ui.dp(activity,6).toFloat()
        }
        val animateSelected=activity.intent.getBooleanExtra(BOUNCE,false)
        activity.intent.removeExtra(BOUNCE)
        listOf("✦  筆記" to Tab.NOTES,"▦  日期" to Tab.DATE,"☺  個人" to Tab.PROFILE).forEach { (label,tab) ->
            bar.addView(Ui.bouncingButton(activity,label,selected==tab) {
                if(tab!=selected) open(activity,tab)
            }.apply {
                isSelected=selected==tab
                contentDescription=AppLanguage.text(activity,when(tab) {
                    Tab.NOTES -> "筆記頁"
                    Tab.DATE -> "日期頁"
                    Tab.PROFILE -> "個人與設定頁"
                })
                if(animateSelected && isSelected) (this as BounceButton).bounce()
            },LinearLayout.LayoutParams(0,Ui.dp(activity,48),1f))
        }
        root.addView(bar)
    }
}
