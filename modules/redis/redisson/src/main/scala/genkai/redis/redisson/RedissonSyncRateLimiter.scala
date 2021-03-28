package genkai.redis.redisson

import genkai.{Identity, Strategy}
import genkai.monad.IdMonadError
import genkai.redis.RedisStrategy
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config

class RedissonSyncRateLimiter private (
  client: RedissonClient,
  strategy: RedisStrategy,
  closeClient: Boolean,
  acquireSha: String,
  permissionsSha: String
) extends RedissonRateLimiter[Identity](
      client,
      IdMonadError,
      strategy,
      closeClient,
      acquireSha,
      permissionsSha
    )

object RedissonSyncRateLimiter {
  def apply(
    client: RedissonClient,
    strategy: Strategy
  ): RedissonSyncRateLimiter = {
    val monad = IdMonadError
    val redisStrategy = RedisStrategy(strategy)

    val (acquireSha, permissionsSha) = monad.eval {
      (
        client.getScript.scriptLoad(redisStrategy.acquireLuaScript),
        client.getScript.scriptLoad(redisStrategy.permissionsLuaScript)
      )
    }

    new RedissonSyncRateLimiter(
      client = client,
      strategy = redisStrategy,
      closeClient = false,
      acquireSha = acquireSha,
      permissionsSha = permissionsSha
    )
  }

  def apply(
    config: Config,
    strategy: Strategy
  ): RedissonSyncRateLimiter = {
    val monad = IdMonadError

    val client = Redisson.create(config)
    val redisStrategy = RedisStrategy(strategy)

    val (acquireSha, permissionsSha) = monad.eval {
      (
        client.getScript.scriptLoad(redisStrategy.acquireLuaScript),
        client.getScript.scriptLoad(redisStrategy.permissionsLuaScript)
      )
    }

    new RedissonSyncRateLimiter(
      client = client,
      strategy = redisStrategy,
      closeClient = true,
      acquireSha = acquireSha,
      permissionsSha = permissionsSha
    )
  }
}
