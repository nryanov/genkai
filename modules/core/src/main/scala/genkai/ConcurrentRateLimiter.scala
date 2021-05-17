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

  final def acquire[A: Key](key: A): F[Either[ConcurrentLimitExhausted[A], Boolean]] =
    acquire(key, Instant.now())

  private[genkai] def acquire[A: Key](
    key: A,
    instant: Instant
  ): F[Either[ConcurrentLimitExhausted[A], Boolean]]

  final def release[A: Key](key: A): F[Either[ConcurrentLimitExhausted[A], Boolean]] =
    release(key, Instant.now())

  private[genkai] def release[A: Key](
    key: A,
    instant: Instant
  ): F[Either[ConcurrentLimitExhausted[A], Boolean]]

  def permissions[A: Key](key: A): F[Long]

  def close(): F[Unit]

  protected def monadError: MonadError[F]
}
