package genkai.redis.redisson.zio

import genkai.Strategy
import genkai.effect.zio.ZioBlockingMonadError
import genkai.redis.RedisStrategy
import genkai.redis.redisson.RedissonRateLimiter
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import zio._
import zio.blocking.{Blocking, blocking}

class RedissonZioRateLimiter private (
  client: RedissonClient,
  monad: ZioBlockingMonadError,
  strategy: RedisStrategy,
  closeClient: Boolean,
  acquireSha: String,
  permissionsSha: String
) extends RedissonRateLimiter[Task](
      client,
      monad,
      strategy,
      closeClient,
      acquireSha,
      permissionsSha
    ) {}

object RedissonZioRateLimiter {
  def useClient(
    client: RedissonClient,
    strategy: Strategy
  ): ZIO[Blocking, Throwable, RedissonZioRateLimiter] = for {
    blocker <- ZIO.service[Blocking.Service]
    monad = new ZioBlockingMonadError(blocker)
    redisStrategy = RedisStrategy(strategy)
    sha <- monad.eval {
      (
        client.getScript.scriptLoad(redisStrategy.acquireLuaScript),
        client.getScript.scriptLoad(redisStrategy.permissionsLuaScript)
      )
    }
  } yield new RedissonZioRateLimiter(
    client = client,
    strategy = redisStrategy,
    monad = monad,
    closeClient = false,
    acquireSha = sha._1,
    permissionsSha = sha._2
  )

  def layerUsingClient(
    client: RedissonClient,
    strategy: Strategy
  ): ZLayer[Blocking, Throwable, Has[RedissonZioRateLimiter]] =
    useClient(client, strategy).toLayer

  def managed(
    config: Config,
    strategy: Strategy
  ): ZManaged[Blocking, Throwable, RedissonZioRateLimiter] =
    ZManaged.make {
      for {
        blocker <- ZIO.service[Blocking.Service]
        monad = new ZioBlockingMonadError(blocker)
        client <- monad.eval(Redisson.create(config))
        redisStrategy = RedisStrategy(strategy)
        sha <- monad.eval {
          (
            client.getScript.scriptLoad(redisStrategy.acquireLuaScript),
            client.getScript.scriptLoad(redisStrategy.permissionsLuaScript)
          )
        }
      } yield new RedissonZioRateLimiter(
        client = client,
        strategy = redisStrategy,
        monad = monad,
        closeClient = true,
        acquireSha = sha._1,
        permissionsSha = sha._2
      )
    } { limiter =>
      blocking(limiter.close().ignore)
    }

  def layerFromManaged(
    config: Config,
    strategy: Strategy
  ): ZLayer[Blocking, Throwable, Has[RedissonZioRateLimiter]] =
    ZLayer.fromManaged(managed(config, strategy))
}
