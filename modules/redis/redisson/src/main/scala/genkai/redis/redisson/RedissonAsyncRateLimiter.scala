package genkai.redis.redisson

import java.time.Instant
import java.util.Collections

import genkai.monad.syntax._
import genkai.{Key, RateLimiter}
import genkai.redis.RedisStrategy
import genkai.monad.{MonadAsyncError, MonadError}
import org.redisson.api.{RFuture, RScript, RedissonClient}
import org.redisson.client.codec.StringCodec

abstract class RedissonAsyncRateLimiter[F[_]](
  client: RedissonClient,
  implicit val monad: MonadAsyncError[F],
  strategy: RedisStrategy,
  closeClient: Boolean,
  acquireSha: String,
  permissionsSha: String
) extends RateLimiter[F] {
  /* to avoid unnecessary memory allocations */
  private val scriptCommand: RScript = client.getScript(new StringCodec)

  override def permissions[A: Key](key: A): F[Long] = {
    val now = Instant.now()

    monad
      .async[Long] { cb =>
        val cf = evalShaAsync(
          permissionsSha,
          Collections.singletonList(strategy.key(key, now)),
          strategy.permissionsArgs(now)
        )

        cf.onComplete { (res: Long, err: Throwable) =>
          if (err != null) cb(Left(err))
          else cb(Right(res))
        }

//        () => cf.cancel(true)
      }
      .map(tokens => strategy.toPermissions(tokens))
  }

  override def reset[A: Key](key: A): F[Unit] = {
    val now = Instant.now()
    monad
      .async[Unit] { cb =>
        val cf = client.getKeys.unlinkAsync(strategy.key(key, now))

        cf.onComplete { (_, err: Throwable) =>
          if (err != null) cb(Left(err))
          else cb(Right(()))
        }

//        () => cf.cancel(true)
      }
      .void
  }

  override def acquire[A: Key](key: A, instant: Instant, cost: Long): F[Boolean] =
    monad
      .async[Long] { cb =>
        val cf = evalShaAsync(
          acquireSha,
          Collections.singletonList(strategy.key(key, instant)),
          strategy.acquireArgs(instant, cost)
        )

        cf.onComplete { (res: Long, err: Throwable) =>
          if (err != null) cb(Left(err))
          else cb(Right(res))
        }

//        () => cf.cancel(true)
      }
      .map(tokens => strategy.isAllowed(tokens))

  override def close(): F[Unit] = monad.ifA(monad.pure(closeClient))(
    monad.eval(client.shutdown()),
    monad.unit
  )

  override protected def monadError: MonadError[F] = monad

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
