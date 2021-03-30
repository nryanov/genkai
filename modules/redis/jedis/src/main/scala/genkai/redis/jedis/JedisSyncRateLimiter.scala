package genkai.redis.jedis

import genkai.monad.IdMonadError
import genkai.monad.syntax._
import genkai.{Identity, Strategy}
import genkai.redis.RedisStrategy
import redis.clients.jedis.JedisPool

class JedisSyncRateLimiter private (
  pool: JedisPool,
  strategy: RedisStrategy,
  closeClient: Boolean,
  acquireSha: String,
  permissionsSha: String
) extends JedisRateLimiter[Identity](
      pool,
      IdMonadError,
      strategy,
      closeClient,
      acquireSha,
      permissionsSha
    )

object JedisSyncRateLimiter {
  def apply(
    pool: JedisPool,
    strategy: Strategy
  ): JedisSyncRateLimiter = {
    implicit val monad = IdMonadError
    val redisStrategy = RedisStrategy(strategy)

    val (acquireSha, permissionsSha) = monad.eval(pool.getResource).flatMap { client =>
      monad.guarantee {
        (
          client.scriptLoad(redisStrategy.acquireLuaScript),
          client.scriptLoad(redisStrategy.permissionsLuaScript)
        )
      }(monad.eval(client.close()))
    }
    new JedisSyncRateLimiter(
      pool = pool,
      strategy = redisStrategy,
      closeClient = false,
      acquireSha = acquireSha,
      permissionsSha = permissionsSha
    )
  }

  def apply(
    host: String,
    port: Int,
    strategy: Strategy
  ): JedisSyncRateLimiter = {
    implicit val monad = IdMonadError
    val redisStrategy = RedisStrategy(strategy)
    val pool = new JedisPool(host, port)

    val (acquireSha, permissionsSha) = monad.eval(pool.getResource).flatMap { client =>
      monad.guarantee {
        (
          client.scriptLoad(redisStrategy.acquireLuaScript),
          client.scriptLoad(redisStrategy.permissionsLuaScript)
        )
      }(monad.eval(client.close()))
    }
    new JedisSyncRateLimiter(
      pool = pool,
      strategy = redisStrategy,
      closeClient = true,
      acquireSha = acquireSha,
      permissionsSha = permissionsSha
    )
  }
}
