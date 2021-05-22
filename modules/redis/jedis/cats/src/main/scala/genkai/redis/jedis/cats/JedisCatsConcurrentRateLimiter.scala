package genkai.redis.jedis.cats

import cats.effect.{Blocker, ContextShift, Resource, Sync}
import genkai.ConcurrentStrategy
import genkai.monad.syntax._
import genkai.effect.cats.CatsMonadError
import genkai.redis.RedisConcurrentStrategy
import genkai.redis.jedis.JedisConcurrentRateLimiter
import redis.clients.jedis.JedisPool

class JedisCatsConcurrentRateLimiter[F[_]: Sync: ContextShift] private (
  pool: JedisPool,
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
    pool: JedisPool,
    strategy: ConcurrentStrategy,
    blocker: Blocker
  ): F[JedisCatsConcurrentRateLimiter[F]] = {
    implicit val monad: CatsMonadError[F] = new CatsMonadError[F](blocker)

    val redisStrategy = RedisConcurrentStrategy(strategy)

    monad
      .eval(pool.getResource)
      .flatMap { client =>
        monad.guarantee {
          monad.eval {
            (
              client.scriptLoad(redisStrategy.acquireLuaScript),
              client.scriptLoad(redisStrategy.releaseLuaScript),
              client.scriptLoad(redisStrategy.permissionsLuaScript)
            )
          }
        }(monad.eval(client.close()))
      }
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
        sha <- monad.eval(pool.getResource).flatMap { client =>
          monad.guarantee {
            monad.eval {
              (
                client.scriptLoad(redisStrategy.acquireLuaScript),
                client.scriptLoad(redisStrategy.releaseLuaScript),
                client.scriptLoad(redisStrategy.permissionsLuaScript)
              )
            }
          }(monad.eval(client.close()))
        }
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
