package genkai.redis.jedis.zio

import zio._
import genkai.Strategy
import genkai.effect.zio.ZioMonadError
import genkai.redis.RedisStrategy
import genkai.redis.jedis.JedisRateLimiter
import redis.clients.jedis.JedisPool
import zio.blocking.{Blocking, blocking}

class JedisZioRateLimiter private (
  pool: JedisPool,
  strategy: RedisStrategy,
  closeClient: Boolean,
  acquireSha: String,
  permissionsSha: String,
  monad: ZioMonadError
) extends JedisRateLimiter[Task](pool, monad, strategy, closeClient, acquireSha, permissionsSha) {}

object JedisZioRateLimiter {
  def useClient(
    pool: JedisPool,
    strategy: Strategy
  ): ZIO[Blocking, Throwable, JedisZioRateLimiter] = for {
    blocker <- ZIO.service[Blocking.Service]
    monad = new ZioMonadError(blocker)
    redisStrategy = RedisStrategy(strategy)
    sha <- monad.eval(pool.getResource).flatMap { client =>
      monad.guarantee {
        monad.eval(
          client.scriptLoad(redisStrategy.acquireLuaScript),
          client.scriptLoad(redisStrategy.permissionsLuaScript)
        )
      }(monad.eval(client.close()))
    }
  } yield new JedisZioRateLimiter(
    pool = pool,
    strategy = redisStrategy,
    closeClient = false,
    acquireSha = sha._1,
    permissionsSha = sha._2,
    monad = monad
  )

  def layerUsingClient(
    pool: JedisPool,
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
        sha <- monad.eval(pool.getResource).flatMap { client =>
          monad.guarantee {
            monad.eval(
              client.scriptLoad(redisStrategy.acquireLuaScript),
              client.scriptLoad(redisStrategy.permissionsLuaScript)
            )
          }(monad.eval(client.close()))
        }
      } yield new JedisZioRateLimiter(
        pool = pool,
        strategy = redisStrategy,
        closeClient = true,
        acquireSha = sha._1,
        permissionsSha = sha._2,
        monad = monad
      )
    } { limiter =>
      blocking(limiter.close().ignore)
    }

  def layerFromManaged(
    host: String,
    port: Int,
    strategy: Strategy
  ): ZLayer[Blocking, Throwable, Has[JedisZioRateLimiter]] =
    ZLayer.fromManaged(managed(host, port, strategy))
}
