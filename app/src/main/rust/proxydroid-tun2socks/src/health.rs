//! Aggregate runtime counters only: no hosts, packets, payloads, credentials or app identities.
use std::sync::atomic::{AtomicU64, Ordering};
use std::time::{SystemTime, UNIX_EPOCH};
static VALUES: [AtomicU64; 17] = [const { AtomicU64::new(0) }; 17];
pub fn now_ms() -> u64 { SystemTime::now().duration_since(UNIX_EPOCH).unwrap_or_default().as_millis() as u64 }
pub fn reset() { for value in &VALUES { value.store(0, Ordering::Relaxed); } heartbeat(); }
pub fn heartbeat() { VALUES[2].store(now_ms(), Ordering::Relaxed); }
pub fn bump(index: usize) { VALUES[index].fetch_add(1, Ordering::Relaxed); }
pub fn exit(code: u64) { VALUES[15].store(code, Ordering::Relaxed); }
pub fn exit_code() -> u64 { VALUES[15].load(Ordering::Relaxed) }
pub fn snapshot(running: bool, finished: bool) -> [i64; 17] {
    let mut values = std::array::from_fn(|i| VALUES[i].load(Ordering::Relaxed) as i64);
    values[0] = i64::from(running); values[1] = i64::from(finished); values
}
