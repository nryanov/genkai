package genkai.redis.redisson.zio

import genkai.Strategy
import genkai.effect.zio.ZioMonadAsyncError
import genkai.redis.RedisStrategy
import genkai.redis.redisson.RedissonAsyncRateLimiter
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import zio.{Has, Task, ZIO, ZLayer, ZManaged}

class RedissonZioAsyncRateLimiter private (
  client: RedissonClient,
  monad: ZioMonadAsyncError,
  strategy: RedisStrategy,
  closeClient: Boolean,
  acquireSha: String,
  permissionsSha: String
) extends RedissonAsyncRateLimiter[Task](
      client,
      monad,
      strategy,
      closeClient,
      acquireSha,
      permissionsSha
    ) {}

object RedissonZioAsyncRateLimiter {
  def useClient(
    client: RedissonClient,
    strategy: Strategy
  ): ZIO[Any, Throwable, RedissonZioAsyncRateLimiter] = {
    val monad = new ZioMonadAsyncError()
    val redisStrategy = RedisStrategy(strategy)

    for {
      sha <- monad.eval {
        (
          client.getScript.scriptLoad(redisStrategy.acquireLuaScript),
          client.getScript.scriptLoad(redisStrategy.permissionsLuaScript)
        )
      }
    } yield new RedissonZioAsyncRateLimiter(
      client = client,
      strategy = redisStrategy,
      monad = monad,
      closeClient = true,
      acquireSha = sha._1,
      permissionsSha = sha._2
    )
  }

  def layerUsingClient(
    client: RedissonClient,
    strategy: Strategy
  ): ZLayer[Any, Throwable, Has[RedissonZioAsyncRateLimiter]] =
    useClient(client, strategy).toLayer

  def managed(
    config: Config,
    strategy: Strategy
  ): ZManaged[Any, Throwable, RedissonZioAsyncRateLimiter] = {
    val monad = new ZioMonadAsyncError()
    val redisStrategy = RedisStrategy(strategy)

    ZManaged.make {
      for {
        client <- monad.eval(Redisson.create(config))
        sha <- monad.eval {
          (
            client.getScript.scriptLoad(redisStrategy.acquireLuaScript),
            client.getScript.scriptLoad(redisStrategy.permissionsLuaScript)
          )
        }
      } yield new RedissonZioAsyncRateLimiter(
        client = client,
        strategy = redisStrategy,
        monad = monad,
        closeClient = true,
        acquireSha = sha._1,
        permissionsSha = sha._2
      )
    }(limiter => limiter.close().orDie)
  }

  def layerFromManaged(
    config: Config,
    strategy: Strategy
  ): ZLayer[Any, Throwable, Has[RedissonZioAsyncRateLimiter]] =
    ZLayer.fromManaged(managed(config, strategy))
}
