package genkai.redis.jedis.cats

import cats.effect.{Blocker, ContextShift, Resource, Sync}
import genkai.ConcurrentStrategy
import genkai.monad.syntax._
import genkai.effect.cats.CatsMonadError
import genkai.redis.RedisConcurrentStrategy
import genkai.redis.jedis.JedisConcurrentRateLimiter
import redis.clients.jedis.util.Pool
import redis.clients.jedis.{Jedis, JedisPool}

class JedisCatsConcurrentRateLimiter[F[_]: Sync: ContextShift] private (
  pool: Pool[Jedis],
  strategy: RedisConcurrentStrategy,
  closeClient: Boolean,
  acquireSha: String,
  releaseSha: String,
  permissionsSha: String,
  monad: CatsMonadError[F]
) extends JedisConcurrentRateLimiter[F](
      pool,
      monad,
      strategy,
      closeClient,
      acquireSha,
      releaseSha,
      permissionsSha
    ) {}

object JedisCatsConcurrentRateLimiter {
  def useClient[F[_]: Sync: ContextShift](
    pool: Pool[Jedis],
    strategy: ConcurrentStrategy,
    blocker: Blocker
  ): F[JedisCatsConcurrentRateLimiter[F]] = {
    implicit val monad: CatsMonadError[F] = new CatsMonadError[F](blocker)

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
        new JedisCatsConcurrentRateLimiter(
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

  def resource[F[_]: Sync: ContextShift](
    host: String,
    port: Int,
    strategy: ConcurrentStrategy,
    blocker: Blocker
  ): Resource[F, JedisCatsConcurrentRateLimiter[F]] = {
    implicit val monad: CatsMonadError[F] = new CatsMonadError[F](blocker)

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
      } yield new JedisCatsConcurrentRateLimiter(
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
