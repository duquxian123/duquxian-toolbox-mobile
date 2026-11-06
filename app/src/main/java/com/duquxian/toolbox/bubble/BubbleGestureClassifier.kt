package com.duquxian.toolbox.bubble

import kotlin.math.abs

internal class BubbleGestureClassifier(
    private val touchSlop: Float,
    private val swipeThreshold: Float,
) {
    enum class Result {
        TAP,
        SWIPE_UP,
        SWIPE_DOWN,
        SWIPE_LEFT,
        SWIPE_RIGHT,
        DRAG_START,
        DRAG_END,
        IGNORE,
    }

    private enum class Mode {
        UNDECIDED,
        SWIPE_CANDIDATE,
        DRAGGING,
    }

    private var mode = Mode.UNDECIDED

    val isDragging: Boolean
        get() = mode == Mode.DRAGGING

    fun reset() {
        mode = Mode.UNDECIDED
    }

    fun onMove(deltaX: Float, deltaY: Float): Boolean {
        if (mode != Mode.UNDECIDED) {
            return false
        }

        if (isSwipe(deltaX, deltaY)) {
            mode = Mode.SWIPE_CANDIDATE
            return true
        }
        return false
    }

    fun onLongPress(): Result {
        if (mode != Mode.UNDECIDED) {
            return Result.IGNORE
        }
        mode = Mode.DRAGGING
        return Result.DRAG_START
    }

    fun onUp(deltaX: Float, deltaY: Float): Result {
        return when (mode) {
            Mode.DRAGGING -> Result.DRAG_END
            Mode.SWIPE_CANDIDATE -> finalSwipeResult(deltaX, deltaY)
            Mode.UNDECIDED -> {
                if (abs(deltaX) <= touchSlop && abs(deltaY) <= touchSlop) {
                    Result.TAP
                } else {
                    Result.IGNORE
                }
            }
        }
    }

    fun onCancel(): Result {
        return if (mode == Mode.DRAGGING) Result.DRAG_END else Result.IGNORE
    }

    private fun finalSwipeResult(deltaX: Float, deltaY: Float): Result {
        if (!isSwipe(deltaX, deltaY)) return Result.IGNORE
        return when {
            abs(deltaY) > abs(deltaX) -> {
                if (deltaY < 0f) Result.SWIPE_UP else Result.SWIPE_DOWN
            }
            else -> {
                if (deltaX < 0f) Result.SWIPE_LEFT else Result.SWIPE_RIGHT
            }
        }
    }

    private fun isSwipe(deltaX: Float, deltaY: Float): Boolean {
        return (abs(deltaY) > abs(deltaX) * AXIS_DOMINANCE &&
            abs(deltaY) >= swipeThreshold) ||
            (abs(deltaX) > abs(deltaY) * AXIS_DOMINANCE &&
                abs(deltaX) >= swipeThreshold)
    }

    companion object {
        private const val AXIS_DOMINANCE = 1.15f
    }
}

