package com.cloud9.gridsync

import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.abs

/**
 * Base class for every screen that has a back button.
 *
 * Adds a left-to-right swipe gesture that does exactly what the back button does
 * (finish the screen). The on-screen "<" back button stays as a second option.
 */
open class SwipeBackActivity : AppCompatActivity() {

    /**
     * Width (dp) of the strip along the left edge where a back-swipe may start.
     * 0 means "the left 35% of the screen". Screens with a drawing canvas use a
     * narrow edge strip so normal drawing never triggers a back navigation.
     */
    protected open val swipeStartZoneDp: Int = 0

    private var swipeTracking = false
    private var swipeStartX = 0f
    private var swipeStartY = 0f

    protected fun dpToPx(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    /** Same action as the back button. */
    protected fun goBack() {
        if (!isFinishing) finish()
    }

    @Suppress("DEPRECATION")
    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val zone = if (swipeStartZoneDp > 0) {
                    dpToPx(swipeStartZoneDp)
                } else {
                    (resources.displayMetrics.widthPixels * 0.35f).toInt()
                }
                swipeTracking = ev.rawX <= zone
                swipeStartX = ev.rawX
                swipeStartY = ev.rawY
            }

            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_CANCEL -> swipeTracking = false

            MotionEvent.ACTION_UP -> {
                if (swipeTracking) {
                    swipeTracking = false
                    val dx = ev.rawX - swipeStartX
                    val dy = abs(ev.rawY - swipeStartY)

                    // Long enough, and mostly horizontal.
                    if (dx >= dpToPx(96) && dy <= dx * 0.6f) {
                        // Let the touched child clean up its pressed state first.
                        val cancel = MotionEvent.obtain(ev)
                        cancel.action = MotionEvent.ACTION_CANCEL
                        super.dispatchTouchEvent(cancel)
                        cancel.recycle()

                        goBack()
                        return true
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}
