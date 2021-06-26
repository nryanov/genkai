package genkai

import java.time.Instant
import genkai.monad.{EitherMonadError, MonadError}

class EitherConcurrentRateLimiter(concurrentRateLimiter: ConcurrentRateLimiter[Id])
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

  override def reset[A: Key](key: A): Either[Throwable, Unit] =
    monadError.eval(concurrentRateLimiter.reset(key))

  override private[genkai] def acquire[A: Key](
    key: A,
    instant: Instant
  ): Either[Throwable, Boolean] =
    monadError.eval(concurrentRateLimiter.acquire(key, instant))

  override private[genkai] def release[A: Key](
    key: A,
    instant: Instant
  ): Either[Throwable, Boolean] =
    monadError.eval(concurrentRateLimiter.release(key, instant))

  override private[genkai] def permissions[A: Key](
    key: A,
    instant: Instant
  ): Either[Throwable, Long] =
    monadError.eval(concurrentRateLimiter.permissions(key, instant))

  override def close(): Either[Throwable, Unit] = monadError.eval(concurrentRateLimiter.close())

  override def monadError: MonadError[Either[Throwable, *]] = EitherMonadError
}
