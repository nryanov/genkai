package genkai

import java.time.Instant
import genkai.monad.{EitherMonadError, MonadError}

class EitherConcurrentRateLimiter(concurrentRateLimiter: ConcurrentRateLimiter[Identity])
    extends ConcurrentRateLimiter[Either[Throwable, *]] {
  type ResultRight[A, B] = Either[ConcurrentLimitExhausted[A], B]
  type Result[A, B] = Either[Throwable, ResultRight[A, B]]

  override private[genkai] def use[A: Key, B](key: A, instant: Instant)(
    f: => Either[Throwable, B]
  ): Result[A, B] =
    monadError.eval(concurrentRateLimiter.use(key, instant)(f)).flatMap {
      case Left(value) =>
        Right[Throwable, ResultRight[A, B]](Left[ConcurrentLimitExhausted[A], B](value))
      case Right(value) =>
        value match {
          case Left(value)  => Left(value)
          case Right(value) => Right(Right(value))
        }
    }

  override private[genkai] def acquire[A: Key](
    key: A,
    instant: Instant
  ): Either[Throwable, Either[ConcurrentLimitExhausted[A], Boolean]] =
    monadError.eval(concurrentRateLimiter.acquire(key, instant))

  override private[genkai] def release[A: Key](
    key: A,
    instant: Instant
  ): Either[Throwable, Either[ConcurrentLimitExhausted[A], Boolean]] =
    monadError.eval(concurrentRateLimiter.release(key, instant))

  override def permissions[A: Key](key: A): Either[Throwable, Long] =
    monadError.eval(concurrentRateLimiter.permissions(key))

  override def close(): Either[Throwable, Unit] = monadError.eval(concurrentRateLimiter.close())

  override protected def monadError: MonadError[Either[Throwable, *]] = EitherMonadError
}
