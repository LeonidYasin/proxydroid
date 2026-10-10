package org.proxydroid.utils

object BootPolicy {
    val actions = setOf("android.intent.action.BOOT_COMPLETED", "android.intent.action.MY_PACKAGE_REPLACED",
        "android.intent.action.QUICKBOOT_POWERON", "com.htc.intent.action.QUICKBOOT_POWERON")
    fun shouldStart(action: String?, enabled: Boolean, unlocked: Boolean) = unlocked && enabled && action in actions
}
