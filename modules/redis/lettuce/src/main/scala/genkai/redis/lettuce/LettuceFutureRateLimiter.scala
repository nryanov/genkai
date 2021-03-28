package genkai.redis.lettuce

import genkai.Strategy
import genkai.monad.{FutureMonadAsyncError, IdMonadError}
import genkai.redis.RedisStrategy
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection

import scala.concurrent.{ExecutionContext, Future}

class LettuceFutureRateLimiter private (
  client: RedisClient,
  connection: StatefulRedisConnection[String, String],
  strategy: RedisStrategy,
  closeClient: Boolean,
  acquireSha: String,
  permissionsSha: String
)(implicit ec: ExecutionContext)
    extends LettuceAsyncRateLimiter[Future](
      client,
      connection,
      new FutureMonadAsyncError(),
      strategy,
      closeClient,
      acquireSha,
      permissionsSha
    )

object LettuceFutureRateLimiter {

  /** client initialization is blocking */
  def apply(
    client: RedisClient,
    strategy: Strategy
  )(implicit ec: ExecutionContext): LettuceFutureRateLimiter = {
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

    new LettuceFutureRateLimiter(
      client = client,
      connection = connection,
      strategy = redisStrategy,
      closeClient = false,
      acquireSha = acquireSha,
      permissionsSha = permissionsSha
    )(ec)
  }

  /** client initialization is blocking */
  def apply(redisUri: String, strategy: Strategy)(implicit
    ec: ExecutionContext
  ): LettuceFutureRateLimiter = {
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

    new LettuceFutureRateLimiter(
      client = client,
      connection = connection,
      strategy = redisStrategy,
      closeClient = true,
      acquireSha = acquireSha,
      permissionsSha = permissionsSha
    )(ec)
  }
}
