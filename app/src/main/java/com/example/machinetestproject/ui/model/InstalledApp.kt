package com.example.machinetestproject.ui.model

import android.graphics.drawable.Drawable

data class InstalledApp(
    val appName: String?,
    val packageName: String?,
    val installTime: String?,
    val appIcon: Drawable?
)

