package org.proxydroid.utils

/** Reject empty, non-decimal and out-of-range input instead of truncating it. */
fun isValidProxyPort(value: String): Boolean =
    value.isNotEmpty() && value.all { it in '0'..'9' } &&
        (value.toIntOrNull()?.let { it in 1..65535 } == true)
