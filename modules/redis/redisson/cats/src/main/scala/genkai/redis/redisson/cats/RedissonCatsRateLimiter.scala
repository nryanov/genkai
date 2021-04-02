package genkai.redis.redisson.cats

import cats.effect.{Blocker, ContextShift, Resource, Sync}
import genkai.Strategy
import genkai.monad.syntax._
import genkai.effect.cats.CatsMonadError
import genkai.redis.RedisStrategy
import genkai.redis.redisson.RedissonRateLimiter
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config

class RedissonCatsRateLimiter[F[_]: Sync: ContextShift] private (
  client: RedissonClient,
  monad: CatsMonadError[F],
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

object RedissonCatsRateLimiter {
  def useClient[F[_]: Sync: ContextShift](
    client: RedissonClient,
    strategy: Strategy,
    blocker: Blocker
  ): F[RedissonCatsRateLimiter[F]] = {
    implicit val monad: CatsMonadError[F] = new CatsMonadError[F](blocker)

    val redisStrategy = RedisStrategy(strategy)

    for {
      sha <- monad.eval {
        (
          client.getScript.scriptLoad(redisStrategy.acquireLuaScript),
          client.getScript.scriptLoad(redisStrategy.permissionsLuaScript)
        )
      }
    } yield new RedissonCatsRateLimiter(
      client = client,
      strategy = redisStrategy,
      monad = monad,
      closeClient = false,
      acquireSha = sha._1,
      permissionsSha = sha._2
    )
  }

  def resource[F[_]: Sync: ContextShift](
    config: Config,
    strategy: Strategy,
    blocker: Blocker
  ): Resource[F, RedissonCatsRateLimiter[F]] = {
    implicit val monad: CatsMonadError[F] = new CatsMonadError[F](blocker)

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
      } yield new RedissonCatsRateLimiter(
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
