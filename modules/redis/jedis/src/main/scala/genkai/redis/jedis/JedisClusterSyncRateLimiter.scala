package genkai.redis.jedis

import genkai.{Id, Strategy}
import genkai.monad.IdMonadError
import genkai.redis.RedisStrategy
import redis.clients.jedis.JedisCluster

class JedisClusterSyncRateLimiter private (
  cluster: JedisCluster,
  strategy: RedisStrategy,
  closeClient: Boolean,
  acquireSha: String,
  permissionsSha: String
) extends JedisClusterRateLimiter[Id](
      cluster,
      IdMonadError,
      strategy,
      closeClient,
      acquireSha,
      permissionsSha
    )

object JedisClusterSyncRateLimiter {
  def apply(
    cluster: JedisCluster,
    strategy: Strategy
  ): JedisClusterSyncRateLimiter = {
    implicit val monad = IdMonadError
    val redisStrategy = RedisStrategy(strategy)

    val (acquireSha, permissionsSha) = monad.eval {
      (
        cluster.scriptLoad(redisStrategy.acquireLuaScript, "script"),
        cluster.scriptLoad(redisStrategy.permissionsLuaScript, "script")
      )
    }
    new JedisClusterSyncRateLimiter(
      cluster = cluster,
      strategy = redisStrategy,
      closeClient = false,
      acquireSha = acquireSha,
      permissionsSha = permissionsSha
    )
  }
}
