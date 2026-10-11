package org.proxydroid

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Process

/** User-requested self restart, in a separate foreground process. No alarms or other-app kill. */
class RestartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val oldPid = intent.getIntExtra("old_pid", -1)
        val manager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        val ownMain = manager.runningAppProcesses?.firstOrNull {
            it.pid == oldPid && it.uid == Process.myUid() && it.processName == packageName
        }
        if (ownMain == null || oldPid == Process.myPid()) { finish(); return }
        Process.killProcess(oldPid)
        startActivity(Intent(this, ProxyDroid::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
        Process.killProcess(Process.myPid())
    }
}
