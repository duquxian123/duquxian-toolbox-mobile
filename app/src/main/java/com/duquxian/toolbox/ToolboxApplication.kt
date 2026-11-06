package com.duquxian.toolbox

import android.app.Application
import com.duquxian.toolbox.debug.DebugLogger

class ToolboxApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DebugLogger.initialize(this)
    }
}

