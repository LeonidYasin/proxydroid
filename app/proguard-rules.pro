# Rust exports name-based JNI symbols. Keep their owner and native signatures.
-keep class org.proxydroid.utils.Tun2SocksHelper { *; }
# Retain actionable stack traces; CI archives mapping.txt for retracing.
-keepattributes SourceFile,LineNumberTable

# AndroidJUnitRunner resolves this shared app/test dependency by its original
# name. Keep the tiny bridge so signed-release instrumentation can start.
-keep class androidx.tracing.Trace { *; }
