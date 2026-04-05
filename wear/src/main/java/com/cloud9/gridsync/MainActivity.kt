package com.cloud9.gridsync

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.ImageView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()

        val splashStartTime = SystemClock.elapsedRealtime()
        splashScreen.setKeepOnScreenCondition {
            SystemClock.elapsedRealtime() - splashStartTime < 1500
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val logo = findViewById<ImageView>(R.id.logo)

        logo.scaleX = 1f
        logo.scaleY = 1f
        logo.alpha = 1f

        Handler(Looper.getMainLooper()).postDelayed({
            logo.animate()
                .scaleX(1.12f)
                .scaleY(1.12f)
                .setDuration(1200)
                .withEndAction {
                    Handler(Looper.getMainLooper()).postDelayed({
                        startActivity(Intent(this, WatchDashboardActivity::class.java))
                        overridePendingTransition(0, 0)
                        finish()
                    }, 1300)
                }
                .start()
        }, 0)
    }
}