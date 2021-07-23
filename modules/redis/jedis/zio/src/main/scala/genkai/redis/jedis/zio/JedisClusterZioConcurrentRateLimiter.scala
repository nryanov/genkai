package genkai.redis.jedis.zio

import zio._
import genkai.ConcurrentStrategy
import genkai.effect.zio.ZioBlockingMonadError
import genkai.redis.RedisConcurrentStrategy
import genkai.redis.jedis.JedisClusterConcurrentRateLimiter
import redis.clients.jedis.JedisCluster
import zio.blocking.Blocking

class JedisClusterZioConcurrentRateLimiter private (
  cluster: JedisCluster,
  strategy: RedisConcurrentStrategy,
  closeClient: Boolean,
  acquireSha: String,
  releaseSha: String,
  permissionsSha: String,
  monad: ZioBlockingMonadError
) extends JedisClusterConcurrentRateLimiter[Task](
      cluster = cluster,
      monad = monad,
      strategy = strategy,
      closeClient = closeClient,
      acquireSha = acquireSha,
      releaseSha = releaseSha,
      permissionsSha = permissionsSha
    )

object JedisClusterZioConcurrentRateLimiter {
  def useClient(
    cluster: JedisCluster,
    strategy: ConcurrentStrategy
  ): ZIO[Blocking, Throwable, JedisClusterZioConcurrentRateLimiter] = for {
    blocker <- ZIO.service[Blocking.Service]
    monad = new ZioBlockingMonadError(blocker)
    redisStrategy = RedisConcurrentStrategy(strategy)
    sha <- monad.eval {
      (
        cluster.scriptLoad(redisStrategy.acquireLuaScript, "Script"),
        cluster.scriptLoad(redisStrategy.releaseLuaScript, "Script"),
        cluster.scriptLoad(redisStrategy.permissionsLuaScript, "Script")
      )
    }
  } yield new JedisClusterZioConcurrentRateLimiter(
    cluster = cluster,
    strategy = redisStrategy,
    closeClient = false,
    acquireSha = sha._1,
    releaseSha = sha._2,
    permissionsSha = sha._3,
    monad = monad
  )

  def layerUsingClient(
    cluster: JedisCluster,
    strategy: ConcurrentStrategy
  ): ZLayer[Blocking, Throwable, Has[JedisClusterZioConcurrentRateLimiter]] =
    useClient(cluster, strategy).toLayer
}
