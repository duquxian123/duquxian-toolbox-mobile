package com.duquxian.toolbox.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import com.duquxian.toolbox.debug.DebugLogger

class QuickActionAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        DebugLogger.event("AccessibilityService", "Connected")
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.packageName?.toString()?.let(ForegroundAppTracker::update)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        DebugLogger.event("AccessibilityService", "Unbound")
        ForegroundAppTracker.update(null)
        if (instance === this) {
            instance = null
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        ForegroundAppTracker.update(null)
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    companion object {
        @Volatile
        private var instance: QuickActionAccessibilityService? = null

        fun perform(actionId: Int): Boolean {
            return instance?.performGlobalAction(actionId) == true
        }

        fun openQuickSettings(): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                perform(GLOBAL_ACTION_QUICK_SETTINGS)
            ) {
                return true
            }
            return perform(GLOBAL_ACTION_NOTIFICATIONS)
        }
    }
}

