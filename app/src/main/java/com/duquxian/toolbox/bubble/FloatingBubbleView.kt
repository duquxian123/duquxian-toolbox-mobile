package com.duquxian.toolbox.bubble

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import com.duquxian.toolbox.R
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

@SuppressLint("ViewConstructor")
class FloatingBubbleView(
    context: Context,
    private val gestureListener: GestureListener,
) : View(context) {

    interface GestureListener {
        fun onTap()
        fun onSwipeUp()
        fun onSwipeDown()
        fun onSwipeLeft()
        fun onSwipeRight()
        fun onDragStart()
        fun onDrag(rawX: Float, rawY: Float)
        fun onDragEnd()
    }

    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val swipeThreshold = max(24f * density, touchSlop * 2f)
    private val gestureClassifier = BubbleGestureClassifier(touchSlop, swipeThreshold)
    private val handler = Handler(Looper.getMainLooper())
    private val baseOuterRadius = 23f * density
    private val baseInnerRadius = 10f * density
    private val baseMaxInnerOffset = 6f * density

    private val longPressRunnable = Runnable {
        if (gestureClassifier.onLongPress() == BubbleGestureClassifier.Result.DRAG_START) {
            cancelInnerAnimation()
            innerOffsetX = 0f
            innerOffsetY = 0f
            innerScale = 0.9f
            invalidate()
            gestureListener.onDragStart()
        }
    }

    private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.bubble_outer)
        setShadowLayer(6f * density, 0f, 2.5f * density, 0x44000000)
    }
    private val outerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = 0x2E4A4E4A
    }
    private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.bubble_inner)
        setShadowLayer(3f * density, 0f, 1.5f * density, 0x3D000000)
    }

    private var downRawX = 0f
    private var downRawY = 0f
    private var innerOffsetX = 0f
    private var innerOffsetY = 0f
    private var innerScale = 1f
    private var innerAnimator: ValueAnimator? = null
    private var visualScale = 1f

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        isFocusable = true
        contentDescription = context.getString(R.string.bubble_name)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f

        val outerRadius = baseOuterRadius * visualScale
        canvas.drawCircle(centerX, centerY, outerRadius, outerPaint)
        canvas.drawCircle(centerX, centerY, outerRadius, outerBorderPaint)
        canvas.drawCircle(
            centerX + innerOffsetX,
            centerY + innerOffsetY,
            baseInnerRadius * visualScale * innerScale,
            innerPaint,
        )
    }

    fun setVisualScale(scale: Float) {
        val constrained = scale.coerceIn(0.7f, 1.3f)
        if (visualScale != constrained) {
            visualScale = constrained
            invalidate()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelInnerAnimation()
                downRawX = event.rawX
                downRawY = event.rawY
                gestureClassifier.reset()
                innerOffsetX = 0f
                innerOffsetY = 0f
                innerScale = 0.96f
                invalidate()
                handler.postDelayed(longPressRunnable, LONG_PRESS_MS)
            }

            MotionEvent.ACTION_MOVE -> {
                if (gestureClassifier.isDragging) {
                    gestureListener.onDrag(event.rawX, event.rawY)
                    return true
                }

                val deltaX = event.rawX - downRawX
                val deltaY = event.rawY - downRawY
                updateInnerOffset(deltaX, deltaY)
                if (gestureClassifier.onMove(deltaX, deltaY)) {
                    handler.removeCallbacks(longPressRunnable)
                }
            }

            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                val result = gestureClassifier.onUp(
                    event.rawX - downRawX,
                    event.rawY - downRawY,
                )
                resetTouchState()
                when (result) {
                    BubbleGestureClassifier.Result.DRAG_END -> {
                        gestureListener.onDragEnd()
                        returnInnerToCenter()
                    }
                    BubbleGestureClassifier.Result.SWIPE_UP -> {
                        gestureListener.onSwipeUp()
                        returnInnerToCenter()
                    }
                    BubbleGestureClassifier.Result.SWIPE_DOWN -> {
                        gestureListener.onSwipeDown()
                        returnInnerToCenter()
                    }
                    BubbleGestureClassifier.Result.SWIPE_LEFT -> {
                        gestureListener.onSwipeLeft()
                        returnInnerToCenter()
                    }
                    BubbleGestureClassifier.Result.SWIPE_RIGHT -> {
                        gestureListener.onSwipeRight()
                        returnInnerToCenter()
                    }
                    BubbleGestureClassifier.Result.TAP -> {
                        performClick()
                        gestureListener.onTap()
                        pulseInnerCircle()
                    }
                    else -> returnInnerToCenter()
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                if (gestureClassifier.onCancel() == BubbleGestureClassifier.Result.DRAG_END) {
                    gestureListener.onDragEnd()
                }
                resetTouchState()
                returnInnerToCenter()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun updateInnerOffset(deltaX: Float, deltaY: Float) {
        val distance = hypot(deltaX, deltaY)
        if (distance <= 0f) {
            innerOffsetX = 0f
            innerOffsetY = 0f
        } else {
            val limitedDistance = min(distance, baseMaxInnerOffset * visualScale)
            innerOffsetX = deltaX / distance * limitedDistance
            innerOffsetY = deltaY / distance * limitedDistance
        }
        innerScale = 1f
        invalidate()
    }

    private fun pulseInnerCircle() {
        cancelInnerAnimation()
        innerOffsetX = 0f
        innerOffsetY = 0f
        val animator = ValueAnimator.ofFloat(1f, 0.8f, 1f).apply {
            duration = TAP_ANIMATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                innerScale = it.animatedValue as Float
                invalidate()
            }
        }
        innerAnimator = animator
        animator.start()
    }

    private fun returnInnerToCenter() {
        cancelInnerAnimation()
        val startX = innerOffsetX
        val startY = innerOffsetY
        val startScale = innerScale
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = RETURN_ANIMATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val fraction = it.animatedValue as Float
                innerOffsetX = startX * (1f - fraction)
                innerOffsetY = startY * (1f - fraction)
                innerScale = startScale + (1f - startScale) * fraction
                invalidate()
            }
        }
        innerAnimator = animator
        animator.start()
    }

    private fun cancelInnerAnimation() {
        innerAnimator?.cancel()
        innerAnimator = null
    }

    private fun resetTouchState() {
        gestureClassifier.reset()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(longPressRunnable)
        cancelInnerAnimation()
        super.onDetachedFromWindow()
    }

    companion object {
        private const val LONG_PRESS_MS = 350L
        private const val TAP_ANIMATION_MS = 220L
        private const val RETURN_ANIMATION_MS = 180L
    }
}

