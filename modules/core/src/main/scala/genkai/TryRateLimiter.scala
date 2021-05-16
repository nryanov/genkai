package genkai

import java.time.Instant

import genkai.monad.{MonadError, TryMonadError}

import scala.util.Try

final class TryRateLimiter(
  rateLimiter: RateLimiter[Identity]
) extends RateLimiter[Try] {
  override private[genkai] def permissions[A: Key](key: A, instant: Instant): Try[Long] =
    monadError.eval(rateLimiter.permissions(key, instant))

  override def reset[A: Key](key: A): Try[Unit] = monadError.eval(rateLimiter.reset(key))

  override private[genkai] def acquire[A: Key](key: A, instant: Instant, cost: Long): Try[Boolean] =
    monadError.eval(rateLimiter.acquire(key, instant, cost))

  override def close(): Try[Unit] = monadError.eval(rateLimiter.close())

  override protected def monadError: MonadError[Try] = TryMonadError
}
