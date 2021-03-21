package genkai
import genkai.monad.{EitherMonad, MonadError}

final class EitherRateLimiter(rateLimiter: RateLimiter[Identity])
    extends RateLimiter[Either[Throwable, *]] {
  override def permissions[A: Key](key: A): Either[Throwable, Long] =
    monadError.eval(rateLimiter.permissions(key))

  override def reset[A: Key](key: A): Either[Throwable, Unit] =
    monadError.eval(rateLimiter.reset(key))

  override def acquire[A: Key](key: A): Either[Throwable, Boolean] =
    monadError.eval(rateLimiter.acquire(key))

  override def close(): Either[Throwable, Unit] = monadError.eval(rateLimiter.close())

  override protected def monadError: MonadError[Either[Throwable, *]] = EitherMonad
}
