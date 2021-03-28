package genkai.redis.lettuce

import java.time.Instant

import genkai.monad.syntax._
import genkai.{Key, RateLimiter}
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
) extends RateLimiter[F] {
  private val asyncCommands = connection.async()

  override def permissions[A: Key](key: A): F[Long] = {
    val now = Instant.now()
    monad
      .async[Long] { cb =>
        val cf = asyncCommands
          .evalsha[Long](
            permissionsSha,
            ScriptOutputType.INTEGER,
            Array(strategy.key(key, now)),
            strategy.permissionsArgs(now): _*
          )
          .whenComplete { (result: Long, err: Throwable) =>
            if (err != null) cb(Left(err))
            else cb(Right(result))
          }

//        () => cf.toCompletableFuture.cancel(true)
      }
      .map(tokens => strategy.toPermissions(tokens))
  }

  override def reset[A: Key](key: A): F[Unit] = {
    val now = Instant.now()
    monad.async[Unit] { cb =>
      val cf = asyncCommands.unlink(strategy.key(key, now)).whenComplete { (_, err: Throwable) =>
        if (err != null) cb(Left(err))
        else cb(Right(()))
      }

//      () => cf.toCompletableFuture.cancel(true)
    }
  }

  override def acquire[A: Key](key: A, instant: Instant, cost: Long): F[Boolean] = monad
    .async[Long] { cb =>
      val cf = asyncCommands
        .evalsha[Long](
          acquireSha,
          ScriptOutputType.INTEGER,
          Array(strategy.key(key, instant)),
          strategy.acquireArgs(instant, cost): _*
        )
        .whenComplete { (result: Long, err: Throwable) =>
          if (err != null) cb(Left(err))
          else cb(Right(result))
        }

//      () => cf.toCompletableFuture.cancel(true)
    }
    .map(tokens => strategy.isAllowed(tokens))

  override def close(): F[Unit] = monad.ifA(monad.pure(closeClient))(
    monad.async[Unit] { cb =>
      val cf = connection.closeAsync().thenCompose(_ => client.shutdownAsync()).whenComplete {
        (_: Void, err: Throwable) =>
          if (err != null) cb(Left(err))
          else cb(Right(()))
      }

//      () => cf.cancel(true)
    },
    monad.async[Unit] { cb =>
      val cf = connection.closeAsync().whenComplete { (_: Void, err: Throwable) =>
        if (err != null) cb(Left(err))
        else cb(Right(()))
      }

//      () => cf.cancel(true)
    }
  )

  override protected def monadError: MonadError[F] = monad
}
