package org.proxydroid

import android.content.ComponentName
import android.content.res.Configuration
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class LauncherBrandingTest {
    @Test fun tvAndPhoneLauncherUseExplicitLocProxyIdentityForEveryLocale() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getActivityInfo(ComponentName(context, ProxyDroid::class.java), 0)
        assertEquals(R.mipmap.locproxy_launcher, info.icon)
        assertEquals(R.drawable.locproxy_tv_banner, info.banner)
        assertEquals(R.mipmap.locproxy_launcher, context.applicationInfo.icon)
        for (language in listOf("en", "ru", "fr", "pt")) {
            val config = Configuration(context.resources.configuration)
            config.setLocale(Locale(language))
            val localized = context.createConfigurationContext(config)
            assertEquals("LocProxyTV", localized.getString(info.labelRes))
            assertEquals("LocProxyTV", localized.getString(context.applicationInfo.labelRes))
        }
        assertNotNull(info.loadIcon(context.packageManager))
        assertNotNull(info.loadBanner(context.packageManager))
    }
}
