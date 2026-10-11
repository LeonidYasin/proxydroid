# Rust exports name-based JNI symbols. Keep their owner and native signatures.
-keep class org.proxydroid.utils.Tun2SocksHelper { *; }
# Retain actionable stack traces; CI archives mapping.txt for retracing.
-keepattributes SourceFile,LineNumberTable
