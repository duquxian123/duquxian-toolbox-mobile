package com.duquxian.toolbox.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.content.res.ColorStateList
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.duquxian.toolbox.R
import com.duquxian.toolbox.databinding.ActivityPermissionsBinding
import com.duquxian.toolbox.permission.PermissionUtils

class PermissionsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPermissionsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemUi.configure(window, binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.rowOverlayPermission.setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"),
                ),
            )
        }
        binding.rowNotificationPermission.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                },
            )
        }
        binding.rowAccessibilityPermission.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        val permissions = PermissionUtils.snapshot(this)
        renderStatus(binding.statusOverlay, permissions.overlayGranted)
        renderStatus(binding.statusNotification, permissions.notificationsGranted)
        renderStatus(binding.statusAccessibility, permissions.accessibilityGranted)
        renderSummary(permissions.overlayGranted, permissions.notificationsGranted, permissions.accessibilityGranted)
    }

    private fun renderStatus(statusView: TextView, granted: Boolean) {
        statusView.setText(
            if (granted) R.string.permission_granted else R.string.permission_status_action,
        )
        val colorRes = if (granted) R.color.success else R.color.warning
        val backgroundRes = if (granted) R.color.success_soft else R.color.warning_soft
        statusView.setTextColor(
            ContextCompat.getColor(this, colorRes),
        )
        statusView.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, backgroundRes),
        )
    }

    private fun renderSummary(
        overlayGranted: Boolean,
        notificationsGranted: Boolean,
        accessibilityGranted: Boolean,
    ) {
        val grantedCount = listOf(
            overlayGranted,
            notificationsGranted,
            accessibilityGranted,
        ).count { it }
        val allGranted = grantedCount == 3
        val colorRes = if (allGranted) R.color.success else R.color.warning
        val softColorRes = if (allGranted) R.color.success_soft else R.color.warning_soft

        binding.textPermissionSummaryTitle.setText(
            if (allGranted) {
                R.string.permission_summary_ready
            } else {
                R.string.permission_summary_missing
            },
        )
        binding.textPermissionSummaryNote.setText(
            if (allGranted) {
                R.string.permission_summary_ready_note
            } else {
                R.string.permission_summary_missing_note
            },
        )
        binding.textPermissionCount.text = getString(
            R.string.permission_count,
            grantedCount,
            3,
        )
        binding.textPermissionCount.setTextColor(ContextCompat.getColor(this, colorRes))
        binding.imagePermissionSummary.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, colorRes),
        )
        binding.summaryIconContainer.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, softColorRes),
        )
    }
}

