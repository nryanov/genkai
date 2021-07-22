package genkai.redis.jedis

import genkai.monad.IdMonadError
import genkai.{Id, Strategy}
import genkai.redis.RedisStrategy
import redis.clients.jedis.util.Pool
import redis.clients.jedis.{Jedis, JedisPool}

class JedisSyncRateLimiter private (
  pool: Pool[Jedis],
  strategy: RedisStrategy,
  closeClient: Boolean,
  acquireSha: String,
  permissionsSha: String
) extends JedisRateLimiter[Id](
      pool,
      IdMonadError,
      strategy,
      closeClient,
      acquireSha,
      permissionsSha
    )

object JedisSyncRateLimiter {
  def apply(
    pool: Pool[Jedis],
    strategy: Strategy
  ): JedisSyncRateLimiter = {
    implicit val monad = IdMonadError
    val redisStrategy = RedisStrategy(strategy)

    val (acquireSha, permissionsSha) = monad.bracket(monad.eval(pool.getResource))(client =>
      monad.eval(
        (
          client.scriptLoad(redisStrategy.acquireLuaScript),
          client.scriptLoad(redisStrategy.permissionsLuaScript)
        )
      )
    )(resource => monad.eval(resource.close()))
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

    val (acquireSha, permissionsSha) = monad.bracket(monad.eval(pool.getResource))(client =>
      monad.eval(
        (
          client.scriptLoad(redisStrategy.acquireLuaScript),
          client.scriptLoad(redisStrategy.permissionsLuaScript)
        )
      )
    )(resource => monad.eval(resource.close()))
    new JedisSyncRateLimiter(
      pool = pool,
      strategy = redisStrategy,
      closeClient = true,
      acquireSha = acquireSha,
      permissionsSha = permissionsSha
    )
  }
}
