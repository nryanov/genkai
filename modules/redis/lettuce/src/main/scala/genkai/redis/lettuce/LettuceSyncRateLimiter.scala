package genkai.redis.lettuce

import genkai.{Id, Strategy}
import genkai.monad.IdMonadError
import genkai.redis.RedisStrategy
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection

class LettuceSyncRateLimiter private (
  client: RedisClient,
  connection: StatefulRedisConnection[String, String],
  strategy: RedisStrategy,
  closeClient: Boolean,
  acquireSha: String,
  permissionsSha: String
) extends LettuceRateLimiter[Id](
      client,
      connection,
      IdMonadError,
      strategy,
      closeClient,
      acquireSha,
      permissionsSha
    )

object LettuceSyncRateLimiter {
  def apply(
    client: RedisClient,
    strategy: Strategy
  ): LettuceSyncRateLimiter = {
    val monad = IdMonadError
    val redisStrategy = RedisStrategy(strategy)

    val connection = client.connect()
    val command = connection.sync()

    val (acquireSha, permissionsSha) = monad.eval {
      (
        command.scriptLoad(redisStrategy.acquireLuaScript),
        command.scriptLoad(redisStrategy.permissionsLuaScript)
      )
    }

    new LettuceSyncRateLimiter(
      client = client,
      connection = connection,
      strategy = redisStrategy,
      closeClient = false,
      acquireSha = acquireSha,
      permissionsSha = permissionsSha
    )
  }

  def apply(
    redisUri: String,
    strategy: Strategy
  ): LettuceSyncRateLimiter = {
    val monad = IdMonadError
    val redisStrategy = RedisStrategy(strategy)

    val client = RedisClient.create(redisUri)
    val connection = client.connect()
    val command = connection.sync()

    val (acquireSha, permissionsSha) = monad.eval {
      (
        command.scriptLoad(redisStrategy.acquireLuaScript),
        command.scriptLoad(redisStrategy.permissionsLuaScript)
      )
    }

    new LettuceSyncRateLimiter(
      client = client,
      connection = connection,
      strategy = redisStrategy,
      closeClient = true,
      acquireSha = acquireSha,
      permissionsSha = permissionsSha
    )
  }
}
