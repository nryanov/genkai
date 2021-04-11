package genkai.redis.jedis

import java.time.Instant

import genkai.monad.syntax._
import genkai.monad.MonadError
import genkai.redis.RedisStrategy
import genkai.{Key, Logging, RateLimiter}
import redis.clients.jedis.{Jedis, JedisPool}

abstract class JedisRateLimiter[F[_]](
  pool: JedisPool,
  implicit val monad: MonadError[F],
  strategy: RedisStrategy,
  closeClient: Boolean,
  acquireSha: String,
  permissionsSha: String
) extends RateLimiter[F]
    with Logging[F] {
  override def permissions[A: Key](key: A): F[Long] = {
    val now = Instant.now()
    useClient { client =>
      val args = strategy.key(key, now) :: strategy.permissionsArgs(now)

      for {
        _ <- debug(s"Permissions request: $args")
        tokens <- monad.eval(client.evalsha(permissionsSha, 1, args: _*))
      } yield strategy.toPermissions(tokens.toString.toLong)
    }
  }

  override def reset[A: Key](key: A): F[Unit] = {
    val now = Instant.now()
    val keyStr = strategy.key(key, now)
    useClient(client =>
      debug(s"Reset limits for: $keyStr").flatMap(_ => monad.eval(client.unlink(keyStr)))
    )
  }

  override def acquire[A: Key](key: A, instant: Instant, cost: Long): F[Boolean] =
    useClient { client =>
      val args = strategy.key(key, instant) :: strategy.acquireArgs(instant, cost)

      for {
        _ <- debug(s"Acquire request: $args")
        tokens <- monad.eval(client.evalsha(acquireSha, 1, args: _*))
      } yield strategy.isAllowed(tokens.toString.toLong)
    }

  override def close(): F[Unit] =
    monad.whenA(closeClient)(monad.eval(pool.close()))

  override protected def monadError: MonadError[F] = monad

  private def useClient[A](fa: Jedis => F[A]): F[A] =
    monad.eval(pool.getResource).flatMap { client =>
      monad.guarantee(fa(client))(monad.eval(client.close()))
    }
}
