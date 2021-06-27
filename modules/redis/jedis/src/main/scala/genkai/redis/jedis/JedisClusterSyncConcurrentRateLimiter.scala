package genkai.redis.jedis

import redis.clients.jedis.JedisCluster
import genkai.monad.IdMonadError
import genkai.redis.RedisConcurrentStrategy
import genkai.{ConcurrentStrategy, Id}

class JedisClusterSyncConcurrentRateLimiter private (
  cluster: JedisCluster,
  strategy: RedisConcurrentStrategy,
  closeClient: Boolean,
  acquireSha: String,
  releaseSha: String,
  permissionsSha: String
) extends JedisClusterConcurrentRateLimiter[Id](
      cluster,
      IdMonadError,
      strategy,
      closeClient,
      acquireSha,
      releaseSha,
      permissionsSha
    )

object JedisClusterSyncConcurrentRateLimiter {
  def apply(
    cluster: JedisCluster,
    strategy: ConcurrentStrategy
  ): JedisClusterSyncConcurrentRateLimiter = {
    implicit val monad = IdMonadError
    val redisStrategy = RedisConcurrentStrategy(strategy)

    val (acquireSha, releaseSha, permissionsSha) = monad.eval {
      (
        cluster.scriptLoad(redisStrategy.acquireLuaScript, "script"),
        cluster.scriptLoad(redisStrategy.releaseLuaScript, "script"),
        cluster.scriptLoad(redisStrategy.permissionsLuaScript, "script")
      )
    }
    new JedisClusterSyncConcurrentRateLimiter(
      cluster = cluster,
      strategy = redisStrategy,
      closeClient = false,
      acquireSha = acquireSha,
      releaseSha = releaseSha,
      permissionsSha = permissionsSha
    )
  }
}
