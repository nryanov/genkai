package genkai

import java.time.Instant

import genkai.monad.{MonadError, TryMonadError}

import scala.util.{Failure, Success, Try}

final class TryConcurrentRateLimiter(concurrentRateLimiter: ConcurrentRateLimiter[Id])
    extends ConcurrentRateLimiter[Try] {
  override private[genkai] def use[A: Key, B](key: A, instant: Instant)(
    f: => Try[B]
  ): Try[Either[ConcurrentLimitExhausted[A], B]] =
    monadError.eval(concurrentRateLimiter.use(key, instant)(f)).flatMap {
      case Left(value) => Success(Left(value))
      case Right(value) =>
        value match {
          case Failure(exception) => Failure(exception)
          case Success(value)     => Success(Right(value))
        }
    }

  override private[genkai] def acquire[A: Key](
    key: A,
    instant: Instant
  ): Try[Boolean] =
    monadError.eval(concurrentRateLimiter.acquire(key, instant))

  override def reset[A: Key](key: A): Try[Unit] = monadError.eval(concurrentRateLimiter.reset(key))

  override private[genkai] def release[A: Key](
    key: A,
    instant: Instant
  ): Try[Boolean] =
    monadError.eval(concurrentRateLimiter.release(key, instant))

  override private[genkai] def permissions[A: Key](key: A, instant: Instant): Try[Long] =
    monadError.eval(concurrentRateLimiter.permissions(key, instant))

  override def close(): Try[Unit] = monadError.eval(concurrentRateLimiter.close())

  override def monadError: MonadError[Try] = TryMonadError
}
