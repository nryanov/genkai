package genkai.redis.redisson.monix

import cats.effect.Resource
import genkai.ConcurrentStrategy
import genkai.effect.monix.MonixMonadAsyncError
import genkai.redis.RedisConcurrentStrategy
import genkai.redis.redisson.RedissonAsyncConcurrentRateLimiter
import monix.eval.Task
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config

class RedissonMonixAsyncConcurrentRateLimiter private (
  client: RedissonClient,
  monad: MonixMonadAsyncError,
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

object RedissonMonixAsyncConcurrentRateLimiter {
  // blocking script loading
  def useClient(
    client: RedissonClient,
    strategy: ConcurrentStrategy
  ): Task[RedissonMonixAsyncConcurrentRateLimiter] = {
    implicit val monad: MonixMonadAsyncError = new MonixMonadAsyncError()

    val redisStrategy = RedisConcurrentStrategy(strategy)

    for {
      sha <- monad.eval {
        (
          client.getScript.scriptLoad(redisStrategy.acquireLuaScript),
          client.getScript.scriptLoad(redisStrategy.releaseLuaScript),
          client.getScript.scriptLoad(redisStrategy.permissionsLuaScript)
        )
      }
    } yield new RedissonMonixAsyncConcurrentRateLimiter(
      client = client,
      strategy = redisStrategy,
      monad = monad,
      closeClient = false,
      acquireSha = sha._1,
      releaseSha = sha._2,
      permissionsSha = sha._3
    )
  }

  // blocking script loading
  def resource(
    config: Config,
    strategy: ConcurrentStrategy
  ): Resource[Task, RedissonMonixAsyncConcurrentRateLimiter] = {
    implicit val monad: MonixMonadAsyncError = new MonixMonadAsyncError()

    val redisStrategy = RedisConcurrentStrategy(strategy)

    Resource.make {
      for {
        client <- monad.eval(Redisson.create(config))
        sha <- monad.eval {
          (
            client.getScript.scriptLoad(redisStrategy.acquireLuaScript),
            client.getScript.scriptLoad(redisStrategy.releaseLuaScript),
            client.getScript.scriptLoad(redisStrategy.permissionsLuaScript)
          )
        }
      } yield new RedissonMonixAsyncConcurrentRateLimiter(
        client = client,
        strategy = redisStrategy,
        monad = monad,
        closeClient = true,
        acquireSha = sha._1,
        releaseSha = sha._2,
        permissionsSha = sha._3
      )
    }(_.close())
  }
}
