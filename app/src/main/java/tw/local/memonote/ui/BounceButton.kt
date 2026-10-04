package tw.local.memonote.ui

import android.content.Context
import android.view.MotionEvent
import android.view.animation.OvershootInterpolator
import android.widget.Button

/** Keeps normal Android click, cancellation, ripple and accessibility behavior. */
class BounceButton(context: Context) : Button(context) {
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isEnabled) when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                animate().cancel()
                animate().scaleX(.94f).scaleY(.94f).setDuration(70).start()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> rebound()
        }
        return super.onTouchEvent(event)
    }
    fun bounce() {
        animate().cancel();scaleX=.96f;scaleY=.96f;rebound()
    }
    private fun rebound() {
        animate().cancel()
        animate().scaleX(1f).scaleY(1f).setDuration(180)
            .setInterpolator(OvershootInterpolator(1.5f)).start()
    }
    override fun performClick(): Boolean {
        if(isEnabled) { if(scaleX>=.999f) bounce() else rebound() }
        return super.performClick()
    }
    override fun onDetachedFromWindow() {
        animate().cancel();scaleX=1f;scaleY=1f
        super.onDetachedFromWindow()
    }
}
