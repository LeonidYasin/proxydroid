package org.proxydroid.release;

import android.app.Activity;
import android.util.Log;
import androidx.test.core.app.ActivityScenario;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.VpnService;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Method;
import java.util.zip.ZipFile;
import static org.junit.Assert.*;

/** Runs against the signed, R8/resource-shrunk APK, not the debug variant. */
@RunWith(AndroidJUnit4.class)
public class MinifiedReleaseSmokeTest {
    @Test(timeout = 60000) public void releaseIdentityAndNoLegacyExecutables() throws Exception {
        Log.i("ReleaseSmoke", "identity/assets start");
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("org.proxydroid", context.getPackageName());
        assertEquals("LocProxyTV", context.getApplicationInfo().loadLabel(context.getPackageManager()).toString());
        assertEquals(0, context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE);
        android.content.pm.ActivityInfo info = context.getPackageManager().getActivityInfo(
                new android.content.ComponentName(context, "org.proxydroid.ProxyDroid"), 0);
        assertEquals("LocProxyTV", info.loadLabel(context.getPackageManager()).toString());
        saveDrawable(context, info.loadBanner(context.getPackageManager()), "launcher-banner.png", 320, 180);
        saveDrawable(context, info.loadIcon(context.getPackageManager()), "launcher-icon.png", 108, 108);
        try (ZipFile apk = new ZipFile(context.getApplicationInfo().sourceDir)) {
            assertNotNull(apk.getEntry("assets/pages/about.html"));
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = apk.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                assertFalse(name.startsWith("assets/armeabi-v7a/") || name.startsWith("assets/x86/"));
            }
        }
    }

    @Test(timeout = 60000) public void optimizedLauncherStarts() throws Exception {
        Log.i("ReleaseSmoke", "launcher start");
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent = new Intent().setClassName(context, "org.proxydroid.ProxyDroid")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // ActivityScenario has a bounded lifecycle transition; never use the
        // unbounded startActivitySync/waitForIdleSync on a Compose launcher.
        try (ActivityScenario<Activity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> assertNotNull(activity.getWindow().getDecorView()));
        }
        Log.i("ReleaseSmoke", "launcher passed");
    }

    @Test(timeout = 60000) public void nativeEntryPointsSurviveR8() throws Exception {
        Log.i("ReleaseSmoke", "JNI start");
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Class<?> type = Class.forName("org.proxydroid.utils.Tun2SocksHelper", true, context.getClassLoader());
        Object helper = type.getDeclaredConstructor().newInstance();
        Method start = type.getDeclaredMethod("nativeStart", VpnService.class, int.class, int.class,
                String.class, String.class, int.class, String.class, String.class);
        start.setAccessible(true);
        // Empty host is rejected before accessing VpnService/TUN: no network,
        // VPN permission or bogus descriptor is used by this linkage test.
        assertEquals(-1, ((Number)start.invoke(helper, null, -1, 1500, "socks5", "", 9, "", "")).intValue());
        Method stop = type.getDeclaredMethod("nativeStop");
        stop.setAccessible(true);
        stop.invoke(helper);
    }
    private static void saveDrawable(Context context, android.graphics.drawable.Drawable drawable,
            String name, int width, int height) throws Exception {
        assertNotNull(drawable);
        android.graphics.Bitmap image = android.graphics.Bitmap.createBitmap(width, height,
                android.graphics.Bitmap.Config.ARGB_8888);
        drawable.setBounds(0, 0, width, height);
        drawable.draw(new android.graphics.Canvas(image));
        // Export only generated public launcher pixels to the instrumentation
        // shell's CI temp directory. Do not weaken scoped-storage protection.
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bytes));
        String encoded = android.util.Base64.encodeToString(bytes.toByteArray(), android.util.Base64.NO_WRAP);
        android.os.ParcelFileDescriptor output = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().executeShellCommand("sh -c echo${IFS}'" + encoded +
                        "'|base64${IFS}-d>/data/local/tmp/" + name);
        try (java.io.InputStream stream = new android.os.ParcelFileDescriptor.AutoCloseInputStream(output)) {
            byte[] buffer = new byte[1024];
            while (stream.read(buffer) != -1) { }
        }
        image.recycle();
    }

}
