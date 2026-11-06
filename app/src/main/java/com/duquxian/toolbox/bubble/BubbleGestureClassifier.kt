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
        SWIPE_UP,
        SWIPE_DOWN,
        SWIPE_LEFT,
        SWIPE_RIGHT,
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

        when {
            abs(deltaY) > abs(deltaX) * AXIS_DOMINANCE &&
                abs(deltaY) >= swipeThreshold -> {
                mode = if (deltaY < 0f) Mode.SWIPE_UP else Mode.SWIPE_DOWN
                return true
            }
            abs(deltaX) > abs(deltaY) * AXIS_DOMINANCE &&
                abs(deltaX) >= swipeThreshold -> {
                mode = if (deltaX < 0f) Mode.SWIPE_LEFT else Mode.SWIPE_RIGHT
                return true
            }
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
            Mode.SWIPE_UP -> Result.SWIPE_UP
            Mode.SWIPE_DOWN -> Result.SWIPE_DOWN
            Mode.SWIPE_LEFT -> Result.SWIPE_LEFT
            Mode.SWIPE_RIGHT -> Result.SWIPE_RIGHT
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

    companion object {
        private const val AXIS_DOMINANCE = 1.15f
    }
}

