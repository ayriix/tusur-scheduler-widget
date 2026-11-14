package ru.tusur.schedulewidget

import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ScrollView
import kotlin.math.abs
import kotlin.math.max

/**
 * Vertical ScrollView that recognises horizontal day swipes only when the gesture
 * starts inside the schedule block. Keeping gesture recognition on the parent
 * prevents lesson-card children and the ScrollView from competing for the same touch.
 */
class ScheduleScrollView(context: Context) : ScrollView(context) {

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val density = resources.displayMetrics.density
    private val targetRect = Rect()

    private var swipeTarget: View? = null
    private var onDaySwipe: ((Int) -> Unit)? = null

    private var downX = 0f
    private var downY = 0f
    private var startedInsideSchedule = false
    private var horizontalGesture = false
    private var animationInProgress = false

    init {
        isFillViewport = false
        clipToPadding = false
    }

    fun setSwipeTarget(target: View, callback: (Int) -> Unit) {
        swipeTarget = target
        onDaySwipe = callback
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                startedInsideSchedule = isInsideSwipeTarget(event.x, event.y)
                horizontalGesture = false
                animationInProgress = false
            }

            MotionEvent.ACTION_MOVE -> {
                if (startedInsideSchedule && !animationInProgress) {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    // A clear horizontal direction is required; vertical scrolling remains native.
                    if (abs(dx) > touchSlop && abs(dx) > abs(dy) * 1.15f) {
                        horizontalGesture = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                }
            }
        }
        return if (horizontalGesture) true else super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!horizontalGesture) return super.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                updateDragVisual(event.x - downX)
                return true
            }

            MotionEvent.ACTION_UP -> {
                val dx = event.x - downX
                val target = swipeTarget
                val threshold = max(48f * density, width * 0.14f)
                if (target != null && abs(dx) >= threshold) {
                    finishSwipe(if (dx < 0f) 1 else -1, dx)
                } else {
                    animateBack()
                }
                finishTouchSequence()
                performClick()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                animateBack()
                finishTouchSequence()
                return true
            }
        }
        return true
    }

    private fun isInsideSwipeTarget(x: Float, y: Float): Boolean {
        val target = swipeTarget ?: return false
        if (target.width <= 0 || target.height <= 0) return false

        target.getDrawingRect(targetRect)
        offsetDescendantRectToMyCoords(target, targetRect)
        return targetRect.contains(x.toInt(), y.toInt())
    }

    private fun updateDragVisual(dx: Float) {
        val target = swipeTarget ?: return
        val targetWidth = target.width.coerceAtLeast(1).toFloat()
        val progress = (abs(dx) / (targetWidth * 0.38f)).coerceIn(0f, 1f)

        target.animate().cancel()
        target.pivotX = targetWidth / 2f
        target.pivotY = target.height / 2f
        target.cameraDistance = 9000f * density
        target.translationX = dx * 0.20f
        target.scaleX = 1f - 0.055f * progress
        target.scaleY = 1f - 0.055f * progress
        target.alpha = 1f - 0.30f * progress
        target.rotationY = if (dx < 0f) 4f * progress else -4f * progress
    }

    private fun finishSwipe(dayDelta: Int, dx: Float) {
        if (animationInProgress) return
        val target = swipeTarget ?: return
        animationInProgress = true
        val direction = if (dx < 0f) -1f else 1f

        target.animate().cancel()
        target.animate()
            .translationX(direction * target.width.coerceAtLeast(1) * 0.22f)
            .scaleX(0.93f)
            .scaleY(0.93f)
            .alpha(0f)
            .rotationY(-direction * 6f)
            .setDuration(145L)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction { onDaySwipe?.invoke(dayDelta) }
            .start()
    }

    private fun animateBack() {
        val target = swipeTarget ?: return
        if (animationInProgress) return
        target.animate().cancel()
        target.animate()
            .translationX(0f)
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .rotationY(0f)
            .setDuration(180L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun finishTouchSequence() {
        horizontalGesture = false
        startedInsideSchedule = false
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
