package genkai.redis.jedis

import redis.clients.jedis.JedisPool
import genkai.monad.syntax._
import genkai.monad.IdMonadError
import genkai.redis.RedisConcurrentStrategy
import genkai.{ConcurrentStrategy, Identity}

class JedisSyncConcurrentRateLimiter private (
  pool: JedisPool,
  strategy: RedisConcurrentStrategy,
  closeClient: Boolean,
  acquireSha: String,
  releaseSha: String,
  permissionsSha: String
) extends JedisConcurrentRateLimiter[Identity](
      pool,
      IdMonadError,
      strategy,
      closeClient,
      acquireSha,
      releaseSha,
      permissionsSha
    )

object JedisSyncConcurrentRateLimiter {
  def apply(
    pool: JedisPool,
    strategy: ConcurrentStrategy
  ): JedisSyncConcurrentRateLimiter = {
    implicit val monad = IdMonadError
    val redisStrategy = RedisConcurrentStrategy(strategy)

    val (acquireSha, releaseSha, permissionsSha) = monad.eval(pool.getResource).flatMap { client =>
      monad.guarantee {
        (
          client.scriptLoad(redisStrategy.acquireLuaScript),
          client.scriptLoad(redisStrategy.releaseLuaScript),
          client.scriptLoad(redisStrategy.permissionsLuaScript)
        )
      }(monad.eval(client.close()))
    }
    new JedisSyncConcurrentRateLimiter(
      pool = pool,
      strategy = redisStrategy,
      closeClient = false,
      acquireSha = acquireSha,
      releaseSha = releaseSha,
      permissionsSha = permissionsSha
    )
  }

  def apply(
    host: String,
    port: Int,
    strategy: ConcurrentStrategy
  ): JedisSyncConcurrentRateLimiter = {
    implicit val monad = IdMonadError
    val redisStrategy = RedisConcurrentStrategy(strategy)
    val pool = new JedisPool(host, port)

    val (acquireSha, releaseSha, permissionsSha) = monad.eval(pool.getResource).flatMap { client =>
      monad.guarantee {
        (
          client.scriptLoad(redisStrategy.acquireLuaScript),
          client.scriptLoad(redisStrategy.releaseLuaScript),
          client.scriptLoad(redisStrategy.permissionsLuaScript)
        )
      }(monad.eval(client.close()))
    }
    new JedisSyncConcurrentRateLimiter(
      pool = pool,
      strategy = redisStrategy,
      closeClient = true,
      acquireSha = acquireSha,
      releaseSha = releaseSha,
      permissionsSha = permissionsSha
    )
  }
}
