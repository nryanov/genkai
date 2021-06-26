package genkai.redis.jedis.zio

import zio._
import genkai.Strategy
import genkai.effect.zio.ZioMonadError
import genkai.redis.RedisStrategy
import genkai.redis.jedis.JedisRateLimiter
import redis.clients.jedis.util.Pool
import redis.clients.jedis.{Jedis, JedisPool}
import zio.blocking.{Blocking, blocking}

class JedisZioRateLimiter private (
  pool: Pool[Jedis],
  strategy: RedisStrategy,
  closeClient: Boolean,
  acquireSha: String,
  permissionsSha: String,
  monad: ZioMonadError
) extends JedisRateLimiter[Task](pool, monad, strategy, closeClient, acquireSha, permissionsSha) {}

object JedisZioRateLimiter {
  def useClient(
    pool: Pool[Jedis],
    strategy: Strategy
  ): ZIO[Blocking, Throwable, JedisZioRateLimiter] = for {
    blocker <- ZIO.service[Blocking.Service]
    monad = new ZioMonadError(blocker)
    redisStrategy = RedisStrategy(strategy)
    sha <- monad.bracket(monad.eval(pool.getResource))(client =>
      monad.eval(
        (
          client.scriptLoad(redisStrategy.acquireLuaScript),
          client.scriptLoad(redisStrategy.permissionsLuaScript)
        )
      )
    )(resource => monad.eval(resource.close()))
  } yield new JedisZioRateLimiter(
    pool = pool,
    strategy = redisStrategy,
    closeClient = false,
    acquireSha = sha._1,
    permissionsSha = sha._2,
    monad = monad
  )

  def layerUsingClient(
    pool: Pool[Jedis],
    strategy: Strategy
  ): ZLayer[Blocking, Throwable, Has[JedisZioRateLimiter]] =
    useClient(pool, strategy).toLayer

  def managed(
    host: String,
    port: Int,
    strategy: Strategy
  ): ZManaged[Blocking, Throwable, JedisZioRateLimiter] =
    ZManaged.make {
      for {
        blocker <- ZIO.service[Blocking.Service]
        monad = new ZioMonadError(blocker)
        redisStrategy = RedisStrategy(strategy)
        pool <- monad.eval(new JedisPool(host, port))
        sha <- monad.bracket(monad.eval(pool.getResource))(client =>
          monad.eval(
            (
              client.scriptLoad(redisStrategy.acquireLuaScript),
              client.scriptLoad(redisStrategy.permissionsLuaScript)
            )
          )
        )(resource => monad.eval(resource.close()))
      } yield new JedisZioRateLimiter(
        pool = pool,
        strategy = redisStrategy,
        closeClient = true,
        acquireSha = sha._1,
        permissionsSha = sha._2,
        monad = monad
      )
    } { limiter =>
      blocking(limiter.close().orDie)
    }

  def layerFromManaged(
    host: String,
    port: Int,
    strategy: Strategy
  ): ZLayer[Blocking, Throwable, Has[JedisZioRateLimiter]] =
    ZLayer.fromManaged(managed(host, port, strategy))
}
