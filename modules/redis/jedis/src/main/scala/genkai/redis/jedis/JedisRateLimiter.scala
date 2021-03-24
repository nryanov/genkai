package genkai.redis.jedis

import java.time.Instant

import genkai.monad.syntax._
import genkai.monad.MonadError
import genkai.redis.RedisStrategy
import genkai.{ClientError, Key, RateLimiter}
import redis.clients.jedis.{Jedis, JedisPool}

abstract class JedisRateLimiter[F[_]](
  pool: JedisPool,
  implicit val monad: MonadError[F],
  strategy: RedisStrategy,
  closeClient: Boolean,
  acquireSha: String,
  permissionsSha: String
) extends RateLimiter[F] {
  override def permissions[A: Key](key: A): F[Long] = {
    val now = Instant.now()
    useClient { client =>
      val args = strategy.key(key, now) :: strategy.args(now)

      monad
        .eval(client.evalsha(permissionsSha, 1, args: _*))
        .map(_.toString.toLong)
        .map(strategy.toPermissions)
    }.adaptError(err => ClientError(err))
  }

  override def reset[A: Key](key: A): F[Unit] = {
    val now = Instant.now()
    useClient(client => monad.eval(client.unlink(strategy.key(key, now))))
  }

  override def acquire[A: Key](key: A, instant: Instant): F[Boolean] =
    useClient { client =>
      val args = strategy.key(key, instant) :: strategy.argsWithTtl(instant)

      monad
        .eval(client.evalsha(acquireSha, 1, args: _*))
        .map(_.toString.toLong)
        .map(strategy.isAllowed)
    }.adaptError(err => ClientError(err))

  override def close(): F[Unit] = monad.whenA(closeClient)(monad.eval(pool.close()))

  override protected def monadError: MonadError[F] = monad

  private def useClient[A](fa: Jedis => F[A]): F[A] =
    monad.eval(pool.getResource).flatMap { client =>
      monad.guarantee(fa(client))(monad.eval(client.close()))
    }
}
