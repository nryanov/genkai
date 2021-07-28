package genkai

import java.time.Instant

import genkai.monad.MonadError

/**
 * @tparam F - effect type
 */
trait RateLimiter[F[_]] {

  /**
   * @param key - ~ object id
   * @tparam A - key type with implicit [[genkai.Key]] type class instance
   * @return - unused permissions
   */
  final def permissions[A: Key](key: A): F[Long] = permissions(key, Instant.now())

  /**
   * For internal usage only (ex. tests)
   * @param key - ~ object id
   * @param instant - request time
   * @tparam A - key type with implicit [[genkai.Key]] type class instance
   * @return - unused permissions
   */
  private[genkai] def permissions[A: Key](key: A, instant: Instant): F[Long]

  /**
   * @param key - ~ object id
   * @tparam A - key type with implicit [[genkai.Key]] type class instance
   * @return - unit if successfully reset tokens
   */
  def reset[A: Key](key: A): F[Unit]

  /**
   * Try to acquire token. Returns immediately.
   * For internal usage only (ex. tests)
   * @param key - ~ object id
   * @param instant - request time
   * @param cost - request cost
   * @tparam A - key type with implicit [[genkai.Key]] type class instance
   * @return - true if token was acquired, false - otherwise
   */
  private[genkai] def acquire[A: Key](key: A, instant: Instant, cost: Long): F[Boolean]

  /**
   * Try to acquire token. Returns immediately.
   * For internal usage only (ex. tests)
   * @param key - ~ object id
   * @param instant - request time
   * @param cost - request cost
   * @tparam A - key type with implicit [[genkai.Key]] type class instance
   * @return - true if token was acquired, false - otherwise
   */
  private[genkai] def acquireS[A: Key](key: A, instant: Instant, cost: Long): F[RateLimiter.State] =
    // fixme: temporary solution
    monadError.raiseError(new RuntimeException("not implemented"))

  /**
   * Try to acquire token. Returns immediately.
   * @param key - ~ object id
   * @tparam A - key type with implicit [[genkai.Key]] type class instance
   * @return
   */
  final def acquire[A: Key](key: A): F[Boolean] = acquire(key, Instant.now(), cost = 1)

  /**
   * Try to acquire token. Returns immediately.
   * @param key - ~ object id
   * @tparam A - key type with implicit [[genkai.Key]] type class instance
   * @return
   */
  final def acquireS[A: Key](key: A): F[RateLimiter.State] = acquireS(key, Instant.now(), cost = 1)

  /**
   * Try to acquire token. Returns immediately.
   * For internal usage only (ex. tests)
   * @param key - ~ object id
   * @param instant - request time
   * @tparam A - key type with implicit [[genkai.Key]] type class instance
   * @return
   */
  private[genkai] def acquire[A: Key](key: A, instant: Instant): F[Boolean] =
    acquire(key, instant, cost = 1)

  /**
   * Try to acquire token. Returns immediately.
   * For internal usage only (ex. tests)
   * @param key - ~ object id
   * @param instant - request time
   * @tparam A - key type with implicit [[genkai.Key]] type class instance
   * @return
   */
  private[genkai] def acquireS[A: Key](key: A, instant: Instant): F[RateLimiter.State] =
    acquireS(key, instant, cost = 1)

  /**
   * Try to acquire token. Returns immediately.
   * @param key - ~ object id
   * @param cost - request cost
   * @tparam A - key type with implicit [[genkai.Key]] type class instance
   * @return
   */
  final def acquire[A: Key](key: A, cost: Long): F[Boolean] = acquire(key, Instant.now(), cost)

  /**
   * Try to acquire token. Returns immediately.
   * @param key - ~ object id
   * @param cost - request cost
   * @tparam A - key type with implicit [[genkai.Key]] type class instance
   * @return
   */
  final def acquireS[A: Key](key: A, cost: Long): F[RateLimiter.State] =
    acquireS(key, Instant.now(), cost)

  /**
   * Close underlying resources if any
   * @return - unit if successfully closed or error wrapped in effect
   */
  def close(): F[Unit]

  def monadError: MonadError[F]
}

object RateLimiter {

  /**
   * @param limit - max available tokens
   * @param remaining - remaining tokens
   * @param isAllowed - is current request allowed
   * @param reset - epoch seconds at which rate limit resets
   * @param resetAfter - total time (in seconds) of when the current rate limit bucket will reset
   * @param key - bucket
   */
  final case class State(
    limit: Long,
    remaining: Long,
    isAllowed: Boolean,
    reset: Long,
    resetAfter: Long,
    key: String
  )
}
