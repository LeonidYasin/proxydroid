package org.proxydroid.ui

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.proxydroid.R

class AppBrandingTest {
    @Test fun displayNameChangesWithoutChangingInstalledIdentity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("LocProxyTV", context.getString(R.string.app_name))
        assertEquals("LocProxyTV", context.applicationInfo.loadLabel(context.packageManager).toString())
        assertEquals("org.proxydroid", context.packageName)
    }
}
