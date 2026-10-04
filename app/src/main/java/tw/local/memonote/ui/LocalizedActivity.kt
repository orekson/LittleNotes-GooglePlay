package tw.local.memonote.ui

import android.app.Activity
import android.content.Context
import android.os.Bundle

open class LocalizedActivity : Activity() {
    private var displayedLanguage = ""

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        displayedLanguage = AppLanguage.code(this)
    }

    override fun onResume() {
        super.onResume()
        tw.local.memonote.entitlement.EntitlementManager.refresh(this)
        if (displayedLanguage != AppLanguage.code(this)) recreate()
    }
}