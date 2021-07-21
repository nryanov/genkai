package genkai.redis.redisson.zio

import genkai.ConcurrentStrategy
import genkai.effect.zio.ZioBlockingMonadError
import genkai.redis.RedisConcurrentStrategy
import genkai.redis.redisson.RedissonConcurrentRateLimiter
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import zio._
import zio.blocking.{Blocking, blocking}

class RedissonZioConcurrentRateLimiter private (
  client: RedissonClient,
  monad: ZioBlockingMonadError,
  strategy: RedisConcurrentStrategy,
  closeClient: Boolean,
  acquireSha: String,
  releaseSha: String,
  permissionsSha: String
) extends RedissonConcurrentRateLimiter[Task](
      client = client,
      monad = monad,
      strategy = strategy,
      closeClient = closeClient,
      acquireSha = acquireSha,
      releaseSha = releaseSha,
      permissionsSha = permissionsSha
    ) {}

object RedissonZioConcurrentRateLimiter {
  def useClient(
    client: RedissonClient,
    strategy: ConcurrentStrategy
  ): ZIO[Blocking, Throwable, RedissonZioConcurrentRateLimiter] = for {
    blocker <- ZIO.service[Blocking.Service]
    monad = new ZioBlockingMonadError(blocker)
    redisStrategy = RedisConcurrentStrategy(strategy)
    sha <- monad.eval {
      (
        client.getScript.scriptLoad(redisStrategy.acquireLuaScript),
        client.getScript.scriptLoad(redisStrategy.releaseLuaScript),
        client.getScript.scriptLoad(redisStrategy.permissionsLuaScript)
      )
    }
  } yield new RedissonZioConcurrentRateLimiter(
    client = client,
    strategy = redisStrategy,
    monad = monad,
    closeClient = false,
    acquireSha = sha._1,
    releaseSha = sha._2,
    permissionsSha = sha._3
  )

  def layerUsingClient(
    client: RedissonClient,
    strategy: ConcurrentStrategy
  ): ZLayer[Blocking, Throwable, Has[RedissonZioConcurrentRateLimiter]] =
    useClient(client, strategy).toLayer

  def managed(
    config: Config,
    strategy: ConcurrentStrategy
  ): ZManaged[Blocking, Throwable, RedissonZioConcurrentRateLimiter] =
    ZManaged.make {
      for {
        blocker <- ZIO.service[Blocking.Service]
        monad = new ZioBlockingMonadError(blocker)
        client <- monad.eval(Redisson.create(config))
        redisStrategy = RedisConcurrentStrategy(strategy)
        sha <- monad.eval {
          (
            client.getScript.scriptLoad(redisStrategy.acquireLuaScript),
            client.getScript.scriptLoad(redisStrategy.releaseLuaScript),
            client.getScript.scriptLoad(redisStrategy.permissionsLuaScript)
          )
        }
      } yield new RedissonZioConcurrentRateLimiter(
        client = client,
        strategy = redisStrategy,
        monad = monad,
        closeClient = true,
        acquireSha = sha._1,
        releaseSha = sha._2,
        permissionsSha = sha._3
      )
    } { limiter =>
      blocking(limiter.close().ignore)
    }

  def layerFromManaged(
    config: Config,
    strategy: ConcurrentStrategy
  ): ZLayer[Blocking, Throwable, Has[RedissonZioConcurrentRateLimiter]] =
    ZLayer.fromManaged(managed(config, strategy))
}
