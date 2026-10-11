package org.proxydroid.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** Read-only platform evidence; cannot inspect or alter an OEM launcher's private database. */
object LauncherIdentity {
    @Suppress("DEPRECATION")
    fun lines(context: Context): List<String> {
        val pm = context.packageManager
        val lines = mutableListOf<String>()
        try {
            lines += "Android application label: ${context.applicationInfo.loadLabel(pm)}; package ${context.packageName}"
            for (category in listOf(Intent.CATEGORY_LAUNCHER, Intent.CATEGORY_LEANBACK_LAUNCHER)) {
                val matches = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category).setPackage(context.packageName), 0)
                lines += "Android $category entries: ${matches.size}"
                matches.forEach { entry ->
                    lines += "Launcher component: ${entry.activityInfo.name}; Android label: ${entry.loadLabel(pm)}; target: ${entry.activityInfo.targetActivity ?: "self"}"
                }
            }
            val legacy = pm.getActivityInfo(ComponentName(context.packageName, "org.proxydroid.ProxyDroid"), 0)
            lines += "Legacy component label: ${legacy.loadLabel(pm)} (retained for old shortcuts, not a second launcher entry)"
            val home = pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
            if (home != null) {
                val version = runCatching { pm.getPackageInfo(home.packageName, 0).versionName }.getOrNull()
                lines += "Default HOME launcher: ${home.packageName}/${home.name}; version ${version ?: "unknown"}; Android label: ${home.loadLabel(pm)}"
            } else lines += "Default HOME launcher: unavailable / no default selected"
            lines += "An OEM launcher may display its own cached/catalogue name; its private label database is not readable by this app. Android labels above are authoritative package-manager values, not a screenshot of HOME."
        } catch (failure: Exception) { lines += "Launcher identity read incomplete: ${failure.javaClass.simpleName}" }
        return lines
    }
}
