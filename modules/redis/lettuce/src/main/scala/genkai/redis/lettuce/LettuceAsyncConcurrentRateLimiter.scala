package genkai.redis.lettuce

import java.time.Instant

import genkai.monad.syntax._
import genkai.{ConcurrentLimitExhausted, ConcurrentRateLimiter, Key, Logging}
import genkai.monad.{MonadAsyncError, MonadError}
import genkai.redis.RedisConcurrentStrategy
import io.lettuce.core.{RedisClient, ScriptOutputType}
import io.lettuce.core.api.StatefulRedisConnection

abstract class LettuceAsyncConcurrentRateLimiter[F[_]](
  client: RedisClient,
  connection: StatefulRedisConnection[String, String],
  implicit val monad: MonadAsyncError[F],
  strategy: RedisConcurrentStrategy,
  closeClient: Boolean,
  acquireSha: String,
  releaseSha: String,
  permissionsSha: String
) extends ConcurrentRateLimiter[F]
    with Logging[F] {
  private val asyncCommands = connection.async()

  override private[genkai] def permissions[A: Key](key: A, instant: Instant): F[Long] = {
    val keyStr = strategy.keys(key, instant)
    val args = strategy.permissionsArgs(instant)

    debug(s"Permissions request ($keyStr): $args") *>
      monad
        .cancelable[Long] { cb =>
          val cf = asyncCommands
            .evalsha[Long](
              permissionsSha,
              ScriptOutputType.INTEGER,
              keyStr.toArray,
              args: _*
            )
            .whenComplete { (result: Long, err: Throwable) =>
              if (err != null) cb(Left(err))
              else cb(Right(result))
            }

          () => monad.eval(cf.toCompletableFuture.cancel(true))
        }
        .map(tokens => strategy.toPermissions(tokens))
  }

  override def reset[A: Key](key: A): F[Unit] = {
    val now = Instant.now()
    val keyStr = strategy.keys(key, now)
    debug(s"Reset limits for: $keyStr") *>
      monad.cancelable[Unit] { cb =>
        val cf = asyncCommands.unlink(keyStr: _*).whenComplete { (_, err: Throwable) =>
          if (err != null) cb(Left(err))
          else cb(Right(()))
        }

        () => monad.eval(cf.toCompletableFuture.cancel(true))
      }
  }

  override private[genkai] def use[A: Key, B](key: A, instant: Instant)(
    f: => F[B]
  ): F[Either[ConcurrentLimitExhausted[A], B]] = monad.ifM(acquire(key, instant))(
    ifTrue = monad.guarantee(f)(release(key, instant).void).map(r => Right(r)),
    ifFalse = monad.pure(Left(ConcurrentLimitExhausted(key)))
  )

  override private[genkai] def release[A: Key](key: A, instant: Instant): F[Boolean] = {
    val keyStr = strategy.keys(key, instant)
    val args = strategy.releaseArgs(instant)

    debug(s"Release request ($keyStr): $args") *>
      monad
        .cancelable[Long] { cb =>
          val cf = asyncCommands
            .evalsha[Long](
              releaseSha,
              ScriptOutputType.INTEGER,
              keyStr.toArray,
              args: _*
            )
            .whenComplete { (result: Long, err: Throwable) =>
              if (err != null) cb(Left(err))
              else cb(Right(result))
            }

          () => monad.eval(cf.toCompletableFuture.cancel(true))
        }
        .map(tokens => strategy.isAllowed(tokens))
  }

  override private[genkai] def acquire[A: Key](key: A, instant: Instant): F[Boolean] = {
    val keyStr = strategy.keys(key, instant)
    val args = strategy.acquireArgs(instant)

    debug(s"Acquire request ($keyStr): $args") *>
      monad
        .cancelable[Long] { cb =>
          val cf = asyncCommands
            .evalsha[Long](
              acquireSha,
              ScriptOutputType.INTEGER,
              keyStr.toArray,
              args: _*
            )
            .whenComplete { (result: Long, err: Throwable) =>
              if (err != null) cb(Left(err))
              else cb(Right(result))
            }

          () => monad.eval(cf.toCompletableFuture.cancel(true))
        }
        .map(tokens => strategy.isAllowed(tokens))
  }

  override def close(): F[Unit] =
    monad.ifM(monad.pure(closeClient))(
      monad.cancelable[Unit] { cb =>
        val cf = connection.closeAsync().thenCompose(_ => client.shutdownAsync()).whenComplete {
          (_: Void, err: Throwable) =>
            if (err != null) cb(Left(err))
            else cb(Right(()))
        }

        () => monad.eval(cf.toCompletableFuture.cancel(true))
      },
      monad.cancelable[Unit] { cb =>
        val cf = connection.closeAsync().whenComplete { (_: Void, err: Throwable) =>
          if (err != null) cb(Left(err))
          else cb(Right(()))
        }

        () => monad.eval(cf.toCompletableFuture.cancel(true))
      }
    )

  override def monadError: MonadError[F] = monad
}
