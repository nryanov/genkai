package genkai.redis.jedis.cats3

import cats.effect.{Resource, Sync}
import genkai.Strategy
import genkai.effect.cats3.Cats3BlockingMonadError
import genkai.monad.syntax._
import genkai.redis.RedisStrategy
import genkai.redis.jedis.JedisRateLimiter
import redis.clients.jedis.util.Pool
import redis.clients.jedis.{Jedis, JedisPool}

class JedisCats3RateLimiter[F[_]: Sync] private (
  pool: Pool[Jedis],
  strategy: RedisStrategy,
  closeClient: Boolean,
  acquireSha: String,
  permissionsSha: String,
  monad: Cats3BlockingMonadError[F]
) extends JedisRateLimiter[F](
      pool,
      monad,
      strategy,
      closeClient,
      acquireSha,
      permissionsSha
    ) {}

object JedisCats3RateLimiter {
  def useClient[F[_]: Sync](
    pool: Pool[Jedis],
    strategy: Strategy
  ): F[JedisCats3RateLimiter[F]] = {
    implicit val monad: Cats3BlockingMonadError[F] = new Cats3BlockingMonadError[F]()

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
        new JedisCats3RateLimiter(
          pool = pool,
          strategy = redisStrategy,
          closeClient = false,
          acquireSha = acquireSha,
          permissionsSha = permissionsSha,
          monad = monad
        )
      }
  }

  def resource[F[_]: Sync](
    host: String,
    port: Int,
    strategy: Strategy
  ): Resource[F, JedisCats3RateLimiter[F]] = {
    implicit val monad: Cats3BlockingMonadError[F] = new Cats3BlockingMonadError[F]()

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
      } yield new JedisCats3RateLimiter(
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
