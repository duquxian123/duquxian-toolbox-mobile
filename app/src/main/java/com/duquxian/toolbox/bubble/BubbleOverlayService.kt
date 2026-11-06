package com.duquxian.toolbox.bubble

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.duquxian.toolbox.R
import com.duquxian.toolbox.accessibility.ForegroundAppTracker
import com.duquxian.toolbox.accessibility.QuickActionAccessibilityService
import com.duquxian.toolbox.data.BubblePreferences
import com.duquxian.toolbox.data.ToolboxSettings
import com.duquxian.toolbox.debug.DebugLogger
import com.duquxian.toolbox.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class BubbleOverlayService : Service(), FloatingBubbleView.GestureListener {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var preferences: BubblePreferences
    private lateinit var windowManager: WindowManager
    private var overlayView: FloatingBubbleView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var settings = ToolboxSettings()
    private var isDragging = false
    private var receiverRegistered = false
    private var torchEnabled = false
    private var screenSuspended = false
    private var settingsLoaded = false
    private var currentForegroundPackage: String? = null
    private var foregroundIsGame = false
    private var isFullscreen = false
    private var lastVisibilityDescription: String? = null
    private var overlayWindowShown: Boolean? = null
    private val unlockRefreshRunnable = Runnable {
        if (!screenSuspended && settingsLoaded && settings.bubbleEnabled) {
            overlayWindowShown = null
            reconcileBubbleVisibility()
            DebugLogger.event("BubbleService", "Unlock visibility refreshed")
        }
    }
    private val unlockHandler = Handler(Looper.getMainLooper())
    private var unlockCheckAttempts = 0
    private val unlockCheckRunnable = object : Runnable {
        override fun run() {
            val keyguardManager = getSystemService(KeyguardManager::class.java)
            if (keyguardManager?.isKeyguardLocked == false) {
                unlockCheckAttempts = 0
                restoreOverlayAfterUnlock()
            } else if (unlockCheckAttempts < MAX_UNLOCK_CHECK_ATTEMPTS) {
                unlockCheckAttempts++
                unlockHandler.postDelayed(this, UNLOCK_CHECK_INTERVAL_MS)
            }
        }
    }

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    DebugLogger.event("BubbleService", "Screen off; hiding bubble")
                    unlockHandler.removeCallbacks(unlockCheckRunnable)
                    unlockHandler.removeCallbacks(unlockRefreshRunnable)
                    unlockCheckAttempts = 0
                    screenSuspended = true
                    isDragging = false
                    setOverlayWindowVisible(false)
                }
                Intent.ACTION_SCREEN_ON -> {
                    DebugLogger.event("BubbleService", "Screen on; checking unlock state")
                    beginUnlockRecovery()
                }
                Intent.ACTION_USER_PRESENT -> {
                    DebugLogger.event("BubbleService", "User present; restoring bubble")
                    unlockHandler.removeCallbacks(unlockCheckRunnable)
                    unlockHandler.removeCallbacks(unlockRefreshRunnable)
                    unlockCheckAttempts = 0
                    restoreOverlayAfterUnlock()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        BubbleRuntimeState.isRunning = true
        DebugLogger.event("BubbleService", "Service created")
        preferences = BubblePreferences(this)
        windowManager = getSystemService(WindowManager::class.java)
        screenSuspended =
            getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        registerScreenStateReceiver()

        serviceScope.launch {
            preferences.settings.collect { updatedSettings ->
                if (!updatedSettings.bubbleEnabled) {
                    stopSelf()
                    return@collect
                }
                settings = updatedSettings
                settingsLoaded = true
                if (overlayView == null && !screenSuspended) {
                    showOverlay()
                }
                if (!isDragging) {
                    applySavedPosition(updatedSettings.bubbleX, updatedSettings.bubbleY)
                }
                overlayView?.setVisualScale(updatedSettings.bubbleSizeScale)
                overlayView?.alpha = updatedSettings.bubbleOpacity
                reconcileBubbleVisibility()
            }
        }

        serviceScope.launch {
            ForegroundAppTracker.packageName.collect { packageName ->
                currentForegroundPackage = packageName
                foregroundIsGame = isGamePackage(packageName)
                updateFullscreenFromWindowMetrics()
                DebugLogger.event(
                    "BubbleService",
                    "Foreground package=$packageName game=$foregroundIsGame " +
                        "fullscreen=$isFullscreen",
                )
                reconcileBubbleVisibility()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        DebugLogger.event("BubbleService", "onStartCommand action=${intent?.action ?: "null"}")
        if (intent?.action == ACTION_STOP) {
            serviceScope.launch {
                preferences.setBubbleEnabled(false)
                stopSelf()
            }
            return START_NOT_STICKY
        }

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlayView?.post {
            applySavedPosition(settings.bubbleX, settings.bubbleY)
            updateFullscreenFromWindowMetrics()
            reconcileBubbleVisibility()
        }
    }

    override fun onDestroy() {
        DebugLogger.event("BubbleService", "Service destroyed")
        BubbleRuntimeState.isRunning = false
        unlockHandler.removeCallbacks(unlockCheckRunnable)
        unlockHandler.removeCallbacks(unlockRefreshRunnable)
        if (receiverRegistered) {
            unregisterReceiver(screenStateReceiver)
            receiverRegistered = false
        }
        removeOverlayWindow()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onTap() {
        handleGesture(BubbleGesture.SINGLE)
    }

    override fun onSwipeUp() {
        handleGesture(BubbleGesture.UP)
    }

    override fun onSwipeDown() {
        handleGesture(BubbleGesture.DOWN)
    }

    override fun onSwipeLeft() {
        handleGesture(BubbleGesture.LEFT)
    }

    override fun onSwipeRight() {
        handleGesture(BubbleGesture.RIGHT)
    }

    override fun onDragStart() {
        isDragging = true
        DebugLogger.event(
            "BubbleService",
            "Drag started vibration=${settings.dragVibrationEnabled}",
        )
        if (settings.dragVibrationEnabled) {
            vibrateForGesture()
        }
    }

    override fun onDrag(rawX: Float, rawY: Float) {
        val view = overlayView ?: return
        val params = layoutParams ?: return
        val bounds = safeBounds()
        params.x = (rawX - view.width / 2f).roundToInt()
            .coerceIn(bounds.left, bounds.right - view.width)
        params.y = (rawY - view.height / 2f).roundToInt()
            .coerceIn(bounds.top, bounds.bottom - view.height)
        windowManager.updateViewLayout(view, params)
    }

    override fun onDragEnd() {
        if (!isDragging) return
        isDragging = false

        val view = overlayView ?: return
        val params = layoutParams ?: return
        val bounds = safeBounds()
        val horizontalRange = (bounds.width() - view.width).coerceAtLeast(1)
        val verticalRange = (bounds.height() - view.height).coerceAtLeast(1)
        val normalizedX = (params.x - bounds.left).toFloat() / horizontalRange
        val normalizedY = (params.y - bounds.top).toFloat() / verticalRange

        serviceScope.launch {
            preferences.setBubblePosition(normalizedX, normalizedY)
        }
    }

    private fun showOverlay() {
        if (overlayView != null) return
        val bubbleSize = dp(BUBBLE_SIZE_DP)
        val view = FloatingBubbleView(this, this).apply {
            alpha = settings.bubbleOpacity
            setVisualScale(settings.bubbleSizeScale)
            visibility = View.VISIBLE
            setOnApplyWindowInsetsListener { _, insets ->
                val compatInsets = WindowInsetsCompat.toWindowInsetsCompat(insets)
                val fullscreen =
                    !compatInsets.isVisible(WindowInsetsCompat.Type.statusBars()) &&
                        !compatInsets.isVisible(WindowInsetsCompat.Type.navigationBars())
                updateFullscreenMode(fullscreen)
                insets
            }
        }
        val params = WindowManager.LayoutParams(
            bubbleSize,
            bubbleSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
        }

        overlayView = view
        layoutParams = params
        applySavedPosition(settings.bubbleX, settings.bubbleY, updateView = false)
        runCatching {
            windowManager.addView(view, params)
        }.onSuccess {
            overlayWindowShown = true
            ViewCompat.requestApplyInsets(view)
        }.onFailure { error ->
            overlayView = null
            layoutParams = null
            overlayWindowShown = null
            DebugLogger.error("BubbleService", "Failed to attach overlay window", error)
        }
    }

    private fun applySavedPosition(
        normalizedX: Float,
        normalizedY: Float,
        updateView: Boolean = true,
    ) {
        val view = overlayView ?: return
        val params = layoutParams ?: return
        val bounds = safeBounds()
        val width = view.width.takeIf { it > 0 } ?: dp(BUBBLE_SIZE_DP)
        val height = view.height.takeIf { it > 0 } ?: dp(BUBBLE_SIZE_DP)
        val horizontalRange = (bounds.width() - width).coerceAtLeast(1)
        val verticalRange = (bounds.height() - height).coerceAtLeast(1)

        params.x = bounds.left + (normalizedX.coerceIn(0f, 1f) * horizontalRange).roundToInt()
        params.y = bounds.top + (normalizedY.coerceIn(0f, 1f) * verticalRange).roundToInt()

        if (updateView) {
            runCatching { windowManager.updateViewLayout(view, params) }
        }
    }

    private fun handleGesture(gesture: BubbleGesture) {
        val binding = settings.binding(gesture)
        DebugLogger.event(
            "BubbleService",
            "Gesture=${gesture.storageValue} action=${binding.action.storageValue} " +
                "vibration=${binding.vibrationEnabled}",
        )
        if (binding.action == BubbleAction.NONE) return

        val succeeded = when (binding.action) {
            BubbleAction.NONE -> false
            BubbleAction.BACK -> performAccessibilityAction(
                AccessibilityService.GLOBAL_ACTION_BACK,
            )
            BubbleAction.HOME -> performAccessibilityAction(
                AccessibilityService.GLOBAL_ACTION_HOME,
            )
            BubbleAction.LOCK_SCREEN -> performAccessibilityAction(
                AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN,
            )
            BubbleAction.RECENTS -> performAccessibilityAction(
                AccessibilityService.GLOBAL_ACTION_RECENTS,
            )
            BubbleAction.NOTIFICATIONS -> performAccessibilityAction(
                AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS,
            )
            BubbleAction.QUICK_SETTINGS -> QuickActionAccessibilityService.openQuickSettings()
                .also { success -> if (!success) showAccessibilityUnavailable() }
            BubbleAction.OPEN_APP -> openSelectedApp(binding)
            BubbleAction.VOLUME_UP -> changeMediaVolume(AudioManager.ADJUST_RAISE)
            BubbleAction.VOLUME_DOWN -> changeMediaVolume(AudioManager.ADJUST_LOWER)
            BubbleAction.FLASHLIGHT -> toggleFlashlight()
            BubbleAction.SCREENSHOT -> takeScreenshot()
            BubbleAction.OPEN_TOOLBOX -> openToolbox()
        }

        if (succeeded && binding.vibrationEnabled) {
            vibrateForGesture()
        }
    }

    private fun performAccessibilityAction(actionId: Int): Boolean {
        val succeeded = QuickActionAccessibilityService.perform(actionId)
        if (!succeeded) {
            showAccessibilityUnavailable()
        }
        return succeeded
    }

    private fun showAccessibilityUnavailable() {
        DebugLogger.event("BubbleService", "Accessibility action failed: service unavailable")
        Toast.makeText(
            this,
            R.string.accessibility_unavailable_toast,
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun openToolbox(): Boolean {
        DebugLogger.event("BubbleService", "Opening toolbox from bubble action")
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        }
        return runCatching { startActivity(intent) }.isSuccess
    }

    private fun openSelectedApp(binding: GestureBinding): Boolean {
        val packageName = binding.targetPackage
        if (packageName == null) {
            showActionError(R.string.choose_target_app_first)
            return false
        }
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            showActionError(R.string.target_app_unavailable)
            return false
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            startActivity(intent)
            true
        }.getOrElse {
            DebugLogger.error("BubbleService", "Target app launch failed", it)
            showActionError(R.string.target_app_unavailable)
            false
        }
    }

    private fun changeMediaVolume(direction: Int): Boolean {
        return runCatching {
            val audioManager = getSystemService(AudioManager::class.java)
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                direction,
                AudioManager.FLAG_SHOW_UI,
            )
        }.isSuccess
    }

    private fun toggleFlashlight(): Boolean {
        return runCatching {
            val cameraManager = getSystemService(CameraManager::class.java)
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: run {
                showActionError(R.string.flashlight_unavailable)
                return false
            }
            val newState = !torchEnabled
            cameraManager.setTorchMode(cameraId, newState)
            torchEnabled = newState
            true
        }.getOrElse {
            DebugLogger.error("BubbleService", "Flashlight toggle failed", it)
            showActionError(R.string.flashlight_unavailable)
            false
        }
    }

    private fun takeScreenshot(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            showActionError(R.string.screenshot_unavailable)
            return false
        }
        return performAccessibilityAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
    }

    private fun vibrateForGesture() {
        val vibrator = getSystemService(Vibrator::class.java)
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(
            VibrationEffect.createOneShot(
                GESTURE_VIBRATION_MS,
                VibrationEffect.DEFAULT_AMPLITUDE,
            ),
        )
    }

    private fun showActionError(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }

    private fun safeBounds(): Rect {
        val edgePadding = dp(8)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                android.view.WindowInsets.Type.systemBars() or
                    android.view.WindowInsets.Type.displayCutout(),
            )
            val bounds = metrics.bounds
            return Rect(
                bounds.left + insets.left + edgePadding,
                bounds.top + insets.top + edgePadding,
                bounds.right - insets.right - edgePadding,
                bounds.bottom - insets.bottom - edgePadding,
            )
        }

        @Suppress("DEPRECATION")
        val displayMetrics = DisplayMetrics().also {
            windowManager.defaultDisplay.getRealMetrics(it)
        }
        val fallbackTopInset = dp(24)
        return Rect(
            edgePadding,
            fallbackTopInset + edgePadding,
            displayMetrics.widthPixels - edgePadding,
            displayMetrics.heightPixels - edgePadding,
        )
    }

    private fun reconcileBubbleVisibility() {
        if (!settingsLoaded || screenSuspended || !settings.bubbleEnabled) return

        val hiddenForGame = settings.autoHideGames && foregroundIsGame
        val hiddenForFullscreen = settings.autoHideFullscreen && isFullscreen
        val shouldShow = !hiddenForGame && !hiddenForFullscreen
        val reason = when {
            hiddenForGame -> "game:${currentForegroundPackage ?: "unknown"}"
            hiddenForFullscreen -> "fullscreen:${currentForegroundPackage ?: "unknown"}"
            else -> "visible"
        }

        if (shouldShow && overlayView == null) {
            showOverlay()
        }
        setOverlayWindowVisible(shouldShow)
        if (lastVisibilityDescription != reason) {
            lastVisibilityDescription = reason
            DebugLogger.event("BubbleService", "Bubble visibility reason=$reason")
        }
    }

    private fun restoreOverlayAfterUnlock() {
        screenSuspended = false
        if (overlayView == null && settingsLoaded && settings.bubbleEnabled) {
            showOverlay()
        }
        reconcileBubbleVisibility()
        unlockHandler.removeCallbacks(unlockRefreshRunnable)
        unlockHandler.postDelayed(unlockRefreshRunnable, UNLOCK_VISIBILITY_REFRESH_MS)
        DebugLogger.event("BubbleService", "Overlay restored after unlock")
    }

    private fun removeOverlayWindow() {
        overlayView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        overlayView = null
        layoutParams = null
        overlayWindowShown = null
        lastVisibilityDescription = null
    }

    private fun updateFullscreenMode(fullscreen: Boolean) {
        if (isFullscreen == fullscreen) return
        isFullscreen = fullscreen
        DebugLogger.event(
            "BubbleService",
            "Fullscreen state=$fullscreen foreground=${currentForegroundPackage ?: "unknown"}",
        )
        reconcileBubbleVisibility()
    }

    private fun updateFullscreenFromWindowMetrics() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val insets = windowManager.currentWindowMetrics.windowInsets
        updateFullscreenMode(
            !insets.isVisible(android.view.WindowInsets.Type.statusBars()) &&
                !insets.isVisible(android.view.WindowInsets.Type.navigationBars()),
        )
    }

    private fun isGamePackage(packageName: String?): Boolean {
        if (packageName == null ||
            packageName == this.packageName ||
            packageName == SYSTEM_UI_PACKAGE
        ) {
            return false
        }
        return runCatching {
            packageManager.getApplicationInfo(packageName, 0).category ==
                ApplicationInfo.CATEGORY_GAME
        }.getOrDefault(false)
    }

    private fun beginUnlockRecovery() {
        unlockHandler.removeCallbacks(unlockCheckRunnable)
        unlockCheckAttempts = 0
        unlockCheckRunnable.run()
    }

    private fun setOverlayWindowVisible(visible: Boolean) {
        if (overlayWindowShown == visible) return
        val view = overlayView ?: return
        val params = layoutParams ?: return
        params.alpha = if (visible) 1f else 0f
        params.flags = if (visible) {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        runCatching {
            windowManager.updateViewLayout(view, params)
            overlayWindowShown = visible
        }.onFailure { error ->
            DebugLogger.error("BubbleService", "Failed to update overlay visibility", error)
        }
    }

    private fun registerScreenStateReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(
            this,
            screenStateReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true
    }

    private fun createNotificationChannel() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.service_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.service_channel_description)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            OPEN_NOTIFICATION_REQUEST,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = Intent(this, BubbleOverlayService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            STOP_NOTIFICATION_REQUEST,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bubble_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(
                0,
                getString(R.string.notification_stop),
                stopPendingIntent,
            )
            .build()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val CHANNEL_ID = "floating_bubble"
        private const val NOTIFICATION_ID = 4101
        private const val OPEN_NOTIFICATION_REQUEST = 20
        private const val STOP_NOTIFICATION_REQUEST = 21
        private const val ACTION_STOP = "com.duquxian.toolbox.action.STOP_BUBBLE"
        private const val BUBBLE_SIZE_DP = 64
        private const val GESTURE_VIBRATION_MS = 35L
        private const val UNLOCK_CHECK_INTERVAL_MS = 150L
        private const val MAX_UNLOCK_CHECK_ATTEMPTS = 40
        private const val UNLOCK_VISIBILITY_REFRESH_MS = 250L
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, BubbleOverlayService::class.java),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BubbleOverlayService::class.java))
        }
    }
}

