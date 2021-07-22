package genkai.redis.lettuce.zio

import genkai.Strategy
import genkai.effect.zio.ZioBlockingMonadError
import genkai.redis.RedisStrategy
import genkai.redis.lettuce.LettuceRateLimiter
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import zio._
import zio.blocking.{Blocking, blocking}

class LettuceZioRateLimiter private (
  client: RedisClient,
  connection: StatefulRedisConnection[String, String],
  strategy: RedisStrategy,
  closeClient: Boolean,
  acquireSha: String,
  permissionsSha: String,
  monad: ZioBlockingMonadError
) extends LettuceRateLimiter[Task](
      client,
      connection,
      monad,
      strategy,
      closeClient,
      acquireSha,
      permissionsSha
    ) {}

object LettuceZioRateLimiter {
  def useClient(
    client: RedisClient,
    strategy: Strategy
  ): ZIO[Blocking, Throwable, LettuceZioRateLimiter] = for {
    blocker <- ZIO.service[Blocking.Service]
    monad = new ZioBlockingMonadError(blocker)
    redisStrategy = RedisStrategy(strategy)
    connection <- monad.eval(client.connect())
    command = connection.sync()
    sha <- monad.eval {
      (
        command.scriptLoad(redisStrategy.acquireLuaScript),
        command.scriptLoad(redisStrategy.permissionsLuaScript)
      )
    }
  } yield new LettuceZioRateLimiter(
    client = client,
    connection = connection,
    strategy = redisStrategy,
    closeClient = false,
    acquireSha = sha._1,
    permissionsSha = sha._2,
    monad = monad
  )

  def layerUsingClient(
    client: RedisClient,
    strategy: Strategy
  ): ZLayer[Blocking, Throwable, Has[LettuceZioRateLimiter]] =
    useClient(client, strategy).toLayer

  def managed(
    redisUri: String,
    strategy: Strategy
  ): ZManaged[Blocking, Throwable, LettuceZioRateLimiter] =
    ZManaged.make {
      for {
        blocker <- ZIO.service[Blocking.Service]
        monad = new ZioBlockingMonadError(blocker)
        client <- monad.eval(RedisClient.create(redisUri))
        redisStrategy = RedisStrategy(strategy)
        connection <- monad.eval(client.connect())
        command = connection.sync()
        sha <- monad.eval {
          (
            command.scriptLoad(redisStrategy.acquireLuaScript),
            command.scriptLoad(redisStrategy.permissionsLuaScript)
          )
        }
      } yield new LettuceZioRateLimiter(
        client = client,
        connection = connection,
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
    redisUri: String,
    strategy: Strategy
  ): ZLayer[Blocking, Throwable, Has[LettuceZioRateLimiter]] =
    ZLayer.fromManaged(managed(redisUri, strategy))
}
