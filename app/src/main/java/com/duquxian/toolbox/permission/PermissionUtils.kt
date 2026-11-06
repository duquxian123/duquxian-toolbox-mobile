package com.duquxian.toolbox.permission

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import com.duquxian.toolbox.accessibility.QuickActionAccessibilityService

data class PermissionSnapshot(
    val overlayGranted: Boolean,
    val notificationsGranted: Boolean,
    val accessibilityGranted: Boolean,
) {
    val allGranted: Boolean
        get() = overlayGranted && notificationsGranted && accessibilityGranted
}

object PermissionUtils {
    fun snapshot(context: Context): PermissionSnapshot {
        return PermissionSnapshot(
            overlayGranted = Settings.canDrawOverlays(context),
            notificationsGranted = hasNotificationPermission(context),
            accessibilityGranted = isAccessibilityServiceEnabled(context),
        )
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val accessibilityManager =
            context.getSystemService(AccessibilityManager::class.java) ?: return false
        val targetClassName = QuickActionAccessibilityService::class.java.name

        return accessibilityManager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { serviceInfo ->
                val resolvedInfo = serviceInfo.resolveInfo?.serviceInfo
                resolvedInfo?.packageName == context.packageName &&
                    resolvedInfo.name == targetClassName
            }
    }
}

