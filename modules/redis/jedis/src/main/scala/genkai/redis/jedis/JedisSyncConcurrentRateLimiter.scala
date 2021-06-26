package genkai.redis.jedis

import redis.clients.jedis.{Jedis, JedisPool}
import genkai.monad.syntax._
import genkai.monad.IdMonadError
import genkai.redis.RedisConcurrentStrategy
import genkai.{ConcurrentStrategy, Id}
import redis.clients.jedis.util.Pool

class JedisSyncConcurrentRateLimiter private (
  pool: Pool[Jedis],
  strategy: RedisConcurrentStrategy,
  closeClient: Boolean,
  acquireSha: String,
  releaseSha: String,
  permissionsSha: String
) extends JedisConcurrentRateLimiter[Id](
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
    pool: Pool[Jedis],
    strategy: ConcurrentStrategy
  ): JedisSyncConcurrentRateLimiter = {
    implicit val monad = IdMonadError
    val redisStrategy = RedisConcurrentStrategy(strategy)

    val (acquireSha, releaseSha, permissionsSha) =
      monad.bracket(monad.eval(pool.getResource))(client =>
        monad.eval(
          (
            client.scriptLoad(redisStrategy.acquireLuaScript),
            client.scriptLoad(redisStrategy.releaseLuaScript),
            client.scriptLoad(redisStrategy.permissionsLuaScript)
          )
        )
      )(resource => monad.eval(resource.close()))
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

    val (acquireSha, releaseSha, permissionsSha) =
      monad.bracket(monad.eval(pool.getResource))(client =>
        monad.eval(
          (
            client.scriptLoad(redisStrategy.acquireLuaScript),
            client.scriptLoad(redisStrategy.releaseLuaScript),
            client.scriptLoad(redisStrategy.permissionsLuaScript)
          )
        )
      )(resource => monad.eval(resource.close()))
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
