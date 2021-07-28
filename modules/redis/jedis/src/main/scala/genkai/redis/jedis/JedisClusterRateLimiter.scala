package genkai.redis.jedis

import java.time.Instant

import genkai.monad.syntax._
import genkai.monad.MonadError
import genkai.redis.RedisStrategy
import genkai.{Key, RateLimiter}
import redis.clients.jedis.JedisCluster

abstract class JedisClusterRateLimiter[F[_]](
  cluster: JedisCluster,
  implicit val monad: MonadError[F],
  strategy: RedisStrategy,
  closeClient: Boolean,
  acquireSha: String,
  permissionsSha: String
) extends RateLimiter[F] {
  override private[genkai] def permissions[A: Key](key: A, instant: Instant): F[Long] = {
    val keys = strategy.keys(key, instant)
    val args = keys ::: strategy.permissionsArgs(instant)

    for {
      tokens <- monad.eval(cluster.evalsha(permissionsSha, keys.size, args: _*))
    } yield strategy.toPermissions(tokens.toString.toLong)
  }

  override def reset[A: Key](key: A): F[Unit] = {
    val now = Instant.now()
    val keyStr = strategy.keys(key, now)

    monad.eval(cluster.unlink(keyStr: _*))
  }

  override private[genkai] def acquireS[A: Key](
    key: A,
    instant: Instant,
    cost: Long
  ): F[RateLimiter.State] = {
    val keys = strategy.keys(key, instant)
    val args = keys ::: strategy.acquireArgs(instant, cost)

    for {
      tokens <- monad.eval(cluster.evalsha(acquireSha, keys.size, args: _*))
    } yield strategy.toState(tokens, instant, Key[A].convert(key))
  }

  override def close(): F[Unit] =
    monad.whenA(closeClient)(monad.eval(cluster.close()))

  override def monadError: MonadError[F] = monad
}
