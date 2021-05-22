package genkai.redis.lettuce.monix

import cats.effect.Resource
import genkai.ConcurrentStrategy
import genkai.effect.monix.MonixMonadAsyncError
import genkai.redis.RedisConcurrentStrategy
import genkai.redis.lettuce.LettuceAsyncConcurrentRateLimiter
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import monix.eval.Task

class LettuceMonixAsyncConcurrentRateLimiter private (
  client: RedisClient,
  connection: StatefulRedisConnection[String, String],
  strategy: RedisConcurrentStrategy,
  closeClient: Boolean,
  acquireSha: String,
  releaseSha: String,
  permissionsSha: String,
  monad: MonixMonadAsyncError
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

object LettuceMonixAsyncConcurrentRateLimiter {
  // blocking script loading
  def useClient(
    client: RedisClient,
    strategy: ConcurrentStrategy
  ): Task[LettuceMonixAsyncConcurrentRateLimiter] = {
    implicit val monad: MonixMonadAsyncError = new MonixMonadAsyncError

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
    } yield new LettuceMonixAsyncConcurrentRateLimiter(
      client = client,
      connection = connection,
      strategy = redisStrategy,
      closeClient = false,
      acquireSha = sha._1,
      releaseSha = sha._2,
      permissionsSha = sha._3,
      monad
    )
  }

  // blocking script loading
  def resource(
    redisUri: String,
    strategy: ConcurrentStrategy
  ): Resource[Task, LettuceMonixAsyncConcurrentRateLimiter] = {
    implicit val monad: MonixMonadAsyncError = new MonixMonadAsyncError

    val redisStrategy = RedisConcurrentStrategy(strategy)

    Resource.make {
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
      } yield new LettuceMonixAsyncConcurrentRateLimiter(
        client = client,
        connection = connection,
        strategy = redisStrategy,
        closeClient = true,
        acquireSha = sha._1,
        releaseSha = sha._2,
        permissionsSha = sha._3,
        monad
      )
    }(_.close())
  }
}
