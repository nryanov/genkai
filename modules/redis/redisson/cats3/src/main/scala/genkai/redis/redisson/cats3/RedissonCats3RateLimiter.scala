package genkai.redis.redisson.cats3

import cats.effect.{Resource, Sync}
import genkai.Strategy
import genkai.monad.syntax._
import genkai.effect.cats3.Cats3BlockingMonadError
import genkai.redis.RedisStrategy
import genkai.redis.redisson.RedissonRateLimiter
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config

class RedissonCats3RateLimiter[F[_]: Sync] private (
  client: RedissonClient,
  monad: Cats3BlockingMonadError[F],
  strategy: RedisStrategy,
  closeClient: Boolean,
  acquireSha: String,
  permissionsSha: String
) extends RedissonRateLimiter[F](
      client,
      monad,
      strategy,
      closeClient,
      acquireSha,
      permissionsSha
    ) {}

object RedissonCats3RateLimiter {
  def useClient[F[_]: Sync](
    client: RedissonClient,
    strategy: Strategy
  ): F[RedissonCats3RateLimiter[F]] = {
    implicit val monad: Cats3BlockingMonadError[F] = new Cats3BlockingMonadError[F]()

    val redisStrategy = RedisStrategy(strategy)

    for {
      sha <- monad.eval {
        (
          client.getScript.scriptLoad(redisStrategy.acquireLuaScript),
          client.getScript.scriptLoad(redisStrategy.permissionsLuaScript)
        )
      }
    } yield new RedissonCats3RateLimiter(
      client = client,
      strategy = redisStrategy,
      monad = monad,
      closeClient = false,
      acquireSha = sha._1,
      permissionsSha = sha._2
    )
  }

  def resource[F[_]: Sync](
    config: Config,
    strategy: Strategy
  ): Resource[F, RedissonCats3RateLimiter[F]] = {
    implicit val monad: Cats3BlockingMonadError[F] = new Cats3BlockingMonadError[F]()

    val redisStrategy = RedisStrategy(strategy)

    Resource.make {
      for {
        client <- monad.eval(Redisson.create(config))
        sha <- monad.eval {
          (
            client.getScript.scriptLoad(redisStrategy.acquireLuaScript),
            client.getScript.scriptLoad(redisStrategy.permissionsLuaScript)
          )
        }
      } yield new RedissonCats3RateLimiter(
        client = client,
        strategy = redisStrategy,
        monad = monad,
        closeClient = true,
        acquireSha = sha._1,
        permissionsSha = sha._2
      )
    }(_.close())
  }
}
