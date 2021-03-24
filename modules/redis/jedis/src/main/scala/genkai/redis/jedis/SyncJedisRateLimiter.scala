package genkai.redis.jedis

import genkai.monad.IdMonad
import genkai.monad.syntax._
import genkai.{Identity, Strategy}
import genkai.redis.RedisStrategy
import redis.clients.jedis.JedisPool

final class SyncJedisRateLimiter private (
  pool: JedisPool,
  strategy: RedisStrategy,
  closeClient: Boolean,
  acquireSha: String,
  permissionsSha: String
) extends JedisRateLimiter[Identity](
      pool,
      IdMonad,
      strategy,
      closeClient,
      acquireSha,
      permissionsSha
    )

object SyncJedisRateLimiter {
  def apply(
    pool: JedisPool,
    strategy: Strategy
  ): SyncJedisRateLimiter = {
    implicit val monad = IdMonad
    val redisStrategy = RedisStrategy(strategy)

    val (acquireSha, permissionsSha) = monad.eval(pool.getResource).flatMap { client =>
      monad.guarantee {
        (
          client.scriptLoad(redisStrategy.acquireLuaScript),
          client.scriptLoad(redisStrategy.permissionsLuaScript)
        )
      }(monad.eval(client.close()))
    }
    new SyncJedisRateLimiter(pool, redisStrategy, false, acquireSha, permissionsSha)
  }
}
