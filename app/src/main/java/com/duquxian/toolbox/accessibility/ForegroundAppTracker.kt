package com.duquxian.toolbox.accessibility

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ForegroundAppTracker {
    private val mutablePackageName = MutableStateFlow<String?>(null)
    val packageName: StateFlow<String?> = mutablePackageName.asStateFlow()

    fun update(packageName: String?) {
        val sanitized = packageName?.takeIf { it.isNotBlank() }
        if (mutablePackageName.value != sanitized) {
            mutablePackageName.value = sanitized
        }
    }
}

