package com.duquxian.toolbox.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.duquxian.toolbox.bubble.BubbleAction
import com.duquxian.toolbox.bubble.BubbleGesture
import com.duquxian.toolbox.bubble.GestureBinding
import com.duquxian.toolbox.bubble.defaultGestureBinding
import com.duquxian.toolbox.bubble.defaultGestureBindings
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.toolboxDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "toolbox_settings",
)

data class ToolboxSettings(
    val gestures: Map<BubbleGesture, GestureBinding> = defaultGestureBindings(),
    val bubbleEnabled: Boolean = false,
    val bubbleX: Float = 1f,
    val bubbleY: Float = 0.55f,
    val bubbleSizeScale: Float = 1f,
    val bubbleOpacity: Float = 1f,
    val autoHideGames: Boolean = true,
    val autoHideFullscreen: Boolean = true,
    val dragVibrationEnabled: Boolean = true,
) {
    fun binding(gesture: BubbleGesture): GestureBinding {
        return gestures[gesture] ?: defaultGestureBinding(gesture)
    }
}

class BubblePreferences(context: Context) {
    private val dataStore = context.applicationContext.toolboxDataStore

    val settings: Flow<ToolboxSettings> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { preferences ->
            ToolboxSettings(
                gestures = BubbleGesture.entries.associateWith { gesture ->
                    preferences.readBinding(gesture)
                },
                bubbleEnabled = preferences[Keys.bubbleEnabled] ?: false,
                bubbleX = (preferences[Keys.bubbleX] ?: 1f).coerceIn(0f, 1f),
                bubbleY = (preferences[Keys.bubbleY] ?: 0.55f).coerceIn(0f, 1f),
                bubbleSizeScale = (preferences[Keys.bubbleSizeScale] ?: 1f)
                    .coerceIn(MIN_SIZE_SCALE, MAX_SIZE_SCALE),
                bubbleOpacity = (preferences[Keys.bubbleOpacity] ?: 1f)
                    .coerceIn(MIN_OPACITY, MAX_OPACITY),
                autoHideGames = preferences[Keys.autoHideGames] ?: true,
                autoHideFullscreen = preferences[Keys.autoHideFullscreen] ?: true,
                dragVibrationEnabled = preferences[Keys.dragVibrationEnabled] ?: true,
            )
        }

    suspend fun setGestureAction(
        gesture: BubbleGesture,
        action: BubbleAction,
        targetPackage: String? = null,
        targetLabel: String? = null,
    ) {
        dataStore.edit { preferences ->
            preferences[actionKey(gesture)] = action.storageValue
            if (targetPackage != null && targetLabel != null) {
                preferences[targetPackageKey(gesture)] = targetPackage
                preferences[targetLabelKey(gesture)] = targetLabel
            } else {
                preferences.remove(targetPackageKey(gesture))
                preferences.remove(targetLabelKey(gesture))
            }
            if (gesture == BubbleGesture.SINGLE) {
                preferences[Keys.legacySingleTapAction] = action.storageValue
            }
        }
    }

    suspend fun setGestureVibration(gesture: BubbleGesture, enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[vibrationKey(gesture)] = enabled
        }
    }

    suspend fun setBubbleEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.bubbleEnabled] = enabled
        }
    }

    suspend fun setBubblePosition(x: Float, y: Float) {
        dataStore.edit { preferences ->
            preferences[Keys.bubbleX] = x.coerceIn(0f, 1f)
            preferences[Keys.bubbleY] = y.coerceIn(0f, 1f)
        }
    }

    suspend fun setBubbleAppearance(sizeScale: Float, opacity: Float) {
        dataStore.edit { preferences ->
            preferences[Keys.bubbleSizeScale] =
                sizeScale.coerceIn(MIN_SIZE_SCALE, MAX_SIZE_SCALE)
            preferences[Keys.bubbleOpacity] =
                opacity.coerceIn(MIN_OPACITY, MAX_OPACITY)
        }
    }

    suspend fun setAutoHideGames(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.autoHideGames] = enabled
        }
    }

    suspend fun setAutoHideFullscreen(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.autoHideFullscreen] = enabled
        }
    }

    suspend fun setDragVibration(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.dragVibrationEnabled] = enabled
        }
    }

    private fun Preferences.readBinding(gesture: BubbleGesture): GestureBinding {
        val default = defaultGestureBinding(gesture)
        val storedAction = this[actionKey(gesture)]
        val legacyAction = if (gesture == BubbleGesture.SINGLE) {
            this[Keys.legacySingleTapAction]
        } else {
            null
        }
        return GestureBinding(
            action = BubbleAction.fromStorage(
                storedAction ?: legacyAction,
                fallback = default.action,
            ),
            vibrationEnabled = this[vibrationKey(gesture)] ?: default.vibrationEnabled,
            targetPackage = this[targetPackageKey(gesture)],
            targetLabel = this[targetLabelKey(gesture)],
        )
    }

    private fun actionKey(gesture: BubbleGesture) =
        stringPreferencesKey("gesture_${gesture.storageValue}_action")

    private fun vibrationKey(gesture: BubbleGesture) =
        booleanPreferencesKey("gesture_${gesture.storageValue}_vibration")

    private fun targetPackageKey(gesture: BubbleGesture) =
        stringPreferencesKey("gesture_${gesture.storageValue}_target_package")

    private fun targetLabelKey(gesture: BubbleGesture) =
        stringPreferencesKey("gesture_${gesture.storageValue}_target_label")

    private object Keys {
        val legacySingleTapAction = stringPreferencesKey("single_tap_action")
        val bubbleEnabled = booleanPreferencesKey("bubble_enabled")
        val bubbleX = floatPreferencesKey("bubble_x")
        val bubbleY = floatPreferencesKey("bubble_y")
        val bubbleSizeScale = floatPreferencesKey("bubble_size_scale")
        val bubbleOpacity = floatPreferencesKey("bubble_opacity")
        val autoHideGames = booleanPreferencesKey("auto_hide_games")
        val autoHideFullscreen = booleanPreferencesKey("auto_hide_fullscreen")
        val dragVibrationEnabled = booleanPreferencesKey("drag_vibration_enabled")
    }

    companion object {
        const val MIN_SIZE_SCALE = 0.7f
        const val MAX_SIZE_SCALE = 1.3f
        const val MIN_OPACITY = 0.4f
        const val MAX_OPACITY = 1f
    }
}

