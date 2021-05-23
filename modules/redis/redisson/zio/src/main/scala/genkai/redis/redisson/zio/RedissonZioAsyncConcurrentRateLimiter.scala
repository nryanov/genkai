package genkai.redis.redisson.zio

import genkai.ConcurrentStrategy
import genkai.effect.zio.ZioMonadAsyncError
import genkai.redis.RedisConcurrentStrategy
import genkai.redis.redisson.RedissonAsyncConcurrentRateLimiter
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import zio.{Has, Task, ZIO, ZLayer, ZManaged}

class RedissonZioAsyncConcurrentRateLimiter private (
  client: RedissonClient,
  monad: ZioMonadAsyncError,
  strategy: RedisConcurrentStrategy,
  closeClient: Boolean,
  acquireSha: String,
  releaseSha: String,
  permissionsSha: String
) extends RedissonAsyncConcurrentRateLimiter[Task](
      client = client,
      monad = monad,
      strategy = strategy,
      closeClient = closeClient,
      acquireSha = acquireSha,
      releaseSha = releaseSha,
      permissionsSha = permissionsSha
    ) {}

object RedissonZioAsyncConcurrentRateLimiter {
  def useClient(
    client: RedissonClient,
    strategy: ConcurrentStrategy
  ): ZIO[Any, Throwable, RedissonZioAsyncConcurrentRateLimiter] = {
    val monad = new ZioMonadAsyncError()
    val redisStrategy = RedisConcurrentStrategy(strategy)

    for {
      sha <- monad.eval {
        (
          client.getScript.scriptLoad(redisStrategy.acquireLuaScript),
          client.getScript.scriptLoad(redisStrategy.releaseLuaScript),
          client.getScript.scriptLoad(redisStrategy.permissionsLuaScript)
        )
      }
    } yield new RedissonZioAsyncConcurrentRateLimiter(
      client = client,
      strategy = redisStrategy,
      monad = monad,
      closeClient = true,
      acquireSha = sha._1,
      releaseSha = sha._2,
      permissionsSha = sha._3
    )
  }

  def layerUsingClient(
    client: RedissonClient,
    strategy: ConcurrentStrategy
  ): ZLayer[Any, Throwable, Has[RedissonZioAsyncConcurrentRateLimiter]] =
    useClient(client, strategy).toLayer

  def managed(
    config: Config,
    strategy: ConcurrentStrategy
  ): ZManaged[Any, Throwable, RedissonZioAsyncConcurrentRateLimiter] = {
    val monad = new ZioMonadAsyncError()
    val redisStrategy = RedisConcurrentStrategy(strategy)

    ZManaged.make {
      for {
        client <- monad.eval(Redisson.create(config))
        sha <- monad.eval {
          (
            client.getScript.scriptLoad(redisStrategy.acquireLuaScript),
            client.getScript.scriptLoad(redisStrategy.releaseLuaScript),
            client.getScript.scriptLoad(redisStrategy.permissionsLuaScript)
          )
        }
      } yield new RedissonZioAsyncConcurrentRateLimiter(
        client = client,
        strategy = redisStrategy,
        monad = monad,
        closeClient = true,
        acquireSha = sha._1,
        releaseSha = sha._2,
        permissionsSha = sha._3
      )
    }(limiter => limiter.close().orDie)
  }

  def layerFromManaged(
    config: Config,
    strategy: ConcurrentStrategy
  ): ZLayer[Any, Throwable, Has[RedissonZioAsyncConcurrentRateLimiter]] =
    ZLayer.fromManaged(managed(config, strategy))
}
