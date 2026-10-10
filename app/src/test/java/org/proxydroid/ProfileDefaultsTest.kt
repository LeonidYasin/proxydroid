package org.proxydroid

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.*

class ProfileDefaultsTest {
    @Test fun newAndResetProfilesUse10808() {
        val profile = Profile()
        assertEquals(10808, profile.port)
        profile.port = 1234
        profile.init()
        assertEquals(10808, profile.port)
    }

    @Test fun missingPreferenceUsesDefaultButCustomPortIsPreserved() {
        val settings = mock(SharedPreferences::class.java)
        `when`(settings.getString("port", "10808")).thenReturn("10808")
        val profile = Profile()
        profile.getProfile(settings)
        assertEquals(10808, profile.port)
        `when`(settings.getString("port", "10808")).thenReturn("9050")
        profile.getProfile(settings)
        assertEquals(9050, profile.port)
    }

    @Test fun jsonMissingPortUsesDefaultAndExplicitPortIsPreserved() {
        val profile = Profile()
        profile.decodeJson("{}")
        assertEquals(10808, profile.port)
        profile.decodeJson("{\"port\":9050}")
        assertEquals(9050, profile.port)
        // Do not silently overwrite existing incomplete/invalid profiles.
        profile.decodeJson("{\"port\":0}")
        assertEquals(0, profile.port)
    }
}
