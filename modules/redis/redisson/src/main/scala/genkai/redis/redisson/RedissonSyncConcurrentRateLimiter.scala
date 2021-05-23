package genkai.redis.redisson

import genkai.{ConcurrentStrategy, Identity}
import genkai.monad.IdMonadError
import genkai.redis.RedisConcurrentStrategy
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config

class RedissonSyncConcurrentRateLimiter private (
  client: RedissonClient,
  strategy: RedisConcurrentStrategy,
  closeClient: Boolean,
  acquireSha: String,
  releaseSha: String,
  permissionsSha: String
) extends RedissonConcurrentRateLimiter[Identity](
      client = client,
      monad = IdMonadError,
      strategy = strategy,
      closeClient = closeClient,
      acquireSha = acquireSha,
      releaseSha = releaseSha,
      permissionsSha = permissionsSha
    )

object RedissonSyncConcurrentRateLimiter {
  def apply(
    client: RedissonClient,
    strategy: ConcurrentStrategy
  ): RedissonSyncConcurrentRateLimiter = {
    val monad = IdMonadError
    val redisStrategy = RedisConcurrentStrategy(strategy)

    val (acquireSha, releaseSha, permissionsSha) = monad.eval {
      (
        client.getScript.scriptLoad(redisStrategy.acquireLuaScript),
        client.getScript.scriptLoad(redisStrategy.releaseLuaScript),
        client.getScript.scriptLoad(redisStrategy.permissionsLuaScript)
      )
    }

    new RedissonSyncConcurrentRateLimiter(
      client = client,
      strategy = redisStrategy,
      closeClient = false,
      acquireSha = acquireSha,
      releaseSha = releaseSha,
      permissionsSha = permissionsSha
    )
  }

  def apply(
    config: Config,
    strategy: ConcurrentStrategy
  ): RedissonSyncConcurrentRateLimiter = {
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

    new RedissonSyncConcurrentRateLimiter(
      client = client,
      strategy = redisStrategy,
      closeClient = true,
      acquireSha = acquireSha,
      releaseSha = releaseSha,
      permissionsSha = permissionsSha
    )
  }
}
