package genkai.redis.redisson

import genkai.Strategy
import genkai.monad.{FutureMonad, IdMonad}
import genkai.redis.RedisStrategy
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config

import scala.concurrent.{ExecutionContext, Future}

class RedissonFutureRateLimiter private (
  client: RedissonClient,
  strategy: RedisStrategy,
  closeClient: Boolean,
  acquireSha: String,
  permissionsSha: String
)(implicit ec: ExecutionContext)
    extends RedissonAsyncRateLimiter[Future](
      client,
      new FutureMonad(),
      strategy,
      closeClient,
      acquireSha,
      permissionsSha
    )

object RedissonFutureRateLimiter {

  /** blocking initialization */
  def apply(
    client: RedissonClient,
    strategy: Strategy
  )(implicit ec: ExecutionContext): RedissonFutureRateLimiter = {
    val monad = IdMonad
    val redisStrategy = RedisStrategy(strategy)

    val (acquireSha, permissionsSha) = monad.eval {
      (
        client.getScript.scriptLoad(redisStrategy.acquireLuaScript),
        client.getScript.scriptLoad(redisStrategy.permissionsLuaScript)
      )
    }

    new RedissonFutureRateLimiter(
      client = client,
      strategy = redisStrategy,
      closeClient = false,
      acquireSha = acquireSha,
      permissionsSha = permissionsSha
    )
  }

  /** blocking initialization */
  def apply(
    config: Config,
    strategy: Strategy
  )(implicit ec: ExecutionContext): RedissonFutureRateLimiter = {
    val monad = IdMonad

    val client = Redisson.create(config)
    val redisStrategy = RedisStrategy(strategy)

    val (acquireSha, permissionsSha) = monad.eval {
      (
        client.getScript.scriptLoad(redisStrategy.acquireLuaScript),
        client.getScript.scriptLoad(redisStrategy.permissionsLuaScript)
      )
    }

    new RedissonFutureRateLimiter(
      client = client,
      strategy = redisStrategy,
      closeClient = true,
      acquireSha = acquireSha,
      permissionsSha = permissionsSha
    )
  }
}
