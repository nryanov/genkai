package genkai.redis.lettuce.zio

import genkai.ConcurrentStrategy
import genkai.effect.zio.ZioMonadError
import genkai.redis.RedisConcurrentStrategy
import genkai.redis.lettuce.LettuceConcurrentRateLimiter
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import zio._
import zio.blocking.{Blocking, blocking}

class LettuceZioConcurrentRateLimiter private (
  client: RedisClient,
  connection: StatefulRedisConnection[String, String],
  strategy: RedisConcurrentStrategy,
  closeClient: Boolean,
  acquireSha: String,
  releaseSha: String,
  permissionsSha: String,
  monad: ZioMonadError
) extends LettuceConcurrentRateLimiter[Task](
      client = client,
      connection = connection,
      monad = monad,
      strategy = strategy,
      closeClient = closeClient,
      acquireSha = acquireSha,
      releaseSha = releaseSha,
      permissionsSha = permissionsSha
    ) {}

object LettuceZioConcurrentRateLimiter {
  def useClient(
    client: RedisClient,
    strategy: ConcurrentStrategy
  ): ZIO[Blocking, Throwable, LettuceZioConcurrentRateLimiter] = for {
    blocker <- ZIO.service[Blocking.Service]
    monad = new ZioMonadError(blocker)
    redisStrategy = RedisConcurrentStrategy(strategy)
    connection <- monad.eval(client.connect())
    command = connection.sync()
    sha <- monad.eval {
      (
        command.scriptLoad(redisStrategy.acquireLuaScript),
        command.scriptLoad(redisStrategy.releaseLuaScript),
        command.scriptLoad(redisStrategy.permissionsLuaScript)
      )
    }
  } yield new LettuceZioConcurrentRateLimiter(
    client = client,
    connection = connection,
    strategy = redisStrategy,
    closeClient = false,
    acquireSha = sha._1,
    releaseSha = sha._2,
    permissionsSha = sha._2,
    monad = monad
  )

  def layerUsingClient(
    client: RedisClient,
    strategy: ConcurrentStrategy
  ): ZLayer[Blocking, Throwable, Has[LettuceZioConcurrentRateLimiter]] =
    useClient(client, strategy).toLayer

  def managed(
    redisUri: String,
    strategy: ConcurrentStrategy
  ): ZManaged[Blocking, Throwable, LettuceZioConcurrentRateLimiter] =
    ZManaged.make {
      for {
        blocker <- ZIO.service[Blocking.Service]
        monad = new ZioMonadError(blocker)
        client <- monad.eval(RedisClient.create(redisUri))
        redisStrategy = RedisConcurrentStrategy(strategy)
        connection <- monad.eval(client.connect())
        command = connection.sync()
        sha <- monad.eval {
          (
            command.scriptLoad(redisStrategy.acquireLuaScript),
            command.scriptLoad(redisStrategy.releaseLuaScript),
            command.scriptLoad(redisStrategy.permissionsLuaScript)
          )
        }
      } yield new LettuceZioConcurrentRateLimiter(
        client = client,
        connection = connection,
        strategy = redisStrategy,
        closeClient = true,
        acquireSha = sha._1,
        releaseSha = sha._2,
        permissionsSha = sha._3,
        monad = monad
      )
    } { limiter =>
      blocking(limiter.close().ignore)
    }

  def layerFromManaged(
    redisUri: String,
    strategy: ConcurrentStrategy
  ): ZLayer[Blocking, Throwable, Has[LettuceZioConcurrentRateLimiter]] =
    ZLayer.fromManaged(managed(redisUri, strategy))
}
