package genkai.redis.redisson.monix

import cats.effect.Resource
import genkai.Strategy
import genkai.effect.monix.MonixMonadAsyncError
import genkai.redis.RedisStrategy
import genkai.redis.redisson.RedissonAsyncRateLimiter
import monix.eval.Task
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config

class RedissonMonixAsyncRateLimiter private (
  client: RedissonClient,
  monad: MonixMonadAsyncError,
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

object RedissonMonixAsyncRateLimiter {
  // blocking script loading
  def useClient(
    client: RedissonClient,
    strategy: Strategy
  ): Task[RedissonMonixAsyncRateLimiter] = {
    implicit val monad: MonixMonadAsyncError = new MonixMonadAsyncError()

    val redisStrategy = RedisStrategy(strategy)

    for {
      sha <- monad.eval {
        (
          client.getScript.scriptLoad(redisStrategy.acquireLuaScript),
          client.getScript.scriptLoad(redisStrategy.permissionsLuaScript)
        )
      }
    } yield new RedissonMonixAsyncRateLimiter(
      client = client,
      strategy = redisStrategy,
      monad = monad,
      closeClient = false,
      acquireSha = sha._1,
      permissionsSha = sha._2
    )
  }

  // blocking script loading
  def resource(
    config: Config,
    strategy: Strategy
  ): Resource[Task, RedissonMonixAsyncRateLimiter] = {
    implicit val monad: MonixMonadAsyncError = new MonixMonadAsyncError()

    val redisStrategy = RedisStrategy(strategy)

    Resource.make {
      for {
        client <- monad.eval(Redisson.create(config))
        sha <- monad.eval {
          (
            client.getScript.scriptLoad(redisStrategy.acquireLuaScript),
            client.getScript.scriptLoad(redisStrategy.permissionsLuaScript)
          )
        }
      } yield new RedissonMonixAsyncRateLimiter(
        client = client,
        strategy = redisStrategy,
        monad = monad,
        closeClient = true,
        acquireSha = sha._1,
        permissionsSha = sha._2
      )
    }(_.close())
  }
}
