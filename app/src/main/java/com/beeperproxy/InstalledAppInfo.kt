package com.beeperproxy

data class InstalledAppInfo(
    val packageName: String,
    val label: String,
    val icon: android.graphics.drawable.Drawable?
)
