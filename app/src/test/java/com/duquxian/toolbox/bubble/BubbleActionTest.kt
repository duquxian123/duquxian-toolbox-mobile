package com.duquxian.toolbox.bubble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.duquxian.toolbox.data.ToolboxSettings

class BubbleActionTest {
    @Test
    fun savedActionsRoundTrip() {
        BubbleAction.entries.forEach { action ->
            assertEquals(action, BubbleAction.fromStorage(action.storageValue))
        }
    }

    @Test
    fun missingOrUnknownActionFallsBackToBack() {
        assertEquals(BubbleAction.BACK, BubbleAction.fromStorage(null))
        assertEquals(BubbleAction.BACK, BubbleAction.fromStorage("unsupported"))
    }

    @Test
    fun actionPickerOrderIsTheConfirmedOrder() {
        assertEquals(
            listOf(
                BubbleAction.NONE,
                BubbleAction.BACK,
                BubbleAction.HOME,
                BubbleAction.LOCK_SCREEN,
                BubbleAction.RECENTS,
                BubbleAction.NOTIFICATIONS,
                BubbleAction.QUICK_SETTINGS,
                BubbleAction.OPEN_APP,
                BubbleAction.VOLUME_UP,
                BubbleAction.VOLUME_DOWN,
                BubbleAction.FLASHLIGHT,
                BubbleAction.SCREENSHOT,
            ),
            BubbleAction.selectableActions,
        )
    }

    @Test
    fun gestureDefaultsMatchTheConfirmedMapping() {
        val defaults = defaultGestureBindings()

        assertEquals(BubbleAction.BACK, defaults.getValue(BubbleGesture.SINGLE).action)
        assertFalse(defaults.getValue(BubbleGesture.SINGLE).vibrationEnabled)
        assertEquals(BubbleAction.HOME, defaults.getValue(BubbleGesture.UP).action)
        assertEquals(BubbleAction.LOCK_SCREEN, defaults.getValue(BubbleGesture.DOWN).action)
        assertEquals(BubbleAction.RECENTS, defaults.getValue(BubbleGesture.LEFT).action)
        assertEquals(BubbleAction.RECENTS, defaults.getValue(BubbleGesture.RIGHT).action)
        assertTrue(defaults.filterKeys { it != BubbleGesture.SINGLE }.values.all { it.vibrationEnabled })
    }

    @Test
    fun dragVibrationIsEnabledByDefault() {
        assertTrue(ToolboxSettings().dragVibrationEnabled)
    }
}

