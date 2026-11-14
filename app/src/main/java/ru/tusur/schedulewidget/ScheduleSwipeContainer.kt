package ru.tusur.schedulewidget

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * A touch container for the schedule list. It intercepts horizontal gestures only,
 * leaving vertical gestures to the surrounding ScrollView.
 *
 * onSwipe receives +1 for a left swipe (next day) and -1 for a right swipe (previous day).
 */
class ScheduleSwipeContainer(
    context: Context,
    private val onSwipe: (Int) -> Unit
) : FrameLayout(context) {

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minimumSwipeDistance = 32f * resources.displayMetrics.density
    // Separate from the swipe threshold: the visual depth builds up more gradually.
    private val visualEffectDistance = 72f * resources.displayMetrics.density

    private var downX = 0f
    private var downY = 0f

    init {
        isClickable = true
        isFocusable = true
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.x - downX
                val deltaY = event.y - downY
                if (abs(deltaX) > touchSlop && abs(deltaX) > abs(deltaY) * 1.2f) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                resetSwipeVisual(animated = false)
                downX = event.x
                downY = event.y
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                // Visual-only feedback: do not alter touch coordinates, interception,
                // swipe threshold, or the callback used to change the selected day.
                updateSwipeVisual(event.x - downX)
                return true
            }

            MotionEvent.ACTION_UP -> {
                val deltaX = event.x - downX
                if (abs(deltaX) >= minimumSwipeDistance) {
                    updateSwipeVisual(deltaX)
                    onSwipe(if (deltaX < 0f) 1 else -1)
                } else {
                    resetSwipeVisual(animated = true)
                }
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                resetSwipeVisual(animated = true)
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return true
    }

    /**
     * Shrink and fade only the displayed schedule content. The touch container itself
     * is never transformed, so MotionEvent coordinates and the working swipe logic
     * remain unchanged.
     */
    private fun updateSwipeVisual(deltaX: Float) {
        val content = getChildAt(0) ?: return
        val progress = (abs(deltaX) / visualEffectDistance).coerceIn(0f, 1f)
        val scale = 1f - 0.07f * progress

        content.pivotX = content.width / 2f
        content.pivotY = content.height / 2f
        content.scaleX = scale
        content.scaleY = scale
        content.alpha = 1f - 0.32f * progress
        // Keep the transformed content inside the FrameLayout bounds, including the
        // short empty-day card; the previous downward shift pushed its bottom edge
        // outside the container and made it appear clipped by the next block.
        content.translationY = -2f * resources.displayMetrics.density * progress
    }

    private fun resetSwipeVisual(animated: Boolean) {
        val content = getChildAt(0) ?: return
        content.animate().cancel()
        if (animated) {
            content.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .translationY(0f)
                .setDuration(280L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } else {
            content.scaleX = 1f
            content.scaleY = 1f
            content.alpha = 1f
            content.translationY = 0f
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
