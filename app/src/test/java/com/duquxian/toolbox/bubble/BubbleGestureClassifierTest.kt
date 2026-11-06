package com.duquxian.toolbox.bubble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleGestureClassifierTest {
    private fun classifier() = BubbleGestureClassifier(
        touchSlop = 8f,
        swipeThreshold = 24f,
    )

    @Test
    fun shortPressBecomesTapImmediatelyOnRelease() {
        val classifier = classifier()
        classifier.reset()
        classifier.onMove(deltaX = 2f, deltaY = 3f)

        assertEquals(
            BubbleGestureClassifier.Result.TAP,
            classifier.onUp(deltaX = 2f, deltaY = 3f),
        )
    }

    @Test
    fun upwardSwipeDisablesLongPress() {
        val classifier = classifier()
        classifier.reset()

        assertTrue(classifier.onMove(deltaX = 2f, deltaY = -30f))
        assertEquals(
            BubbleGestureClassifier.Result.IGNORE,
            classifier.onLongPress(),
        )
        assertEquals(
            BubbleGestureClassifier.Result.SWIPE_UP,
            classifier.onUp(deltaX = 2f, deltaY = -30f),
        )
    }

    @Test
    fun downwardSwipeIsRecognized() {
        val classifier = classifier()
        classifier.reset()
        classifier.onMove(deltaX = -1f, deltaY = 28f)

        assertEquals(
            BubbleGestureClassifier.Result.SWIPE_DOWN,
            classifier.onUp(deltaX = -1f, deltaY = 28f),
        )
    }

    @Test
    fun leftSwipeIsRecognized() {
        val classifier = classifier()
        classifier.reset()
        classifier.onMove(deltaX = -34f, deltaY = 3f)

        assertEquals(
            BubbleGestureClassifier.Result.SWIPE_LEFT,
            classifier.onUp(deltaX = -34f, deltaY = 3f),
        )
    }

    @Test
    fun rightSwipeIsRecognized() {
        val classifier = classifier()
        classifier.reset()
        classifier.onMove(deltaX = 34f, deltaY = -3f)

        assertEquals(
            BubbleGestureClassifier.Result.SWIPE_RIGHT,
            classifier.onUp(deltaX = 34f, deltaY = -3f),
        )
    }

    @Test
    fun longPressWithoutMovementEntersDragMode() {
        val classifier = classifier()
        classifier.reset()

        assertEquals(
            BubbleGestureClassifier.Result.DRAG_START,
            classifier.onLongPress(),
        )
        assertTrue(classifier.isDragging)
        assertEquals(
            BubbleGestureClassifier.Result.DRAG_END,
            classifier.onUp(deltaX = 60f, deltaY = 90f),
        )
    }

    @Test
    fun swipeCannotTurnIntoDragAfterLongPressTimer() {
        val classifier = classifier()
        classifier.reset()
        classifier.onMove(deltaX = 0f, deltaY = -40f)

        classifier.onLongPress()

        assertFalse(classifier.isDragging)
    }

    @Test
    fun ambiguousDiagonalMovementOutsideTapToleranceIsIgnored() {
        val classifier = classifier()
        classifier.reset()
        classifier.onMove(deltaX = 30f, deltaY = 28f)

        assertEquals(
            BubbleGestureClassifier.Result.IGNORE,
            classifier.onUp(deltaX = 30f, deltaY = 28f),
        )
    }

    @Test
    fun finalUpwardMovementOverridesEarlierDownwardMovement() {
        val classifier = classifier()
        classifier.reset()

        assertTrue(classifier.onMove(deltaX = 0f, deltaY = 32f))
        assertFalse(classifier.onMove(deltaX = 0f, deltaY = 8f))

        assertEquals(
            BubbleGestureClassifier.Result.SWIPE_UP,
            classifier.onUp(deltaX = 0f, deltaY = -32f),
        )
    }

    @Test
    fun finalHorizontalMovementOverridesEarlierOppositeMovement() {
        val classifier = classifier()
        classifier.reset()
        classifier.onMove(deltaX = -32f, deltaY = 2f)

        assertEquals(
            BubbleGestureClassifier.Result.SWIPE_RIGHT,
            classifier.onUp(deltaX = 32f, deltaY = 2f),
        )
    }

    @Test
    fun returningNearStartAfterSwipeDoesNotBecomeTap() {
        val classifier = classifier()
        classifier.reset()
        classifier.onMove(deltaX = 0f, deltaY = 30f)

        assertEquals(
            BubbleGestureClassifier.Result.IGNORE,
            classifier.onUp(deltaX = 0f, deltaY = 2f),
        )
    }
}

