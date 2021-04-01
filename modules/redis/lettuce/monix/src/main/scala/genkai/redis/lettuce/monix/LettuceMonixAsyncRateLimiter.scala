package genkai.redis.lettuce.monix

import cats.effect.Resource
import genkai.Strategy
import genkai.effect.monix.MonixMonadAsyncError
import genkai.redis.RedisStrategy
import genkai.redis.lettuce.LettuceAsyncRateLimiter
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import monix.eval.Task

class LettuceMonixAsyncRateLimiter private (
  client: RedisClient,
  connection: StatefulRedisConnection[String, String],
  strategy: RedisStrategy,
  closeClient: Boolean,
  acquireSha: String,
  permissionsSha: String,
  monad: MonixMonadAsyncError
) extends LettuceAsyncRateLimiter[Task](
      client,
      connection,
      monad,
      strategy,
      closeClient,
      acquireSha,
      permissionsSha
    ) {}

object LettuceMonixAsyncRateLimiter {
  // blocking script loading
  def useClient(
    client: RedisClient,
    strategy: Strategy
  ): Task[LettuceMonixAsyncRateLimiter] = {
    implicit val monad: MonixMonadAsyncError = new MonixMonadAsyncError

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
    } yield new LettuceMonixAsyncRateLimiter(
      client = client,
      connection = connection,
      strategy = redisStrategy,
      closeClient = false,
      acquireSha = sha._1,
      permissionsSha = sha._2,
      monad
    )
  }

  // blocking script loading
  def resource(
    redisUri: String,
    strategy: Strategy
  ): Resource[Task, LettuceMonixAsyncRateLimiter] = {
    implicit val monad: MonixMonadAsyncError = new MonixMonadAsyncError

    val redisStrategy = RedisStrategy(strategy)

    Resource.make {
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
      } yield new LettuceMonixAsyncRateLimiter(
        client = client,
        connection = connection,
        strategy = redisStrategy,
        closeClient = true,
        acquireSha = sha._1,
        permissionsSha = sha._2,
        monad
      )
    }(_.close())
  }
}
