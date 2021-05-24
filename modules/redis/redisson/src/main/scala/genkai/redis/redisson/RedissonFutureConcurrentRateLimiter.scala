package genkai.redis.redisson

import genkai.ConcurrentStrategy
import genkai.monad.{FutureMonadAsyncError, IdMonadError}
import genkai.redis.RedisConcurrentStrategy
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config

import scala.concurrent.{ExecutionContext, Future}

class RedissonFutureConcurrentRateLimiter private (
  client: RedissonClient,
  strategy: RedisConcurrentStrategy,
  closeClient: Boolean,
  acquireSha: String,
  releaseSha: String,
  permissionsSha: String
)(implicit ec: ExecutionContext)
    extends RedissonAsyncConcurrentRateLimiter[Future](
      client = client,
      monad = new FutureMonadAsyncError(),
      strategy = strategy,
      closeClient = closeClient,
      acquireSha = acquireSha,
      releaseSha = releaseSha,
      permissionsSha = permissionsSha
    )

object RedissonFutureConcurrentRateLimiter {

  /** blocking initialization */
  def apply(
    client: RedissonClient,
    strategy: ConcurrentStrategy
  )(implicit ec: ExecutionContext): RedissonFutureConcurrentRateLimiter = {
    val monad = IdMonadError
    val redisStrategy = RedisConcurrentStrategy(strategy)

    val (acquireSha, releaseSha, permissionsSha) = monad.eval {
      (
        client.getScript.scriptLoad(redisStrategy.acquireLuaScript),
        client.getScript.scriptLoad(redisStrategy.releaseLuaScript),
        client.getScript.scriptLoad(redisStrategy.permissionsLuaScript)
      )
    }

    new RedissonFutureConcurrentRateLimiter(
      client = client,
      strategy = redisStrategy,
      closeClient = false,
      acquireSha = acquireSha,
      releaseSha = releaseSha,
      permissionsSha = permissionsSha
    )
  }

  /** blocking initialization */
  def apply(
    config: Config,
    strategy: ConcurrentStrategy
  )(implicit ec: ExecutionContext): RedissonFutureConcurrentRateLimiter = {
    val monad = IdMonadError

    val client = Redisson.create(config)
    val redisStrategy = RedisConcurrentStrategy(strategy)

    val (acquireSha, releaseSha, permissionsSha) = monad.eval {
      (
        client.getScript.scriptLoad(redisStrategy.acquireLuaScript),
        client.getScript.scriptLoad(redisStrategy.releaseLuaScript),
        client.getScript.scriptLoad(redisStrategy.permissionsLuaScript)
      )
    }

    new RedissonFutureConcurrentRateLimiter(
      client = client,
      strategy = redisStrategy,
      closeClient = true,
      acquireSha = acquireSha,
      releaseSha = releaseSha,
      permissionsSha = permissionsSha
    )
  }
}
