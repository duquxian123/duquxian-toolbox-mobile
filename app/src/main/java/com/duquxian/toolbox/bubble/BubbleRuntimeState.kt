package com.duquxian.toolbox.bubble

object BubbleRuntimeState {
    @Volatile
    var isRunning: Boolean = false
        internal set
}

