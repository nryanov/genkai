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
   * @param key - ~ object id
   * @param instant - request time
   * @tparam A - key type with implicit [[genkai.Key]] type class instance
   * @return - unused permissions
   */
  def permissions[A: Key](key: A, instant: Instant): F[Long]

  /**
   * @param key - ~ object id
   * @tparam A - key type with implicit [[genkai.Key]] type class instance
   * @return - unit if successfully reset tokens
   */
  def reset[A: Key](key: A): F[Unit]

  /**
   * Try to acquire token. Returns immediately.
   * @param key - ~ object id
   * @param instant - request time
   * @param cost - request cost
   * @tparam A - key type with implicit [[genkai.Key]] type class instance
   * @return - true if token was acquired, false - otherwise
   */
  def acquire[A: Key](key: A, instant: Instant, cost: Long): F[Boolean]

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
   * @param instant - request time
   * @tparam A - key type with implicit [[genkai.Key]] type class instance
   * @return
   */
  final def acquire[A: Key](key: A, instant: Instant): F[Boolean] =
    acquire(key, instant, cost = 1)

  /**
   * Try to acquire token. Returns immediately.
   * @param key - ~ object id
   * @param cost - request cost
   * @tparam A - key type with implicit [[genkai.Key]] type class instance
   * @return
   */
  final def acquire[A: Key](key: A, cost: Long): F[Boolean] = acquire(key, Instant.now(), cost)

  /**
   * Close underlying resources if any
   * @return - unit if successfully closed or error wrapped in effect
   */
  def close(): F[Unit]

  protected def monadError: MonadError[F]
}
