package genkai.redis.lettuce

import java.time.Instant

import genkai.monad.syntax._
import genkai.{Key, Logging, RateLimiter}
import genkai.monad.{MonadAsyncError, MonadError}
import genkai.redis.RedisStrategy
import io.lettuce.core.{RedisClient, ScriptOutputType}
import io.lettuce.core.api.StatefulRedisConnection

abstract class LettuceAsyncRateLimiter[F[_]](
  client: RedisClient,
  connection: StatefulRedisConnection[String, String],
  implicit val monad: MonadAsyncError[F],
  strategy: RedisStrategy,
  closeClient: Boolean,
  acquireSha: String,
  permissionsSha: String
) extends RateLimiter[F]
    with Logging[F] {
  private val asyncCommands = connection.async()

  override def permissions[A: Key](key: A, instant: Instant): F[Long] = {
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

  override def acquire[A: Key](key: A, instant: Instant, cost: Long): F[Boolean] = {
    val keyStr = strategy.keys(key, instant)
    val args = strategy.acquireArgs(instant, cost)

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
    monad.ifA(monad.pure(closeClient))(
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

  override protected def monadError: MonadError[F] = monad
}
