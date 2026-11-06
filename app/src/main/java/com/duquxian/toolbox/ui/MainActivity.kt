package com.duquxian.toolbox.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.duquxian.toolbox.R
import com.duquxian.toolbox.bubble.BubbleOverlayService
import com.duquxian.toolbox.bubble.BubbleRuntimeState
import com.duquxian.toolbox.data.BubblePreferences
import com.duquxian.toolbox.data.ToolboxSettings
import com.duquxian.toolbox.databinding.ActivityMainBinding
import com.duquxian.toolbox.debug.DebugLogger
import com.duquxian.toolbox.permission.PermissionSnapshot
import com.duquxian.toolbox.permission.PermissionUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var preferences: BubblePreferences
    private var refreshJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemUi.configure(window, binding.root)
        DebugLogger.event("MainActivity", "Home created")

        preferences = BubblePreferences(this)
        binding.cardBubble.setOnClickListener {
            DebugLogger.event("MainActivity", "Bubble tool row clicked")
            runCatching {
                startActivity(Intent(this, SettingsActivity::class.java))
            }.onFailure { error ->
                DebugLogger.error("MainActivity", "Failed to open bubble settings", error)
            }
        }
        binding.cardTravelExpense.setOnClickListener {
            DebugLogger.event("MainActivity", "Travel expense tool clicked")
            startActivity(Intent(this, TravelExpenseActivity::class.java))
        }
        binding.buttonAbout.setOnClickListener {
            showAboutDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun refreshUi() {
        refreshJob?.cancel()
        refreshJob = lifecycleScope.launch {
            val settings = preferences.settings.first()
            val permissions = PermissionUtils.snapshot(this@MainActivity)

            if (settings.bubbleEnabled &&
                permissions.allGranted &&
                !BubbleRuntimeState.isRunning
            ) {
                BubbleOverlayService.start(this@MainActivity)
            }

            renderUi(settings, permissions, BubbleRuntimeState.isRunning)
        }
    }

    private fun renderUi(
        settings: ToolboxSettings,
        permissions: PermissionSnapshot,
        running: Boolean,
    ) {
        val statusText: Int
        val statusColor: Int

        when {
            settings.bubbleEnabled && !permissions.allGranted -> {
                statusText = R.string.status_missing_permissions
                statusColor = R.color.warning
            }
            running && settings.bubbleEnabled -> {
                statusText = R.string.status_running
                statusColor = R.color.success
            }
            settings.bubbleEnabled -> {
                statusText = R.string.status_starting
                statusColor = R.color.warning
            }
            else -> {
                statusText = R.string.status_not_enabled
                statusColor = R.color.text_secondary
            }
        }

        binding.textStatus.setText(statusText)
        ContextCompat.getColor(this, statusColor).let { color ->
            binding.textStatus.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.surface_alt),
            )
            binding.textStatus.setTextColor(color)
        }
    }

    private fun showAboutDialog() {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
        val versionLine = getString(
            R.string.about_version_line,
            packageInfo.versionName ?: "unknown",
            packageInfo.longVersionCode,
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.about_dialog_title)
            .setMessage(
                getString(
                    R.string.about_dialog_message_with_version,
                    getString(R.string.about_dialog_message),
                    versionLine,
                ),
            )
            .setNeutralButton(R.string.debug_log) { _, _ ->
                startActivity(Intent(this, DebugLogActivity::class.java))
            }
            .setPositiveButton(R.string.close, null)
            .show()
    }
}

