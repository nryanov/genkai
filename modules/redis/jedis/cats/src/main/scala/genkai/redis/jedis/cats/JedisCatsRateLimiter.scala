package genkai.redis.jedis.cats

import cats.effect.{Resource, Sync}
import genkai.Strategy
import genkai.monad.syntax._
import genkai.effect.cats.CatsBlockingMonadError
import genkai.redis.RedisStrategy
import genkai.redis.jedis.JedisRateLimiter
import redis.clients.jedis.util.Pool
import redis.clients.jedis.{Jedis, JedisPool}

class JedisCatsRateLimiter[F[_]: Sync: ContextShift] private (
  pool: Pool[Jedis],
  strategy: RedisStrategy,
  closeClient: Boolean,
  acquireSha: String,
  permissionsSha: String,
  monad: CatsBlockingMonadError[F]
) extends JedisRateLimiter[F](
      pool,
      monad,
      strategy,
      closeClient,
      acquireSha,
      permissionsSha
    ) {}

object JedisCatsRateLimiter {
  def useClient[F[_]: Sync: ContextShift](
    pool: Pool[Jedis],
    strategy: Strategy,
    blocker: Blocker
  ): F[JedisCatsRateLimiter[F]] = {
    implicit val monad: CatsBlockingMonadError[F] = new CatsBlockingMonadError[F](blocker)

    val redisStrategy = RedisStrategy(strategy)

    monad
      .bracket(monad.eval(pool.getResource))(client =>
        monad.eval(
          (
            client.scriptLoad(redisStrategy.acquireLuaScript),
            client.scriptLoad(redisStrategy.permissionsLuaScript)
          )
        )
      )(resource => monad.eval(resource.close()))
      .map { case (acquireSha, permissionsSha) =>
        new JedisCatsRateLimiter(
          pool = pool,
          strategy = redisStrategy,
          closeClient = false,
          acquireSha = acquireSha,
          permissionsSha = permissionsSha,
          monad = monad
        )
      }
  }

  def resource[F[_]: Sync: ContextShift](
    host: String,
    port: Int,
    strategy: Strategy,
    blocker: Blocker
  ): Resource[F, JedisCatsRateLimiter[F]] = {
    implicit val monad: CatsBlockingMonadError[F] = new CatsBlockingMonadError[F](blocker)

    val redisStrategy = RedisStrategy(strategy)

    Resource.make {
      for {
        pool <- monad.eval(new JedisPool(host, port))
        sha <- monad.bracket(monad.eval(pool.getResource))(client =>
          monad.eval(
            (
              client.scriptLoad(redisStrategy.acquireLuaScript),
              client.scriptLoad(redisStrategy.permissionsLuaScript)
            )
          )
        )(resource => monad.eval(resource.close()))
      } yield new JedisCatsRateLimiter(
        pool = pool,
        strategy = redisStrategy,
        closeClient = true,
        acquireSha = sha._1,
        permissionsSha = sha._2,
        monad = monad
      )
    }(_.close())
  }
}
