package org.proxydroid

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.proxydroid.utils.isValidProxyPort

class ProxyInputValidationTest {
    @Test fun acceptsPortBoundariesAndDefault() {
        listOf("1", "10808", "65535").forEach { assertTrue(isValidProxyPort(it)) }
    }
    @Test fun rejectsInvalidInputWithoutTruncation() {
        listOf("", "0", "65536", "9999999999999", "-1", "abc10808", " 10808", "١٠٨٠٨")
            .forEach { assertFalse(it, isValidProxyPort(it)) }
    }
}
