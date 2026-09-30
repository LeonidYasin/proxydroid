/* proxydroid - Global / Individual Proxy App for Android
 * Copyright (C) 2011 Max Lv <max.c.lv@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.proxydroid

import android.app.Application
import android.util.Log
import org.proxydroid.utils.Utils

class ProxyDroidApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        installGlobalExceptionHandler()
    }

    /**
     * Last-resort safety net: catches any exception that escapes all local
     * try/catch blocks, in any thread. Default Android behaviour kills the
     * process with no user-visible explanation; here we record the reason
     * into [Utils.lastError] (so the UI can surface it on next launch) and
     * log it.
     *
     * NOTE: this CANNOT catch framework-level failures thrown before our
     * code runs (e.g. SecurityException from startForeground with a
     * disallowed FGS type). Those must be fixed in the manifest /
     * startForeground call itself.
     */
    private fun installGlobalExceptionHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val msg = "Uncaught ${throwable.javaClass.simpleName}: " +
                    (throwable.message ?: "no message")
                Log.e(TAG, msg, throwable)
                Utils.setLastError(msg)
            } catch (_: Throwable) {
                // Never let the handler itself crash the process.
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        private const val TAG = "ProxyDroidApp"
    }
}
