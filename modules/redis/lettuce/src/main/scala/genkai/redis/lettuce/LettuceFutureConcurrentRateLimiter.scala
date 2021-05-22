package genkai.redis.lettuce

import genkai.ConcurrentStrategy
import genkai.monad.{FutureMonadAsyncError, IdMonadError}
import genkai.redis.RedisConcurrentStrategy
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection

import scala.concurrent.{ExecutionContext, Future}

class LettuceFutureConcurrentRateLimiter private (
  client: RedisClient,
  connection: StatefulRedisConnection[String, String],
  strategy: RedisConcurrentStrategy,
  closeClient: Boolean,
  acquireSha: String,
  releaseSha: String,
  permissionsSha: String
)(implicit ec: ExecutionContext)
    extends LettuceAsyncConcurrentRateLimiter[Future](
      client = client,
      connection = connection,
      monad = new FutureMonadAsyncError(),
      strategy = strategy,
      closeClient = closeClient,
      acquireSha = acquireSha,
      releaseSha = releaseSha,
      permissionsSha = permissionsSha
    )

object LettuceFutureConcurrentRateLimiter {

  /** client initialization is blocking */
  def apply(
    client: RedisClient,
    strategy: ConcurrentStrategy
  )(implicit ec: ExecutionContext): LettuceFutureConcurrentRateLimiter = {
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

    new LettuceFutureConcurrentRateLimiter(
      client = client,
      connection = connection,
      strategy = redisStrategy,
      closeClient = false,
      acquireSha = acquireSha,
      releaseSha = releaseSha,
      permissionsSha = permissionsSha
    )(ec)
  }

  /** client initialization is blocking */
  def apply(redisUri: String, strategy: ConcurrentStrategy)(implicit
    ec: ExecutionContext
  ): LettuceFutureConcurrentRateLimiter = {
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

    new LettuceFutureConcurrentRateLimiter(
      client = client,
      connection = connection,
      strategy = redisStrategy,
      closeClient = true,
      acquireSha = acquireSha,
      releaseSha = releaseSha,
      permissionsSha = permissionsSha
    )(ec)
  }
}
