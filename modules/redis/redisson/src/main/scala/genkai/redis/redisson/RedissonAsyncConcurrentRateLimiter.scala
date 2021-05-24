package genkai.redis.redisson

import java.time.Instant

import genkai.monad.syntax._
import genkai.{ConcurrentLimitExhausted, ConcurrentRateLimiter, Key, Logging}
import genkai.redis.RedisConcurrentStrategy
import genkai.monad.{MonadAsyncError, MonadError}
import org.redisson.api.{RFuture, RScript, RedissonClient}
import org.redisson.client.codec.StringCodec

import scala.collection.JavaConverters._

abstract class RedissonAsyncConcurrentRateLimiter[F[_]](
  client: RedissonClient,
  implicit val monad: MonadAsyncError[F],
  strategy: RedisConcurrentStrategy,
  closeClient: Boolean,
  acquireSha: String,
  releaseSha: String,
  permissionsSha: String
) extends ConcurrentRateLimiter[F]
    with Logging[F] {
  /* to avoid unnecessary memory allocations */
  private val scriptCommand: RScript = client.getScript(new StringCodec)

  override private[genkai] def permissions[A: Key](key: A, instant: Instant): F[Long] = {
    val keyStr = strategy.keys(key, instant)
    val args = strategy.permissionsArgs(instant)

    debug(s"Permissions request ($keyStr): $args") *>
      monad
        .cancelable[Long] { cb =>
          val cf = evalShaAsync(
            permissionsSha,
            new java.util.LinkedList[Object](keyStr.asJava),
            args
          )

          cf.onComplete { (res: Long, err: Throwable) =>
            if (err != null) cb(Left(err))
            else cb(Right(res))
          }

          () => monad.eval(cf.cancel(true))
        }
        .map(tokens => strategy.toPermissions(tokens))
  }

  override def reset[A: Key](key: A): F[Unit] = {
    val now = Instant.now()
    val keyStr = strategy.keys(key, now)

    debug(s"Reset limits for: $keyStr") *>
      monad
        .cancelable[Unit] { cb =>
          val cf = client.getKeys.unlinkAsync(keyStr: _*)

          cf.onComplete { (_, err: Throwable) =>
            if (err != null) cb(Left(err))
            else cb(Right(()))
          }

          () => monad.eval(cf.cancel(true))
        }
        .void
  }

  override private[genkai] def use[A: Key, B](key: A, instant: Instant)(
    f: => F[B]
  ): F[Either[ConcurrentLimitExhausted[A], B]] =
    monad.ifM(acquire(key, instant))(
      ifTrue = monad.guarantee(f)(release(key, instant).void).map(r => Right(r)),
      ifFalse = monad.pure(Left(ConcurrentLimitExhausted(key)))
    )

  override private[genkai] def release[A: Key](key: A, instant: Instant): F[Boolean] = {
    val keyStr = strategy.keys(key, instant)
    val args = strategy.releaseArgs(instant)

    debug(s"Release request ($keyStr): $args") *>
      monad
        .cancelable[Long] { cb =>
          val cf = evalShaAsync(
            releaseSha,
            new java.util.LinkedList[Object](keyStr.asJava),
            args
          )

          cf.onComplete { (res: Long, err: Throwable) =>
            if (err != null) cb(Left(err))
            else cb(Right(res))
          }

          () => monad.eval(cf.cancel(true))
        }
        .map(tokens => strategy.isReleased(tokens))
  }

  override private[genkai] def acquire[A: Key](key: A, instant: Instant): F[Boolean] = {
    val keyStr = strategy.keys(key, instant)
    val args = strategy.acquireArgs(instant)

    debug(s"Acquire request ($keyStr): $args") *>
      monad
        .cancelable[Long] { cb =>
          val cf = evalShaAsync(
            acquireSha,
            new java.util.LinkedList[Object](keyStr.asJava),
            args
          )

          cf.onComplete { (res: Long, err: Throwable) =>
            if (err != null) cb(Left(err))
            else cb(Right(res))
          }

          () => monad.eval(cf.cancel(true))
        }
        .map(tokens => strategy.isAllowed(tokens))
  }

  override def close(): F[Unit] = monad.ifM(monad.pure(closeClient))(
    monad.eval(client.shutdown()),
    monad.unit
  )

  override def monadError: MonadError[F] = monad

  private def evalShaAsync(
    sha: String,
    keys: java.util.List[Object],
    args: Seq[String]
  ): RFuture[Long] =
    scriptCommand.evalShaAsync[Long](
      RScript.Mode.READ_WRITE,
      sha,
      RScript.ReturnType.INTEGER,
      keys,
      args: _*
    )
}
