/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Class: RateLimitBucketCache
 * Description: Bounded rate-limit buckets that do not reset a live key to admit a new one (#918).
 */

package org.ezkey.auth.config;

import io.github.bucket4j.Bucket;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * In-memory token buckets that refuse to evict a live key in order to store a new one.
 *
 * <p>A size-capped cache that drops the least-recent entry (Caffeine {@code maximumSize}) lets an
 * attacker mint a fresh full budget by rotating keys until the exhausted one is gone. This cache
 * keeps every entry that was touched inside {@code idleTtl}. When the map is full, a new key shares
 * one overflow bucket instead of displacing a resident. Idle entries may be dropped, which is safe
 * once {@code idleTtl} is longer than the Bucket4j refill window: the dropped bucket would already
 * have refilled.
 *
 * @author Ezkey contributors
 * @since 2026
 */
final class RateLimitBucketCache {

  private final int maxEntries;

  private final Duration idleTtl;

  private final Bucket overflow;

  private final ConcurrentHashMap<String, TimedBucket> buckets = new ConcurrentHashMap<>();

  /**
   * Creates a cache.
   *
   * @param maxEntries maximum resident keys before new keys share {@code overflow}
   * @param idleTtl how long an untouched bucket stays resident
   * @param overflow shared bucket for keys that cannot be admitted
   */
  RateLimitBucketCache(int maxEntries, Duration idleTtl, Bucket overflow) {
    if (maxEntries < 1) {
      throw new IllegalArgumentException("maxEntries must be >= 1");
    }
    this.maxEntries = maxEntries;
    this.idleTtl = idleTtl;
    this.overflow = overflow;
  }

  /**
   * Returns the resident bucket for {@code key}, creating it when the cache can admit another
   * entry. A brand-new key at capacity receives the overflow bucket.
   *
   * @param key bucket key
   * @param factory supplier of a fresh full bucket; invoked only when the key is admitted
   * @return bucket to probe (never null)
   */
  Bucket resolve(String key, Supplier<Bucket> factory) {
    TimedBucket existing = buckets.get(key);
    if (existing != null) {
      if (!existing.idle(idleTtl)) {
        existing.touch();
        return existing.bucket;
      }
      buckets.remove(key, existing);
    }
    if (buckets.size() >= maxEntries) {
      evictIdle();
    }
    if (buckets.size() >= maxEntries) {
      return overflow;
    }
    TimedBucket created = new TimedBucket(factory.get());
    TimedBucket raced = buckets.putIfAbsent(key, created);
    if (raced != null) {
      raced.touch();
      return raced.bucket;
    }
    return created.bucket;
  }

  /**
   * Returns one token to a resident bucket. Absent keys are ignored so a refund cannot create a
   * fresh budget.
   *
   * @param key bucket key previously charged
   */
  void refundOne(String key) {
    TimedBucket existing = buckets.get(key);
    if (existing == null || existing.idle(idleTtl)) {
      return;
    }
    existing.touch();
    existing.bucket.addTokens(1);
  }

  private void evictIdle() {
    buckets.entrySet().removeIf(entry -> entry.getValue().idle(idleTtl));
  }

  private static final class TimedBucket {

    private final Bucket bucket;

    private volatile long lastAccessNanos = System.nanoTime();

    private TimedBucket(Bucket bucket) {
      this.bucket = bucket;
    }

    private void touch() {
      lastAccessNanos = System.nanoTime();
    }

    private boolean idle(Duration ttl) {
      return System.nanoTime() - lastAccessNanos > ttl.toNanos();
    }
  }
}
