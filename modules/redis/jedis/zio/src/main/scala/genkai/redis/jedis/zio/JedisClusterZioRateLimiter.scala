package genkai.redis.jedis.zio

import zio._
import zio.blocking.Blocking
import genkai.Strategy
import genkai.redis.RedisStrategy
import genkai.effect.zio.ZioMonadError
import redis.clients.jedis.JedisCluster
import genkai.redis.jedis.JedisClusterRateLimiter

class JedisClusterZioRateLimiter private (
  cluster: JedisCluster,
  strategy: RedisStrategy,
  closeClient: Boolean,
  acquireSha: String,
  permissionsSha: String,
  monad: ZioMonadError
) extends JedisClusterRateLimiter[Task](
      cluster = cluster,
      monad = monad,
      strategy = strategy,
      closeClient = closeClient,
      acquireSha = acquireSha,
      permissionsSha = permissionsSha
    )

object JedisClusterZioRateLimiter {
  def useClient(
    cluster: JedisCluster,
    strategy: Strategy
  ): ZIO[Blocking, Throwable, JedisClusterZioRateLimiter] = for {
    blocker <- ZIO.service[Blocking.Service]
    monad = new ZioMonadError(blocker)
    redisStrategy = RedisStrategy(strategy)
    sha <- monad.eval {
      (
        cluster.scriptLoad(redisStrategy.acquireLuaScript, "script"),
        cluster.scriptLoad(redisStrategy.permissionsLuaScript, "script")
      )
    }
  } yield new JedisClusterZioRateLimiter(
    cluster = cluster,
    strategy = redisStrategy,
    closeClient = false,
    acquireSha = sha._1,
    permissionsSha = sha._2,
    monad = monad
  )

  def layerUsingClient(
    cluster: JedisCluster,
    strategy: Strategy
  ): ZLayer[Blocking, Throwable, Has[JedisClusterZioRateLimiter]] =
    useClient(cluster, strategy).toLayer

}
