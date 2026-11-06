package com.duquxian.toolbox.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.duquxian.toolbox.R
import com.duquxian.toolbox.bubble.BubbleAction
import com.duquxian.toolbox.bubble.BubbleGesture
import com.duquxian.toolbox.bubble.BubbleOverlayService
import com.duquxian.toolbox.bubble.BubbleRuntimeState
import com.duquxian.toolbox.bubble.GestureBinding
import com.duquxian.toolbox.data.BubblePreferences
import com.duquxian.toolbox.data.ToolboxSettings
import com.duquxian.toolbox.databinding.ActivitySettingsBinding
import com.duquxian.toolbox.databinding.DialogGestureConfigBinding
import com.duquxian.toolbox.debug.DebugLogger
import com.duquxian.toolbox.permission.PermissionSnapshot
import com.duquxian.toolbox.permission.PermissionUtils
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.radiobutton.MaterialRadioButton
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var preferences: BubblePreferences
    private var currentSettings = ToolboxSettings()
    private var waitingToEnable = false
    private var refreshJob: Job? = null
    private var pendingFlashlightGesture: BubbleGesture? = null
    private var pendingFlashlightDialog: BottomSheetDialog? = null
    private var pendingAppGesture: BubbleGesture? = null

    private val flashlightPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val gesture = pendingFlashlightGesture
        val dialog = pendingFlashlightDialog
        pendingFlashlightGesture = null
        pendingFlashlightDialog = null
        if (granted && gesture != null) {
            saveGestureAction(gesture, BubbleAction.FLASHLIGHT, dialog = dialog)
        } else {
            Toast.makeText(
                this,
                R.string.camera_permission_denied,
                Toast.LENGTH_SHORT,
            ).show()
            dialog?.dismiss()
        }
    }

    private val appPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val gesture = pendingAppGesture
        pendingAppGesture = null
        if (result.resultCode != Activity.RESULT_OK || gesture == null) return@registerForActivityResult

        val data = result.data ?: return@registerForActivityResult
        val packageName = data.getStringExtra(AppPickerActivity.EXTRA_PACKAGE_NAME)
            ?: return@registerForActivityResult
        val label = data.getStringExtra(AppPickerActivity.EXTRA_APP_LABEL)
            ?: return@registerForActivityResult
        saveGestureAction(
            gesture = gesture,
            action = BubbleAction.OPEN_APP,
            targetPackage = packageName,
            targetLabel = label,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemUi.configure(window, binding.root)
        DebugLogger.event("SettingsActivity", "Bubble settings opened")

        preferences = BubblePreferences(this)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.buttonToggleBubble.setOnClickListener {
            DebugLogger.event(
                "SettingsActivity",
                "Toggle button clicked enabled=${currentSettings.bubbleEnabled}",
            )
            if (currentSettings.bubbleEnabled) {
                disableBubble()
            } else {
                requestEnable()
            }
        }

        binding.sliderBubbleSize.addOnChangeListener { _, value, fromUser ->
            binding.textBubbleSizeValue.text = getString(
                R.string.percentage_format,
                value.roundToInt(),
            )
            if (fromUser) {
                currentSettings = currentSettings.copy(bubbleSizeScale = value / 100f)
                saveBubbleAppearance()
            }
        }
        binding.sliderBubbleOpacity.addOnChangeListener { _, value, fromUser ->
            binding.textBubbleOpacityValue.text = getString(
                R.string.percentage_format,
                value.roundToInt(),
            )
            if (fromUser) {
                currentSettings = currentSettings.copy(bubbleOpacity = value / 100f)
                saveBubbleAppearance()
            }
        }
        binding.rowAutoHideGames.setOnClickListener { toggleAutoHideGames() }
        binding.buttonAutoHideGames.setOnClickListener { toggleAutoHideGames() }
        binding.rowAutoHideFullscreen.setOnClickListener { toggleAutoHideFullscreen() }
        binding.buttonAutoHideFullscreen.setOnClickListener { toggleAutoHideFullscreen() }

        binding.cardPermissions.setOnClickListener {
            startActivity(Intent(this, PermissionsActivity::class.java))
        }
        binding.rowSingleGesture.setOnClickListener {
            showGestureConfig(BubbleGesture.SINGLE)
        }
        binding.rowSwipeUp.setOnClickListener {
            showGestureConfig(BubbleGesture.UP)
        }
        binding.rowSwipeDown.setOnClickListener {
            showGestureConfig(BubbleGesture.DOWN)
        }
        binding.rowSwipeLeft.setOnClickListener {
            showGestureConfig(BubbleGesture.LEFT)
        }
        binding.rowSwipeRight.setOnClickListener {
            showGestureConfig(BubbleGesture.RIGHT)
        }
        binding.rowDragGesture.setOnClickListener {
            showDragConfig()
        }
        binding.buttonHelp.setOnClickListener {
            showHelpDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    private fun refreshState() {
        refreshJob?.cancel()
        refreshJob = lifecycleScope.launch {
            var settings = preferences.settings.first()
            val permissions = PermissionUtils.snapshot(this@SettingsActivity)

            if (waitingToEnable && permissions.allGranted) {
                waitingToEnable = false
                settings = enableAndStart(settings)
            } else if (settings.bubbleEnabled &&
                permissions.allGranted &&
                !BubbleRuntimeState.isRunning
            ) {
                BubbleOverlayService.start(this@SettingsActivity)
            }

            renderUi(settings, permissions, BubbleRuntimeState.isRunning)
        }
    }

    private fun requestEnable() {
        lifecycleScope.launch {
            val permissions = PermissionUtils.snapshot(this@SettingsActivity)
            DebugLogger.event(
                "SettingsActivity",
                "Enable requested; allPermissions=${permissions.allGranted}",
            )
            if (permissions.allGranted) {
                val settings = enableAndStart(currentSettings)
                renderUi(settings, permissions, running = true)
            } else {
                waitingToEnable = true
                startActivity(Intent(this@SettingsActivity, PermissionsActivity::class.java))
            }
        }
    }

    private suspend fun enableAndStart(settings: ToolboxSettings): ToolboxSettings {
        DebugLogger.event("SettingsActivity", "Starting bubble service")
        preferences.setBubbleEnabled(true)
        BubbleOverlayService.start(this)
        return settings.copy(bubbleEnabled = true)
    }

    private fun disableBubble() {
        lifecycleScope.launch {
            DebugLogger.event("SettingsActivity", "Stopping bubble service")
            waitingToEnable = false
            preferences.setBubbleEnabled(false)
            BubbleOverlayService.stop(this@SettingsActivity)
            currentSettings = currentSettings.copy(bubbleEnabled = false)
            renderUi(
                currentSettings,
                PermissionUtils.snapshot(this@SettingsActivity),
                running = false,
            )
        }
    }

    private fun renderUi(
        settings: ToolboxSettings,
        permissions: PermissionSnapshot,
        running: Boolean,
    ) {
        currentSettings = settings
        val canRun = settings.bubbleEnabled && permissions.allGranted

        val statusText: Int
        val descriptionText: Int
        val statusColor: Int
        when {
            settings.bubbleEnabled && !permissions.allGranted -> {
                statusText = R.string.status_missing_permissions
                descriptionText = R.string.bubble_missing_description
                statusColor = R.color.warning
            }
            running && canRun -> {
                statusText = R.string.status_running
                descriptionText = R.string.bubble_running_description
                statusColor = R.color.success
            }
            settings.bubbleEnabled -> {
                statusText = R.string.status_starting
                descriptionText = R.string.bubble_running_description
                statusColor = R.color.warning
            }
            else -> {
                statusText = R.string.status_not_enabled
                descriptionText = R.string.bubble_stopped_description
                statusColor = R.color.text_secondary
            }
        }

        binding.textStatus.setText(statusText)
        binding.textStatus.setTextColor(ContextCompat.getColor(this, statusColor))
        binding.textStatusDescription.setText(descriptionText)
        renderToggleButton(settings.bubbleEnabled)
        renderPermissionState(permissions)
        renderAppearance(settings)
        renderGestureRows(settings)
        DebugLogger.event(
            "SettingsActivity",
            "UI state enabled=${settings.bubbleEnabled} running=$running " +
                "overlay=${permissions.overlayGranted} " +
                "notification=${permissions.notificationsGranted} " +
                "accessibility=${permissions.accessibilityGranted}",
        )
    }

    private fun renderPermissionState(permissions: PermissionSnapshot) {
        binding.textPermissionStatus.setText(
            if (permissions.allGranted) {
                R.string.permission_status_ready
            } else {
                R.string.permission_status_action
            },
        )
        binding.textPermissionStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (permissions.allGranted) R.color.success else R.color.warning,
            ),
        )
        binding.cardPermissions.setCardBackgroundColor(
            ContextCompat.getColor(
                this,
                if (permissions.allGranted) R.color.surface else R.color.red_soft,
            ),
        )
        binding.cardPermissions.strokeColor = ContextCompat.getColor(
            this,
            if (permissions.allGranted) R.color.divider else R.color.red_soft_stroke,
        )
    }

    private fun renderAppearance(settings: ToolboxSettings) {
        binding.sliderBubbleSize.value = settings.bubbleSizeScale * 100f
        binding.sliderBubbleOpacity.value = settings.bubbleOpacity * 100f
        binding.textBubbleSizeValue.text = getString(
            R.string.percentage_format,
            (settings.bubbleSizeScale * 100f).roundToInt(),
        )
        binding.textBubbleOpacityValue.text = getString(
            R.string.percentage_format,
            (settings.bubbleOpacity * 100f).roundToInt(),
        )

        renderAutoHideButton(binding.buttonAutoHideGames, settings.autoHideGames)
        renderAutoHideButton(binding.buttonAutoHideFullscreen, settings.autoHideFullscreen)
    }

    private fun saveBubbleAppearance() {
        lifecycleScope.launch {
            preferences.setBubbleAppearance(
                sizeScale = currentSettings.bubbleSizeScale,
                opacity = currentSettings.bubbleOpacity,
            )
            DebugLogger.event(
                "SettingsActivity",
                "Appearance size=${currentSettings.bubbleSizeScale} " +
                    "opacity=${currentSettings.bubbleOpacity}",
            )
        }
    }

    private fun toggleAutoHideGames() {
        val enabled = !currentSettings.autoHideGames
        currentSettings = currentSettings.copy(autoHideGames = enabled)
        renderAutoHideButton(binding.buttonAutoHideGames, enabled)
        DebugLogger.event("SettingsActivity", "Auto hide games=$enabled")
        lifecycleScope.launch { preferences.setAutoHideGames(enabled) }
    }

    private fun toggleAutoHideFullscreen() {
        val enabled = !currentSettings.autoHideFullscreen
        currentSettings = currentSettings.copy(autoHideFullscreen = enabled)
        renderAutoHideButton(binding.buttonAutoHideFullscreen, enabled)
        DebugLogger.event("SettingsActivity", "Auto hide fullscreen=$enabled")
        lifecycleScope.launch { preferences.setAutoHideFullscreen(enabled) }
    }

    private fun renderAutoHideButton(
        button: com.google.android.material.button.MaterialButton,
        enabled: Boolean,
    ) {
        renderEnabledButton(button, enabled)
    }

    private fun renderGestureRows(settings: ToolboxSettings) {
        binding.textSingleAction.text = actionValue(settings.binding(BubbleGesture.SINGLE))
        binding.textSwipeUpAction.text = actionValue(settings.binding(BubbleGesture.UP))
        binding.textSwipeDownAction.text = actionValue(settings.binding(BubbleGesture.DOWN))
        binding.textSwipeLeftAction.text = actionValue(settings.binding(BubbleGesture.LEFT))
        binding.textSwipeRightAction.text = actionValue(settings.binding(BubbleGesture.RIGHT))
        binding.textDragVibration.setText(
            if (settings.dragVibrationEnabled) {
                R.string.drag_vibration_enabled
            } else {
                R.string.drag_vibration_disabled
            },
        )
    }

    private fun actionValue(binding: GestureBinding): String {
        if (binding.action == BubbleAction.OPEN_APP) {
            return binding.targetLabel?.let { getString(R.string.open_app_value, it) }
                ?: getString(R.string.choose_app_value)
        }
        return getString(binding.action.labelRes)
    }

    private fun renderToggleButton(bubbleEnabled: Boolean) {
        binding.buttonToggleBubble.setText(
            if (bubbleEnabled) R.string.stop_short else R.string.start_short,
        )
        binding.buttonToggleBubble.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(
                this,
                if (bubbleEnabled) R.color.surface_alt else R.color.red_primary,
            ),
        )
        binding.buttonToggleBubble.setTextColor(
            ContextCompat.getColor(
                this,
                if (bubbleEnabled) R.color.red_primary else R.color.on_primary,
            ),
        )
    }

    private fun showGestureConfig(gesture: BubbleGesture) {
        val gestureBinding = currentSettings.binding(gesture)
        val dialogBinding = DialogGestureConfigBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(dialogBinding.root)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED

        dialogBinding.textGestureTitle.text = getString(
            R.string.gesture_config_title,
            getString(gesture.labelRes),
        )
        var vibrationEnabled = gestureBinding.vibrationEnabled
        fun updateVibrationButton() {
            renderEnabledButton(dialogBinding.buttonGestureVibration, vibrationEnabled)
        }
        fun toggleVibration() {
            vibrationEnabled = !vibrationEnabled
            updateVibrationButton()
            saveGestureVibration(gesture, vibrationEnabled)
        }
        updateVibrationButton()
        dialogBinding.rowGestureVibration.setOnClickListener { toggleVibration() }
        dialogBinding.buttonGestureVibration.setOnClickListener { toggleVibration() }

        BubbleAction.selectableActions.forEach { action ->
            val radioButton = MaterialRadioButton(this).apply {
                id = View.generateViewId()
                tag = action
                text = actionPickerLabel(action, gestureBinding)
                isChecked = action == gestureBinding.action
                isEnabled = action != BubbleAction.SCREENSHOT ||
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.text_primary))
                setPadding(dp(8), 0, dp(8), 0)
            }
            radioButton.setOnClickListener {
                when (action) {
                    BubbleAction.OPEN_APP -> openAppPicker(gesture, dialog)
                    BubbleAction.FLASHLIGHT -> requestFlashlightPermission(gesture, dialog)
                    else -> saveGestureAction(gesture, action, dialog = dialog)
                }
            }
            dialogBinding.groupGestureActions.addView(
                radioButton,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(48),
                ),
            )
        }
        dialog.show()
    }

    private fun showDragConfig() {
        val dialogBinding = DialogGestureConfigBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(dialogBinding.root)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED

        dialogBinding.textGestureTitle.setText(R.string.drag_settings_title)
        dialogBinding.groupGestureActions.visibility = View.GONE

        var vibrationEnabled = currentSettings.dragVibrationEnabled
        fun updateVibrationButton() {
            renderEnabledButton(dialogBinding.buttonGestureVibration, vibrationEnabled)
        }
        fun toggleVibration() {
            vibrationEnabled = !vibrationEnabled
            updateVibrationButton()
            currentSettings = currentSettings.copy(dragVibrationEnabled = vibrationEnabled)
            binding.textDragVibration.setText(
                if (vibrationEnabled) {
                    R.string.drag_vibration_enabled
                } else {
                    R.string.drag_vibration_disabled
                },
            )
            lifecycleScope.launch {
                preferences.setDragVibration(vibrationEnabled)
            }
            DebugLogger.event("SettingsActivity", "Drag vibration=$vibrationEnabled")
        }
        updateVibrationButton()
        dialogBinding.rowGestureVibration.setOnClickListener { toggleVibration() }
        dialogBinding.buttonGestureVibration.setOnClickListener { toggleVibration() }
        dialog.show()
    }

    private fun renderEnabledButton(
        button: com.google.android.material.button.MaterialButton,
        enabled: Boolean,
    ) {
        button.setText(if (enabled) R.string.switch_enabled else R.string.switch_disabled)
        button.setTextColor(
            ContextCompat.getColor(
                this,
                if (enabled) R.color.success else R.color.text_secondary,
            ),
        )
        button.strokeColor = ColorStateList.valueOf(
            ContextCompat.getColor(
                this,
                if (enabled) R.color.success else R.color.divider,
            ),
        )
    }

    private fun actionPickerLabel(
        action: BubbleAction,
        gestureBinding: GestureBinding,
    ): String {
        return when {
            action == BubbleAction.SCREENSHOT &&
                Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                getString(R.string.gesture_unavailable_screenshot)
            action == BubbleAction.OPEN_APP && gestureBinding.targetLabel != null ->
                "${getString(action.labelRes)}（${gestureBinding.targetLabel}）"
            else -> getString(action.labelRes)
        }
    }

    private fun saveGestureVibration(gesture: BubbleGesture, enabled: Boolean) {
        lifecycleScope.launch {
            preferences.setGestureVibration(gesture, enabled)
            val updated = currentSettings.binding(gesture).copy(vibrationEnabled = enabled)
            currentSettings = currentSettings.copy(
                gestures = currentSettings.gestures + (gesture to updated),
            )
            DebugLogger.event(
                "SettingsActivity",
                "Gesture ${gesture.storageValue} vibration=$enabled",
            )
        }
    }

    private fun saveGestureAction(
        gesture: BubbleGesture,
        action: BubbleAction,
        targetPackage: String? = null,
        targetLabel: String? = null,
        dialog: BottomSheetDialog? = null,
    ) {
        lifecycleScope.launch {
            preferences.setGestureAction(
                gesture = gesture,
                action = action,
                targetPackage = targetPackage,
                targetLabel = targetLabel,
            )
            val updated = currentSettings.binding(gesture).copy(
                action = action,
                targetPackage = targetPackage,
                targetLabel = targetLabel,
            )
            currentSettings = currentSettings.copy(
                gestures = currentSettings.gestures + (gesture to updated),
            )
            renderGestureRows(currentSettings)
            DebugLogger.event(
                "SettingsActivity",
                "Gesture ${gesture.storageValue} action=${action.storageValue}",
            )
            dialog?.dismiss()
        }
    }

    private fun requestFlashlightPermission(
        gesture: BubbleGesture,
        dialog: BottomSheetDialog,
    ) {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            saveGestureAction(gesture, BubbleAction.FLASHLIGHT, dialog = dialog)
            return
        }

        pendingFlashlightGesture = gesture
        pendingFlashlightDialog = dialog
        flashlightPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun openAppPicker(
        gesture: BubbleGesture,
        dialog: BottomSheetDialog,
    ) {
        dialog.dismiss()
        pendingAppGesture = gesture
        appPickerLauncher.launch(Intent(this, AppPickerActivity::class.java))
    }

    private fun showHelpDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.help_dialog_title)
            .setMessage(R.string.help_dialog_message)
            .setNegativeButton(R.string.check_permissions) { _, _ ->
                startActivity(Intent(this, PermissionsActivity::class.java))
            }
            .setPositiveButton(R.string.got_it, null)
            .show()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
