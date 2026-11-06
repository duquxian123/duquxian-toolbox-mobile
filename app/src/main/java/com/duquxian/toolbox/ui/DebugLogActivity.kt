package com.duquxian.toolbox.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.duquxian.toolbox.R
import com.duquxian.toolbox.databinding.ActivityDebugLogBinding
import com.duquxian.toolbox.debug.DebugLogger
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DebugLogActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDebugLogBinding
    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    val content = DebugLogger.read().ifBlank { getString(R.string.log_empty) }
                    contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                        writer.write(content)
                    } ?: error("Unable to open export destination")
                }.isSuccess
            }
            Toast.makeText(
                this@DebugLogActivity,
                if (saved) R.string.log_saved else R.string.log_save_failed,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDebugLogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemUi.configure(window, binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.buttonCopy.setOnClickListener {
            val clipboard = getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(
                ClipData.newPlainText(
                    getString(R.string.debug_log_title),
                    DebugLogger.readSupportSummary().ifBlank {
                        getString(R.string.log_empty)
                    },
                ),
            )
            Toast.makeText(this, R.string.log_copied, Toast.LENGTH_SHORT).show()
        }
        binding.buttonExport.setOnClickListener {
            val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val baseName = getString(R.string.export_log_file_name)
            exportLauncher.launch(baseName.replace(".txt", "-$timestamp.txt"))
        }
        binding.buttonClear.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.clear_log_confirm_title)
                .setMessage(R.string.clear_log_confirm_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.clear) { _, _ ->
                    DebugLogger.clear()
                    loadLogs()
                }
                .show()
        }
        loadLogs()
    }

    private fun loadLogs() {
        lifecycleScope.launch {
            val content = withContext(Dispatchers.IO) { DebugLogger.read() }
            binding.textLog.setText(
                content.ifBlank { getString(R.string.log_empty) },
            )
        }
    }
}

