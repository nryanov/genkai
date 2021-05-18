package genkai.redis.jedis.cats

import cats.effect.{Resource, Sync}
import genkai.Strategy
import genkai.monad.syntax._
import genkai.effect.cats.CatsMonadError
import genkai.redis.RedisStrategy
import genkai.redis.jedis.JedisRateLimiter
import redis.clients.jedis.JedisPool

class JedisCatsRateLimiter[F[_]: Sync: ContextShift] private (
  pool: JedisPool,
  strategy: RedisStrategy,
  closeClient: Boolean,
  acquireSha: String,
  permissionsSha: String,
  monad: CatsMonadError[F]
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
    pool: JedisPool,
    strategy: Strategy): F[JedisCatsRateLimiter[F]] = {
    implicit val monad: CatsMonadError[F] = new CatsMonadError[F](blocker)

    val redisStrategy = RedisStrategy(strategy)

    monad
      .eval(pool.getResource)
      .flatMap { client =>
        monad.guarantee {
          monad.eval {
            (
              client.scriptLoad(redisStrategy.acquireLuaScript),
              client.scriptLoad(redisStrategy.permissionsLuaScript)
            )
          }
        }(monad.eval(client.close()))
      }
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
    strategy: Strategy): Resource[F, JedisCatsRateLimiter[F]] = {
    implicit val monad: CatsMonadError[F] = new CatsMonadError[F](blocker)

    val redisStrategy = RedisStrategy(strategy)

    Resource.make {
      for {
        pool <- monad.eval(new JedisPool(host, port))
        sha <- monad.eval(pool.getResource).flatMap { client =>
          monad.guarantee {
            monad.eval {
              (
                client.scriptLoad(redisStrategy.acquireLuaScript),
                client.scriptLoad(redisStrategy.permissionsLuaScript)
              )
            }
          }(monad.eval(client.close()))
        }
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
