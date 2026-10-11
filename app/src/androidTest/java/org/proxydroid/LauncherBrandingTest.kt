package org.proxydroid

import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class LauncherBrandingTest {
    @Suppress("DEPRECATION")
    @Test fun tvAndPhoneLauncherUseExplicitLocProxyIdentityForEveryLocale() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pm = context.packageManager
        val alias = pm.getActivityInfo(ComponentName(context.packageName, "org.proxydroid.LocProxyTV"), 0)
        val legacy = pm.getActivityInfo(ComponentName(context, ProxyDroid::class.java), 0)
        assertEquals("org.proxydroid.ProxyDroid", alias.targetActivity)
        assertEquals("LocProxyTV", alias.nonLocalizedLabel.toString())
        assertEquals("LocProxyTV", legacy.nonLocalizedLabel.toString())
        assertEquals("LocProxyTV", context.applicationInfo.nonLocalizedLabel.toString())
        assertEquals(R.mipmap.locproxy_launcher, alias.icon)
        assertEquals(R.drawable.locproxy_tv_banner, legacy.banner)
        assertEquals(R.mipmap.locproxy_launcher, context.applicationInfo.icon)
        for (category in listOf(Intent.CATEGORY_LAUNCHER, Intent.CATEGORY_LEANBACK_LAUNCHER)) {
            val matches = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category).setPackage(context.packageName), 0)
            assertEquals("Must not duplicate launcher entries", 1, matches.size)
            assertEquals("org.proxydroid.LocProxyTV", matches.single().activityInfo.name)
            assertEquals("LocProxyTV", matches.single().loadLabel(pm).toString())
        }
        for (language in listOf("en", "ru", "fr", "pt")) {
            val config = Configuration(context.resources.configuration)
            config.setLocale(Locale(language))
            assertEquals("LocProxyTV", context.createConfigurationContext(config).getString(R.string.app_name))
        }
        assertNotNull(alias.loadIcon(pm)); assertNotNull(alias.loadBanner(pm))
        assertNotNull(pm.getActivityInfo(ComponentName(context, ProxyDroid::class.java), 0))
    }
    @Test fun newLauncherAliasOpensTheSameActivityAndKeepsInstalledIdentity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        assertNotNull(intent)
        assertEquals("org.proxydroid.LocProxyTV", intent!!.component!!.className)
        ActivityScenario.launch<ProxyDroid>(intent).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals("org.proxydroid", activity.packageName)
                assertEquals(ProxyDroid::class.java, activity.javaClass)
            }
        }
    }
}
