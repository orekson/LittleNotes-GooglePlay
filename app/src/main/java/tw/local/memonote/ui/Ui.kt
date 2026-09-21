package tw.local.memonote.ui

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.*
import android.widget.*

object Ui {
    val ink=0xff302b3e.toInt(); val purple=0xff7954b3.toInt(); val muted=0xff82778f.toInt(); val page=0xfffaf7ff.toInt()
    fun dp(c: Context,n: Int)=(n*c.resources.displayMetrics.density).toInt()
    fun rounded(color: Int,radius: Float=18f)=GradientDrawable().apply { setColor(color); cornerRadius=radius }
    fun label(c: Context,text: String,size: Float=16f,color: Int=ink,bold: Boolean=false)=TextView(c).apply {
        this.text=text; textSize=size; setTextColor(color); if(bold) typeface=Typeface.create("sans-serif",Typeface.BOLD)
    }
    fun button(c: Context,text: String,primary: Boolean=false,click: ()->Unit)=Button(c).apply {
        this.text=text; isAllCaps=false; textSize=14f; minHeight=dp(c,46); minimumHeight=dp(c,46)
        setTextColor(if(primary) Color.WHITE else purple); backgroundTintList=android.content.res.ColorStateList.valueOf(if(primary) purple else 0xffeee5fb.toInt())
        setOnClickListener { click() }
    }
    fun column(c: Context)=LinearLayout(c).apply { orientation=LinearLayout.VERTICAL }
    fun row(c: Context)=LinearLayout(c).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
    fun space(c: Context,height: Int)=Space(c).apply { layoutParams=LinearLayout.LayoutParams(1,dp(c,height)) }
    fun root(a: Activity): LinearLayout {
        a.window.statusBarColor=page; a.window.navigationBarColor=page
        a.window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        if(Build.VERSION.SDK_INT>=30) a.window.setDecorFitsSystemWindows(false)
        return column(a).apply {
            setBackgroundColor(page)
            setOnApplyWindowInsetsListener { v,insets ->
                if(Build.VERSION.SDK_INT>=30) { val b=insets.getInsets(WindowInsets.Type.systemBars()); v.setPadding(b.left,b.top,b.right,b.bottom) }
                else v.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom)
                insets
            }
            a.setContentView(this)
        }
    }
    fun pad(v: View,n: Int) { val p=dp(v.context,n); v.setPadding(p,p,p,p) }
    fun toast(c: Context,text: String)=Toast.makeText(c,text,Toast.LENGTH_LONG).show()
}
