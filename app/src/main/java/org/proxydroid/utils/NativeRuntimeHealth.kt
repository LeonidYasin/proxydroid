package org.proxydroid.utils

/** Read-only aggregate JNI observation, NOT a successful end-to-end VPN/video test. */
class NativeRuntimeHealth private constructor(private val values: LongArray) {
    val running get() = values[0] == 1L
    val taskFinished get() = values[1] == 1L
    val coreExitCode get() = values[15]
    fun lines(now: Long = System.currentTimeMillis()): List<String> = listOf(
        "Native runtime: busy=$running; root task finished=$taskFinished; core exit code=$coreExitCode (0=none/normal Stop, 1=reader, 2=TCP runner, 3=stack, 4=acceptor, 5=writer, 6=panic, 7=TUN read, 8=TUN write)",
        "Native TUN reader heartbeat age ms: ${if (values[2] == 0L) "not recorded" else (now-values[2]).coerceAtLeast(0).toString()}; heartbeat proves loop activity only, NOT forwarding",
        "Native session counters: TUN rx packets=${values[3]}; tx packets=${values[4]}; TCP connect attempts=${values[5]}; accepted=${values[6]}; failures=${values[7]}; handshake timeouts=${values[16]}; relay errors=${values[14]}",
        "Native DNS queries=${values[8]}; DoH failures=${values[9]}; unsupported UDP/non-DNS packets=${values[10]}; IPv6 seen=${values[11]}; TUN read failures=${values[12]}; write drops=${values[13]}",
        "Counters are aggregates since native start (or last stopped session); no domains, payloads or credentials. Idle/zero counters alone do not prove a failure. Compare two reports during playback/stall."
    )
    companion object {
        fun parse(values: LongArray): NativeRuntimeHealth? = values.takeIf { it.size == 17 && it[0] in 0..1 && it[1] in 0..1 }?.let { NativeRuntimeHealth(it.copyOf()) }
        fun startFailure(code: Int): String = when (code) {
            -10 -> "NATIVE_ALREADY_RUNNING: old native session/cleanup is still active; use Stop, then reconnect"
            -11 -> "NATIVE_INVALID_CONFIG: check profile/protocol/endpoint"
            -12 -> "NATIVE_RUNTIME_INIT_FAILED: could not initialise native runtime"
            -13 -> "NATIVE_DOH_CLIENT_INIT_FAILED: could not initialise proxy/DoH client; NOT proof of an unreachable proxy"
            -14 -> "NATIVE_IO_INIT_FAILED: native TUN/runtime I/O initialisation failed"
            -2 -> "NATIVE_START_PANIC: native initialisation panicked"
            else -> "NATIVE_START_FAILED: rc=$code; not enough evidence to blame proxy reachability"
        }
    }
}
