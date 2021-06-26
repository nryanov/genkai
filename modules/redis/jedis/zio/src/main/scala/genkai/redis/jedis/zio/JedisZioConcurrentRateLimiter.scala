package genkai.redis.jedis.zio

import zio._
import genkai.ConcurrentStrategy
import genkai.effect.zio.ZioMonadError
import genkai.redis.RedisConcurrentStrategy
import genkai.redis.jedis.JedisConcurrentRateLimiter
import redis.clients.jedis.util.Pool
import redis.clients.jedis.{Jedis, JedisPool}
import zio.blocking.{Blocking, blocking}

class JedisZioConcurrentRateLimiter private (
  pool: Pool[Jedis],
  strategy: RedisConcurrentStrategy,
  closeClient: Boolean,
  acquireSha: String,
  releaseSha: String,
  permissionsSha: String,
  monad: ZioMonadError
) extends JedisConcurrentRateLimiter[Task](
      pool,
      monad = monad,
      strategy = strategy,
      closeClient = closeClient,
      acquireSha = acquireSha,
      releaseSha = releaseSha,
      permissionsSha = permissionsSha
    ) {}

object JedisZioConcurrentRateLimiter {
  def useClient(
    pool: Pool[Jedis],
    strategy: ConcurrentStrategy
  ): ZIO[Blocking, Throwable, JedisZioConcurrentRateLimiter] = for {
    blocker <- ZIO.service[Blocking.Service]
    monad = new ZioMonadError(blocker)
    redisStrategy = RedisConcurrentStrategy(strategy)
    sha <- monad.eval(pool.getResource).flatMap { client =>
      monad.guarantee {
        monad.eval {
          (
            client.scriptLoad(redisStrategy.acquireLuaScript),
            client.scriptLoad(redisStrategy.releaseLuaScript),
            client.scriptLoad(redisStrategy.permissionsLuaScript)
          )
        }
      }(monad.eval(client.close()))
    }
  } yield new JedisZioConcurrentRateLimiter(
    pool = pool,
    strategy = redisStrategy,
    closeClient = false,
    acquireSha = sha._1,
    releaseSha = sha._2,
    permissionsSha = sha._3,
    monad = monad
  )

  def layerUsingClient(
    pool: Pool[Jedis],
    strategy: ConcurrentStrategy
  ): ZLayer[Blocking, Throwable, Has[JedisZioConcurrentRateLimiter]] =
    useClient(pool, strategy).toLayer

  def managed(
    host: String,
    port: Int,
    strategy: ConcurrentStrategy
  ): ZManaged[Blocking, Throwable, JedisZioConcurrentRateLimiter] =
    ZManaged.make {
      for {
        blocker <- ZIO.service[Blocking.Service]
        monad = new ZioMonadError(blocker)
        redisStrategy = RedisConcurrentStrategy(strategy)
        pool <- monad.eval(new JedisPool(host, port))
        sha <- monad.eval(pool.getResource).flatMap { client =>
          monad.guarantee {
            monad.eval {
              (
                client.scriptLoad(redisStrategy.acquireLuaScript),
                client.scriptLoad(redisStrategy.releaseLuaScript),
                client.scriptLoad(redisStrategy.permissionsLuaScript)
              )
            }
          }(monad.eval(client.close()))
        }
      } yield new JedisZioConcurrentRateLimiter(
        pool = pool,
        strategy = redisStrategy,
        closeClient = true,
        acquireSha = sha._1,
        releaseSha = sha._2,
        permissionsSha = sha._3,
        monad = monad
      )
    } { limiter =>
      blocking(limiter.close().orDie)
    }

  def layerFromManaged(
    host: String,
    port: Int,
    strategy: ConcurrentStrategy
  ): ZLayer[Blocking, Throwable, Has[JedisZioConcurrentRateLimiter]] =
    ZLayer.fromManaged(managed(host, port, strategy))
}
