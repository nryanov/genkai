package genkai.redis.jedis.cats3

import cats.effect.{Resource, Sync}
import genkai.ConcurrentStrategy
import genkai.monad.syntax._
import genkai.effect.cats3.Cats3BlockingMonadError
import genkai.redis.RedisConcurrentStrategy
import genkai.redis.jedis.JedisConcurrentRateLimiter
import redis.clients.jedis.{Jedis, JedisPool}
import redis.clients.jedis.util.Pool

class JedisCats3ConcurrentRateLimiter[F[_]: Sync] private (
  pool: Pool[Jedis],
  strategy: RedisConcurrentStrategy,
  closeClient: Boolean,
  acquireSha: String,
  releaseSha: String,
  permissionsSha: String,
  monad: Cats3BlockingMonadError[F]
) extends JedisConcurrentRateLimiter[F](
      pool,
      monad,
      strategy,
      closeClient,
      acquireSha,
      releaseSha,
      permissionsSha
    ) {}

object JedisCats3ConcurrentRateLimiter {
  def useClient[F[_]: Sync](
    pool: Pool[Jedis],
    strategy: ConcurrentStrategy
  ): F[JedisCats3ConcurrentRateLimiter[F]] = {
    implicit val monad: Cats3BlockingMonadError[F] = new Cats3BlockingMonadError[F]()

    val redisStrategy = RedisConcurrentStrategy(strategy)

    monad
      .bracket(monad.eval(pool.getResource))(client =>
        monad.eval(
          (
            client.scriptLoad(redisStrategy.acquireLuaScript),
            client.scriptLoad(redisStrategy.releaseLuaScript),
            client.scriptLoad(redisStrategy.permissionsLuaScript)
          )
        )
      )(resource => monad.eval(resource.close()))
      .map { case (acquireSha, releaseSha, permissionsSha) =>
        new JedisCats3ConcurrentRateLimiter(
          pool = pool,
          strategy = redisStrategy,
          closeClient = false,
          acquireSha = acquireSha,
          releaseSha = releaseSha,
          permissionsSha = permissionsSha,
          monad = monad
        )
      }
  }

  def resource[F[_]: Sync](
    host: String,
    port: Int,
    strategy: ConcurrentStrategy
  ): Resource[F, JedisCats3ConcurrentRateLimiter[F]] = {
    implicit val monad: Cats3BlockingMonadError[F] = new Cats3BlockingMonadError[F]()

    val redisStrategy = RedisConcurrentStrategy(strategy)

    Resource.make {
      for {
        pool <- monad.eval(new JedisPool(host, port))
        sha <- monad.bracket(monad.eval(pool.getResource))(client =>
          monad.eval(
            (
              client.scriptLoad(redisStrategy.acquireLuaScript),
              client.scriptLoad(redisStrategy.releaseLuaScript),
              client.scriptLoad(redisStrategy.permissionsLuaScript)
            )
          )
        )(resource => monad.eval(resource.close()))
      } yield new JedisCats3ConcurrentRateLimiter(
        pool = pool,
        strategy = redisStrategy,
        closeClient = true,
        acquireSha = sha._1,
        releaseSha = sha._2,
        permissionsSha = sha._3,
        monad = monad
      )
    }(_.close())
  }
}
