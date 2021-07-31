package genkai

import java.time.Instant

import genkai.monad.MonadError

/**
 * @tparam F - effect type
 */
trait ConcurrentRateLimiter[F[_]] {

  final def use[A: Key, B](key: A)(f: => F[B]): F[Either[ConcurrentLimitExhausted[A], B]] =
    use(key, Instant.now())(f)

  private[genkai] def use[A: Key, B](key: A, instant: Instant)(
    f: => F[B]
  ): F[Either[ConcurrentLimitExhausted[A], B]]

  def reset[A: Key](key: A): F[Unit]

  final def acquire[A: Key](key: A): F[Boolean] =
    acquire(key, Instant.now())

  private[genkai] def acquire[A: Key](
    key: A,
    instant: Instant
  ): F[Boolean]

  final def release[A: Key](key: A): F[Boolean] =
    release(key, Instant.now())

  private[genkai] def release[A: Key](
    key: A,
    instant: Instant
  ): F[Boolean]

  final def permissions[A: Key](key: A): F[Long] = permissions(key, Instant.now())

  private[genkai] def permissions[A: Key](key: A, instant: Instant): F[Long]

  def close(): F[Unit]

  def monadError: MonadError[F]
}
