package genkai.redis.jedis

import java.time.Instant

import genkai.monad.syntax._
import genkai.monad.MonadError
import genkai.redis.RedisStrategy
import genkai.{Key, Logging, RateLimiter}
import redis.clients.jedis.util.Pool
import redis.clients.jedis.Jedis

abstract class JedisRateLimiter[F[_]](
  pool: Pool[Jedis],
  implicit val monad: MonadError[F],
  strategy: RedisStrategy,
  closeClient: Boolean,
  acquireSha: String,
  permissionsSha: String
) extends RateLimiter[F]
    with Logging[F] {
  override private[genkai] def permissions[A: Key](key: A, instant: Instant): F[Long] =
    useClient { client =>
      val keys = strategy.keys(key, instant)
      val args = keys ::: strategy.permissionsArgs(instant)

      for {
        _ <- debug(s"Permissions request: $args")
        tokens <- monad.eval(client.evalsha(permissionsSha, keys.size, args: _*))
      } yield strategy.toPermissions(tokens.toString.toLong)
    }

  override def reset[A: Key](key: A): F[Unit] = {
    val now = Instant.now()
    val keyStr = strategy.keys(key, now)
    useClient(client =>
      debug(s"Reset limits for: $keyStr").flatMap(_ => monad.eval(client.unlink(keyStr: _*)))
    )
  }

  override private[genkai] def acquire[A: Key](key: A, instant: Instant, cost: Long): F[Boolean] =
    useClient { client =>
      val keys = strategy.keys(key, instant)
      val args = keys ::: strategy.acquireArgs(instant, cost)

      for {
        _ <- debug(s"Acquire request: $args")
        tokens <- monad.eval(client.evalsha(acquireSha, keys.size, args: _*))
      } yield strategy.isAllowed(tokens.toString.toLong)
    }

  override def close(): F[Unit] =
    monad.whenA(closeClient)(monad.eval(pool.close()))

  override def monadError: MonadError[F] = monad

  private def useClient[A](fa: Jedis => F[A]): F[A] =
    monad.bracket(monad.eval(pool.getResource))(client => fa(client))(client =>
      monad.eval(client.close())
    )
}
