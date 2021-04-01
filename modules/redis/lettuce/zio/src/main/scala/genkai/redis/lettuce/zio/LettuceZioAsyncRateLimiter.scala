package genkai.redis.lettuce.zio

import genkai.Strategy
import genkai.effect.zio.ZioMonadAsyncError
import genkai.redis.RedisStrategy
import genkai.redis.lettuce.LettuceAsyncRateLimiter
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import zio.{Has, Task, ZIO, ZLayer, ZManaged}

class LettuceZioAsyncRateLimiter private (
  client: RedisClient,
  connection: StatefulRedisConnection[String, String],
  strategy: RedisStrategy,
  closeClient: Boolean,
  acquireSha: String,
  permissionsSha: String,
  monad: ZioMonadAsyncError
) extends LettuceAsyncRateLimiter[Task](
      client,
      connection,
      monad,
      strategy,
      closeClient,
      acquireSha,
      permissionsSha
    ) {}

object LettuceZioAsyncRateLimiter {
  def useClient(
    client: RedisClient,
    strategy: Strategy
  ): ZIO[Any, Throwable, LettuceZioAsyncRateLimiter] = {
    val monad = new ZioMonadAsyncError()
    val redisStrategy = RedisStrategy(strategy)

    for {
      connection <- monad.eval(client.connect())
      command = connection.sync()
      sha <- monad.eval {
        (
          command.scriptLoad(redisStrategy.acquireLuaScript),
          command.scriptLoad(redisStrategy.permissionsLuaScript)
        )
      }
    } yield new LettuceZioAsyncRateLimiter(
      client = client,
      connection = connection,
      strategy = redisStrategy,
      closeClient = false,
      acquireSha = sha._1,
      permissionsSha = sha._2,
      monad = monad
    )
  }

  def layerUsingClient(
    client: RedisClient,
    strategy: Strategy
  ): ZLayer[Any, Throwable, Has[LettuceZioAsyncRateLimiter]] =
    useClient(client, strategy).toLayer

  def managed(
    redisUri: String,
    strategy: Strategy
  ): ZManaged[Any, Throwable, LettuceZioAsyncRateLimiter] = {
    val monad = new ZioMonadAsyncError()
    val redisStrategy = RedisStrategy(strategy)

    ZManaged.make {
      for {
        client <- monad.eval(RedisClient.create(redisUri))
        connection <- monad.eval(client.connect())
        command = connection.sync()
        sha <- monad.eval {
          (
            command.scriptLoad(redisStrategy.acquireLuaScript),
            command.scriptLoad(redisStrategy.permissionsLuaScript)
          )
        }
      } yield new LettuceZioAsyncRateLimiter(
        client = client,
        connection = connection,
        strategy = redisStrategy,
        closeClient = false,
        acquireSha = sha._1,
        permissionsSha = sha._2,
        monad = monad
      )
    }(limiter => limiter.close().orDie)
  }

  def layerFromManaged(
    redisUri: String,
    strategy: Strategy
  ): ZLayer[Any, Throwable, Has[LettuceZioAsyncRateLimiter]] =
    ZLayer.fromManaged(managed(redisUri, strategy))
}
