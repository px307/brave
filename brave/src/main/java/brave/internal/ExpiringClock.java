package brave.internal;

import brave.Clock;

/** gets a timestamp based on duration since the create tick. */
public final class ExpiringClock implements Clock {

  public static ExpiringClock create(Clock delegate, long expirationNanos) {
    return new ExpiringClock(delegate, expirationNanos);
  }

  final Clock delegate;
  final long expirationNanos;
  transient volatile long baseEpochMicros, baseTickNanos;
  transient volatile long nextExpirationTick;

  ExpiringClock(Clock delegate, long expirationNanos) {
    this.expirationNanos = expirationNanos;
    // for consistency, read a tick before reading time as we do the same in expiration case
    long tick = System.nanoTime();
    this.delegate = delegate;
    this.baseEpochMicros = delegate.currentTimeMicroseconds();
    this.baseTickNanos = tick;
    this.nextExpirationTick = tick + expirationNanos;
  }

  public Clock fix() {
    return new TickClock(currentTimeMicroseconds(), baseTickNanos);
  }

  /** Returns epoch microseconds calculated from */
  @Override public long currentTimeMicroseconds() {
    long thisExpirationTick = nextExpirationTick;
    long thisTickNanos = System.nanoTime();
    if (thisTickNanos - thisExpirationTick >= 0) {
      synchronized (this) {
        // If we won the race, update state. Otherwise, fall through and reuse our tick
        if (thisExpirationTick == nextExpirationTick) {
          long newEpochMicros = baseEpochMicros = delegate.currentTimeMicroseconds();
          baseTickNanos = thisTickNanos;
          nextExpirationTick = thisTickNanos + expirationNanos;
          return newEpochMicros; // reuse the base time on expiry
        }
      }
    }
    return ((thisTickNanos - baseTickNanos - expirationNanos) / 1000) + baseEpochMicros;
  }

  @Override public String toString() {
    return "ExpiringClock{"
        + "baseEpochMicros=" + baseEpochMicros + ", "
        + "baseTickNanos=" + baseTickNanos + ", "
        + "nextExpirationTick=" + nextExpirationTick
        + "}";
  }
}