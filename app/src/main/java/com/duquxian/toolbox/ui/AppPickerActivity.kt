package com.duquxian.toolbox.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.duquxian.toolbox.R
import com.duquxian.toolbox.databinding.ActivityAppPickerBinding
import java.util.Locale

class AppPickerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAppPickerBinding
    private val allApps = mutableListOf<AppEntry>()
    private val filteredApps = mutableListOf<AppEntry>()
    private lateinit var adapter: AppAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemUi.configure(window, binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        allApps.addAll(loadLaunchableApps())
        filteredApps.addAll(allApps)
        adapter = AppAdapter(filteredApps)
        binding.listApps.adapter = adapter
        updateEmptyState(isSearching = false)

        binding.listApps.setOnItemClickListener { _, _, position, _ ->
            val app = adapter.getItem(position) ?: return@setOnItemClickListener
            setResult(
                Activity.RESULT_OK,
                Intent().apply {
                    putExtra(EXTRA_PACKAGE_NAME, app.packageName)
                    putExtra(EXTRA_APP_LABEL, app.label)
                },
            )
            finish()
        }

        binding.editSearch.doAfterTextChanged { text ->
            val query = text?.toString()?.trim().orEmpty()
            filteredApps.clear()
            if (query.isBlank()) {
                filteredApps.addAll(allApps)
            } else {
                filteredApps.addAll(
                    allApps.filter { app ->
                        app.label.contains(query, ignoreCase = true) ||
                            app.packageName.contains(query, ignoreCase = true)
                    },
                )
            }
            adapter.notifyDataSetChanged()
            updateEmptyState(isSearching = query.isNotBlank())
        }
    }

    private fun loadLaunchableApps(): List<AppEntry> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        return packageManager.queryIntentActivities(
            launcherIntent,
            android.content.pm.PackageManager.MATCH_ALL,
        ).mapNotNull { resolveInfo ->
            val packageName = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
            AppEntry(
                packageName = packageName,
                label = resolveInfo.loadLabel(packageManager).toString(),
                resolveInfo = resolveInfo,
            )
        }.distinctBy { it.packageName }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    private fun updateEmptyState(isSearching: Boolean) {
        val isEmpty = filteredApps.isEmpty()
        binding.textEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.listApps.visibility = if (isEmpty) View.GONE else View.VISIBLE
        if (isEmpty) {
            binding.textEmpty.setText(
                if (isSearching) R.string.app_search_empty else R.string.no_launchable_apps,
            )
        }
    }

    private inner class AppAdapter(
        items: MutableList<AppEntry>,
    ) : ArrayAdapter<AppEntry>(this, 0, items) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val textView = convertView as? TextView ?: TextView(parent.context).apply {
                layoutParams = AbsListView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(60),
                )
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(20), 0, dp(20), 0)
                setCompoundDrawablePadding(dp(14))
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
            }
            val app = getItem(position) ?: return textView
            val icon = app.resolveInfo.loadIcon(packageManager)
            icon.setBounds(0, 0, dp(32), dp(32))
            textView.text = app.label
            textView.setCompoundDrawablesRelative(icon, null, null, null)
            return textView
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private data class AppEntry(
        val packageName: String,
        val label: String,
        val resolveInfo: ResolveInfo,
    )

    companion object {
        const val EXTRA_PACKAGE_NAME = "selected_package_name"
        const val EXTRA_APP_LABEL = "selected_app_label"
    }
}

