package genkai.redis.lettuce.zio

import genkai.ConcurrentStrategy
import genkai.effect.zio.ZioMonadAsyncError
import genkai.redis.RedisConcurrentStrategy
import genkai.redis.lettuce.LettuceAsyncConcurrentRateLimiter
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import zio.{Has, Task, ZIO, ZLayer, ZManaged}

class LettuceZioAsyncConcurrentRateLimiter private (
  client: RedisClient,
  connection: StatefulRedisConnection[String, String],
  strategy: RedisConcurrentStrategy,
  closeClient: Boolean,
  acquireSha: String,
  releaseSha: String,
  permissionsSha: String,
  monad: ZioMonadAsyncError
) extends LettuceAsyncConcurrentRateLimiter[Task](
      client = client,
      connection = connection,
      monad = monad,
      strategy = strategy,
      closeClient = closeClient,
      acquireSha = acquireSha,
      releaseSha = releaseSha,
      permissionsSha = permissionsSha
    ) {}

object LettuceZioAsyncConcurrentRateLimiter {
  def useClient(
    client: RedisClient,
    strategy: ConcurrentStrategy
  ): ZIO[Any, Throwable, LettuceZioAsyncConcurrentRateLimiter] = {
    val monad = new ZioMonadAsyncError()
    val redisStrategy = RedisConcurrentStrategy(strategy)

    for {
      connection <- monad.eval(client.connect())
      command = connection.sync()
      sha <- monad.eval {
        (
          command.scriptLoad(redisStrategy.acquireLuaScript),
          command.scriptLoad(redisStrategy.releaseLuaScript),
          command.scriptLoad(redisStrategy.permissionsLuaScript)
        )
      }
    } yield new LettuceZioAsyncConcurrentRateLimiter(
      client = client,
      connection = connection,
      strategy = redisStrategy,
      closeClient = false,
      acquireSha = sha._1,
      releaseSha = sha._2,
      permissionsSha = sha._3,
      monad = monad
    )
  }

  def layerUsingClient(
    client: RedisClient,
    strategy: ConcurrentStrategy
  ): ZLayer[Any, Throwable, Has[LettuceZioAsyncConcurrentRateLimiter]] =
    useClient(client, strategy).toLayer

  def managed(
    redisUri: String,
    strategy: ConcurrentStrategy
  ): ZManaged[Any, Throwable, LettuceZioAsyncConcurrentRateLimiter] = {
    val monad = new ZioMonadAsyncError()
    val redisStrategy = RedisConcurrentStrategy(strategy)

    ZManaged.make {
      for {
        client <- monad.eval(RedisClient.create(redisUri))
        connection <- monad.eval(client.connect())
        command = connection.sync()
        sha <- monad.eval {
          (
            command.scriptLoad(redisStrategy.acquireLuaScript),
            command.scriptLoad(redisStrategy.releaseLuaScript),
            command.scriptLoad(redisStrategy.permissionsLuaScript)
          )
        }
      } yield new LettuceZioAsyncConcurrentRateLimiter(
        client = client,
        connection = connection,
        strategy = redisStrategy,
        closeClient = false,
        acquireSha = sha._1,
        releaseSha = sha._2,
        permissionsSha = sha._3,
        monad = monad
      )
    }(limiter => limiter.close().orDie)
  }

  def layerFromManaged(
    redisUri: String,
    strategy: ConcurrentStrategy
  ): ZLayer[Any, Throwable, Has[LettuceZioAsyncConcurrentRateLimiter]] =
    ZLayer.fromManaged(managed(redisUri, strategy))
}
