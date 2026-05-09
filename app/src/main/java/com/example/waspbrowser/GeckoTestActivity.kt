package com.example.waspbrowser

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

class GeckoTestActivity : AppCompatActivity() {

    companion object {
        private var runtime: GeckoRuntime? = null
        private const val TAG = "GeckoTest"
    }

    private lateinit var geckoView: GeckoView
    private lateinit var geckoSession: GeckoSession

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate start")

        setContentView(R.layout.activity_gecko_test)

        geckoView = findViewById(R.id.geckoView)

        if (runtime == null) {
            runtime = GeckoRuntime.create(this)
        }

        geckoSession = GeckoSession()
        geckoSession.open(runtime!!)
        geckoView.setSession(geckoSession)

        geckoSession.loadUri("resource://android/assets/bee/bee.html")
    }
}