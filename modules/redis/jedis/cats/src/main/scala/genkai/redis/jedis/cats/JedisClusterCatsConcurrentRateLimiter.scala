package genkai.redis.jedis.cats

import cats.effect.Sync
import genkai.ConcurrentStrategy
import genkai.monad.syntax._
import genkai.effect.cats.CatsBlockingMonadError
import genkai.redis.RedisConcurrentStrategy
import genkai.redis.jedis.JedisClusterConcurrentRateLimiter
import redis.clients.jedis.JedisCluster

class JedisClusterCatsConcurrentRateLimiter[F[_]: Sync: ContextShift] private (
  cluster: JedisCluster,
  strategy: RedisConcurrentStrategy,
  closeClient: Boolean,
  acquireSha: String,
  releaseSha: String,
  permissionsSha: String,
  monad: CatsBlockingMonadError[F]
) extends JedisClusterConcurrentRateLimiter[F](
      cluster = cluster,
      monad = monad,
      strategy = strategy,
      closeClient = closeClient,
      acquireSha = acquireSha,
      releaseSha = releaseSha,
      permissionsSha = permissionsSha
    ) {}

object JedisClusterCatsConcurrentRateLimiter {
  def useClient[F[_]: Sync: ContextShift](
    cluster: JedisCluster,
    strategy: ConcurrentStrategy): F[JedisClusterCatsConcurrentRateLimiter[F]] = {
    implicit val monad: CatsBlockingMonadError[F] = new CatsBlockingMonadError[F](blocker)

    val redisStrategy = RedisConcurrentStrategy(strategy)

    monad.eval {
      (
        cluster.scriptLoad(redisStrategy.acquireLuaScript, "script"),
        cluster.scriptLoad(redisStrategy.releaseLuaScript, "script"),
        cluster.scriptLoad(redisStrategy.permissionsLuaScript, "script")
      )
    }.map { case (acquireSha, releaseSha, permissionsSha) =>
      new JedisClusterCatsConcurrentRateLimiter(
        cluster = cluster,
        strategy = redisStrategy,
        closeClient = false,
        acquireSha = acquireSha,
        releaseSha = releaseSha,
        permissionsSha = permissionsSha,
        monad = monad
      )
    }
  }
}
