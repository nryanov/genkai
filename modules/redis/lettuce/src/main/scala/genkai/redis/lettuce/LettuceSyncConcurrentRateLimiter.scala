package genkai.redis.lettuce

import genkai.{ConcurrentStrategy, Identity}
import genkai.monad.IdMonadError
import genkai.redis.RedisConcurrentStrategy
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection

class LettuceSyncConcurrentRateLimiter private (
  client: RedisClient,
  connection: StatefulRedisConnection[String, String],
  strategy: RedisConcurrentStrategy,
  closeClient: Boolean,
  acquireSha: String,
  releaseSha: String,
  permissionsSha: String
) extends LettuceConcurrentRateLimiter[Identity](
      client,
      connection,
      monad = IdMonadError,
      strategy = strategy,
      closeClient = closeClient,
      acquireSha = acquireSha,
      releaseSha = releaseSha,
      permissionsSha = permissionsSha
    )

object LettuceSyncConcurrentRateLimiter {
  def apply(
    client: RedisClient,
    strategy: ConcurrentStrategy
  ): LettuceSyncConcurrentRateLimiter = {
    val monad = IdMonadError
    val redisStrategy = RedisConcurrentStrategy(strategy)

    val connection = client.connect()
    val command = connection.sync()

    val (acquireSha, releaseSha, permissionsSha) = monad.eval {
      (
        command.scriptLoad(redisStrategy.acquireLuaScript),
        command.scriptLoad(redisStrategy.releaseLuaScript),
        command.scriptLoad(redisStrategy.permissionsLuaScript)
      )
    }

    new LettuceSyncConcurrentRateLimiter(
      client = client,
      connection = connection,
      strategy = redisStrategy,
      closeClient = false,
      acquireSha = acquireSha,
      releaseSha = releaseSha,
      permissionsSha = permissionsSha
    )
  }

  def apply(
    redisUri: String,
    strategy: ConcurrentStrategy
  ): LettuceSyncConcurrentRateLimiter = {
    val monad = IdMonadError
    val redisStrategy = RedisConcurrentStrategy(strategy)

    val client = RedisClient.create(redisUri)
    val connection = client.connect()
    val command = connection.sync()

    val (acquireSha, releaseSha, permissionsSha) = monad.eval {
      (
        command.scriptLoad(redisStrategy.acquireLuaScript),
        command.scriptLoad(redisStrategy.releaseLuaScript),
        command.scriptLoad(redisStrategy.permissionsLuaScript)
      )
    }

    new LettuceSyncConcurrentRateLimiter(
      client = client,
      connection = connection,
      strategy = redisStrategy,
      closeClient = true,
      acquireSha = acquireSha,
      releaseSha = releaseSha,
      permissionsSha = permissionsSha
    )
  }
}
