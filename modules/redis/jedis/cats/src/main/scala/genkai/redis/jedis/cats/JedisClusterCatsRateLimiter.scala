package genkai.redis.jedis.cats

import cats.effect.{Blocker, ContextShift, Sync}
import genkai.Strategy
import genkai.effect.cats.CatsBlockingMonadError
import genkai.redis.RedisStrategy
import genkai.monad.syntax._
import genkai.redis.jedis.JedisClusterRateLimiter
import redis.clients.jedis.JedisCluster

class JedisClusterCatsRateLimiter[F[_]: Sync: ContextShift] private (
  cluster: JedisCluster,
  strategy: RedisStrategy,
  closeClient: Boolean,
  acquireSha: String,
  permissionsSha: String,
  monad: CatsBlockingMonadError[F]
) extends JedisClusterRateLimiter[F](
      cluster,
      monad,
      strategy,
      closeClient,
      acquireSha,
      permissionsSha
    ) {}

object JedisClusterCatsRateLimiter {
  def useClient[F[_]: Sync: ContextShift](
    cluster: JedisCluster,
    strategy: Strategy,
    blocker: Blocker
  ): F[JedisClusterCatsRateLimiter[F]] = {
    implicit val monad: CatsBlockingMonadError[F] = new CatsBlockingMonadError[F](blocker)

    val redisStrategy = RedisStrategy(strategy)

    monad.eval {
      (
        cluster.scriptLoad(redisStrategy.acquireLuaScript, "script"),
        cluster.scriptLoad(redisStrategy.permissionsLuaScript, "script")
      )
    }.map { case (acquireSha, permissionsSha) =>
      new JedisClusterCatsRateLimiter(
        cluster = cluster,
        strategy = redisStrategy,
        closeClient = false,
        acquireSha = acquireSha,
        permissionsSha = permissionsSha,
        monad = monad
      )
    }
  }
}
