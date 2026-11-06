package com.duquxian.toolbox.bubble

import androidx.annotation.StringRes
import com.duquxian.toolbox.R

enum class BubbleAction(
    val storageValue: String,
    @StringRes val labelRes: Int,
) {
    NONE("none", R.string.action_none),
    BACK("back", R.string.action_back),
    HOME("home", R.string.action_home),
    LOCK_SCREEN("lock_screen", R.string.action_lock_screen),
    RECENTS("recents", R.string.action_recents),
    NOTIFICATIONS("notifications", R.string.action_notifications),
    QUICK_SETTINGS("quick_settings", R.string.action_quick_settings),
    OPEN_APP("open_app", R.string.action_open_app),
    VOLUME_UP("volume_up", R.string.action_volume_up),
    VOLUME_DOWN("volume_down", R.string.action_volume_down),
    FLASHLIGHT("flashlight", R.string.action_flashlight),
    SCREENSHOT("screenshot", R.string.action_screenshot),
    OPEN_TOOLBOX("toolbox", R.string.action_toolbox),
    ;

    companion object {
        val selectableActions = listOf(
            NONE,
            BACK,
            HOME,
            LOCK_SCREEN,
            RECENTS,
            NOTIFICATIONS,
            QUICK_SETTINGS,
            OPEN_APP,
            VOLUME_UP,
            VOLUME_DOWN,
            FLASHLIGHT,
            SCREENSHOT,
        )

        fun fromStorage(value: String?, fallback: BubbleAction = BACK): BubbleAction {
            return entries.firstOrNull { it.storageValue == value } ?: fallback
        }
    }
}

enum class BubbleGesture(
    val storageValue: String,
    @StringRes val labelRes: Int,
    val defaultAction: BubbleAction,
    val defaultVibration: Boolean,
) {
    SINGLE("single", R.string.gesture_title_single, BubbleAction.BACK, false),
    UP("up", R.string.gesture_title_up, BubbleAction.HOME, true),
    DOWN("down", R.string.gesture_title_down, BubbleAction.LOCK_SCREEN, true),
    LEFT("left", R.string.gesture_title_left, BubbleAction.RECENTS, true),
    RIGHT("right", R.string.gesture_title_right, BubbleAction.RECENTS, true),
}

data class GestureBinding(
    val action: BubbleAction,
    val vibrationEnabled: Boolean,
    val targetPackage: String? = null,
    val targetLabel: String? = null,
)

fun defaultGestureBinding(gesture: BubbleGesture): GestureBinding {
    return GestureBinding(
        action = gesture.defaultAction,
        vibrationEnabled = gesture.defaultVibration,
    )
}

fun defaultGestureBindings(): Map<BubbleGesture, GestureBinding> {
    return BubbleGesture.entries.associateWith(::defaultGestureBinding)
}

