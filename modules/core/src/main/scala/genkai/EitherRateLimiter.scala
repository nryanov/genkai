package genkai
import java.time.Instant

import genkai.monad.{EitherMonadError, MonadError}

final class EitherRateLimiter(rateLimiter: RateLimiter[Identity])
    extends RateLimiter[Either[Throwable, *]] {
  override private[genkai] def permissions[A: Key](
    key: A,
    instant: Instant
  ): Either[Throwable, Long] =
    monadError.eval(rateLimiter.permissions(key, instant))

  override def reset[A: Key](key: A): Either[Throwable, Unit] =
    monadError.eval(rateLimiter.reset(key))

  override private[genkai] def acquire[A: Key](
    key: A,
    instant: Instant,
    cost: Long
  ): Either[Throwable, Boolean] =
    monadError.eval(rateLimiter.acquire(key, instant, cost))

  override def close(): Either[Throwable, Unit] = monadError.eval(rateLimiter.close())

  override protected def monadError: MonadError[Either[Throwable, *]] = EitherMonadError
}
