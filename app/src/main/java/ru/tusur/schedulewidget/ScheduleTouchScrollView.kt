package ru.tusur.schedulewidget

import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.ScrollView
import kotlin.math.abs

/**
 * Keeps normal vertical page scrolling, but handles horizontal day changes
 * when the gesture starts inside the schedule block only.
 */
class ScheduleTouchScrollView(
    context: Context,
    private val onDaySwipe: (Int) -> Unit
) : ScrollView(context) {

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minimumSwipeDistance = 36f * resources.displayMetrics.density
    private val scheduleRect = Rect()

    private var scheduleRegion: View? = null
    private var downX = 0f
    private var downY = 0f
    private var startedInsideSchedule = false
    private var horizontalGestureCaptured = false

    init {
        isFillViewport = false
        clipToPadding = false
    }

    fun setScheduleRegion(view: View) {
        scheduleRegion = view
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                startedInsideSchedule = isInsideSchedule(event.x, event.y)
                horizontalGestureCaptured = false
            }

            MotionEvent.ACTION_MOVE -> {
                if (startedInsideSchedule) {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    // Claim clearly horizontal movement before ScrollView can
                    // mistake a diagonal swipe for vertical page scrolling.
                    if (abs(dx) > touchSlop && abs(dx) > abs(dy) * 1.05f) {
                        horizontalGestureCaptured = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                }
            }
        }

        return super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (horizontalGestureCaptured) {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> return true

                MotionEvent.ACTION_UP -> {
                    val dx = event.x - downX
                    if (abs(dx) >= minimumSwipeDistance) {
                        // Left = next day; right = previous day.
                        onDaySwipe(if (dx < 0f) 1 else -1)
                    }
                    finishHorizontalGesture()
                    performClick()
                    return true
                }

                MotionEvent.ACTION_CANCEL -> {
                    finishHorizontalGesture()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun isInsideSchedule(x: Float, y: Float): Boolean {
        val region = scheduleRegion ?: return false
        if (region.parent == null || region.width <= 0 || region.height <= 0) return false

        scheduleRect.set(0, 0, region.width, region.height)
        offsetDescendantRectToMyCoords(region, scheduleRect)
        return scheduleRect.contains(x.toInt(), y.toInt())
    }

    private fun finishHorizontalGesture() {
        horizontalGestureCaptured = false
        startedInsideSchedule = false
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
