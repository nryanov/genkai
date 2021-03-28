package genkai

import java.time.Instant

import genkai.monad.{MonadError, TryMonad}

import scala.util.Try

final class TryRateLimiter(
  rateLimiter: RateLimiter[Identity]
) extends RateLimiter[Try] {
  override def permissions[A: Key](key: A): Try[Long] =
    monadError.eval(rateLimiter.permissions(key))

  override def reset[A: Key](key: A): Try[Unit] = monadError.eval(rateLimiter.reset(key))

  override def acquire[A: Key](key: A, instant: Instant, cost: Long): Try[Boolean] =
    monadError.eval(rateLimiter.acquire(key, instant, cost))

  override def close(): Try[Unit] = monadError.eval(rateLimiter.close())

  override protected def monadError: MonadError[Try] = TryMonad
}
