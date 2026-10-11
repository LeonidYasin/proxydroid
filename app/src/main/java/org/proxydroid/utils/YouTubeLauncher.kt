package org.proxydroid.utils

import android.content.Context
import android.content.Intent
import android.net.Uri

object YouTubeLauncher {
    fun open(context: Context): Boolean {
        val pm = context.packageManager
        for (pkg in listOf("com.google.android.youtube.tv", "com.google.android.youtube")) {
            val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
            if (intent != null && runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return true
        }
        return runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }
}
