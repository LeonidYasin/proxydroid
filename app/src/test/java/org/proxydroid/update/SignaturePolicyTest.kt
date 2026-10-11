package org.proxydroid.update
import org.junit.Assert.*
import org.junit.Test
class SignaturePolicyTest {
    @Test fun matchingCertificateIsAccepted() { assertNull(SignatureReport(setOf("abc"), setOf("abc")).failure) }
    @Test fun differentCertificateIsRefusedWithExplanation() { assertTrue(SignatureReport(setOf("debug"), setOf("release")).failure!!.contains("differs")) }
    @Test fun missingInstalledCertificateIsNotClaimedDifferent() { assertTrue(SignatureReport(emptySet(), setOf("release")).failure!!.contains("could not read the installed")) }
    @Test fun missingDownloadedCertificateIsRefused() { assertTrue(SignatureReport(setOf("release"), emptySet()).failure!!.contains("could not read the downloaded")) }
}
