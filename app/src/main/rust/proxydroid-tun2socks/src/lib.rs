//! JNI entry points for ProxyDroid's tun2socks layer.
//!
//! The functions exported here are loaded by `org.proxydroid.utils.Tun2SocksHelper`.
//! Their JNI symbol names (`Java_org_proxydroid_...`) form a stable ABI: changing
//! them breaks the running app. The Rust-side internals may evolve freely.

mod dns_table;
mod doh_client;
mod error;
mod logging;
mod health;
mod protect;
mod tun2socks;

use jni::objects::{JClass, JObject, JString};
use jni::sys::jint;
use jni::JNIEnv;
use std::panic::AssertUnwindSafe;
use std::sync::OnceLock;

use crate::error::Tun2SocksError;
use crate::tun2socks::{ProxyKind, UpstreamConfig};

/// Process-wide tokio runtime. Lazily created on first `nativeStart`.
static RUNTIME: OnceLock<tokio::runtime::Runtime> = OnceLock::new();

/// Return the shared tokio runtime, building it on first call.
///
/// Returns `Err` if the runtime could not be constructed; callers must surface
/// the error rather than panic across the FFI boundary.
pub(crate) fn try_get_runtime() -> Result<&'static tokio::runtime::Runtime, Tun2SocksError> {
    if let Some(rt) = RUNTIME.get() {
        return Ok(rt);
    }
    let rt = tokio::runtime::Builder::new_multi_thread()
        .worker_threads(2)
        .enable_all()
        .build()
        .map_err(Tun2SocksError::Runtime)?;
    // get_or_init runs the closure at most once across threads; if another
    // thread raced ahead and inserted first, our freshly-built runtime is
    // dropped.
    Ok(RUNTIME.get_or_init(|| rt))
}

/// Convert a `JString` to `Option<String>`. Returns `None` for null or on
/// conversion failure.
fn jstring_to_opt(env: &mut JNIEnv, s: JString) -> Option<String> {
    if s.is_null() {
        return None;
    }
    env.get_string(&s).ok().map(|s| s.into())
}

/// JNI entry: start the tun2socks loop.
///
/// Returns 0 on success, -1 on an expected error (logged), and -2 if the Rust
/// implementation panicked. Never propagates panics across the FFI boundary.
///
/// # ABI
///
/// Invoked from the JVM via the symbol
/// `Java_org_proxydroid_utils_Tun2SocksHelper_nativeStart`. Renaming this
/// function breaks the Android app.
#[no_mangle]
#[allow(clippy::too_many_arguments)]
pub extern "system" fn Java_org_proxydroid_utils_Tun2SocksHelper_nativeStart(
    mut env: JNIEnv,
    _class: JClass,
    vpn_service: JObject,
    tun_fd: jint,
    _mtu: jint,
    proxy_type: JString,
    socks_host: JString,
    socks_port: jint,
    socks_user: JString,
    socks_password: JString,
) -> jint {
    logging::init_android_logger();

    // Wrap the body in catch_unwind so a stray panic in Rust doesn't unwind
    // into the JVM (which is undefined behaviour).
    let result = std::panic::catch_unwind(AssertUnwindSafe(|| {
        let kind_str = jstring_to_opt(&mut env, proxy_type).unwrap_or_else(|| "socks5".into());
        let host = match jstring_to_opt(&mut env, socks_host) {
            Some(s) if !s.is_empty() => s,
            _ => return Err(Tun2SocksError::InvalidConfig("socks_host is empty".into())),
        };
        let user = jstring_to_opt(&mut env, socks_user).filter(|s| !s.is_empty());
        let password = jstring_to_opt(&mut env, socks_password).filter(|s| !s.is_empty());

        if !vpn_service.is_null() {
            protect::set_vpn_service(&env, &vpn_service);
        } else {
            logging::bridge_log("nativeStart: WARNING vpn_service is null; protect() unavailable");
        }

        let cfg = UpstreamConfig {
            kind: ProxyKind::parse(&kind_str),
            host,
            port: socks_port as u16,
            user,
            password,
        };

        tun2socks::start(tun_fd, cfg)
    }));

    match result {
        Ok(Ok(())) => 0,
        Ok(Err(e)) => {
            let code = match &e {
                Tun2SocksError::AlreadyRunning => -10,
                Tun2SocksError::InvalidConfig(_) => -11,
                Tun2SocksError::Runtime(_) => -12,
                Tun2SocksError::HttpClient(_) => -13,
                Tun2SocksError::Io(_) => -14,
            };
            logging::bridge_log(&format!("nativeStart: code={} error: {}", code, e));
            code
        }
        Err(_) => {
            logging::bridge_log("nativeStart: panicked");
            -2
        }
    }
}

/// JNI entry: stop the tun2socks loop and release the stored VpnService ref.
///
/// # ABI
///
/// Invoked from the JVM via the symbol
/// `Java_org_proxydroid_utils_Tun2SocksHelper_nativeStop`. Renaming this
/// function breaks the Android app.
#[no_mangle]
pub extern "system" fn Java_org_proxydroid_utils_Tun2SocksHelper_nativeStop(
    mut env: JNIEnv,
    _class: JClass,
) {
    // Catch panics for the same reason as nativeStart.
    let result = std::panic::catch_unwind(|| {
        if !tun2socks::stop() { return false; }
        protect::clear_vpn_service();
        logging::bridge_log("nativeStop: completed");
        true
    });
    if !matches!(result, Ok(true)) {
        let _ = env.throw_new("java/lang/IllegalStateException", "Native VPN stop did not complete; reconnect refused");
    }
}

/// Read-only JNI health snapshot. Does not create a runtime or initiate network requests.
#[no_mangle]
pub extern "system" fn Java_org_proxydroid_utils_Tun2SocksHelper_nativeStatus(
    mut env: JNIEnv, _class: JClass,
) -> jni::sys::jlongArray {
    let values = tun2socks::health_snapshot();
    match env.new_long_array(values.len() as i32) {
        Ok(array) => {
            if env.set_long_array_region(&array, 0, &values).is_err() { return std::ptr::null_mut(); }
            array.into_raw()
        }
        Err(_) => std::ptr::null_mut(),
    }
}
