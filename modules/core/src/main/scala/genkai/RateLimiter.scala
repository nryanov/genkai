package genkai

import java.time.Instant

import genkai.monad.MonadError

trait RateLimiter[F[_]] {
  def permissions[A: Key](key: A): F[Long]

  def reset[A: Key](key: A): F[Unit]

  def acquire[A: Key](key: A, instant: Instant): F[Boolean]

  final def acquire[A: Key](key: A): F[Boolean] = acquire(key, Instant.now())

  def close(): F[Unit]

  protected def monadError: MonadError[F]
}
