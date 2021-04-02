package genkai.redis.redisson.cats

import cats.effect.{Concurrent, Resource}
import genkai.Strategy
import genkai.monad.syntax._
import genkai.effect.cats.CatsMonadAsyncError
import genkai.redis.RedisStrategy
import genkai.redis.redisson.RedissonAsyncRateLimiter
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config

class RedissonCatsAsyncRateLimiter[F[_]: Concurrent] private (
  client: RedissonClient,
  monad: CatsMonadAsyncError[F],
  strategy: RedisStrategy,
  closeClient: Boolean,
  acquireSha: String,
  permissionsSha: String
) extends RedissonAsyncRateLimiter[F](
      client,
      monad,
      strategy,
      closeClient,
      acquireSha,
      permissionsSha
    ) {}

object RedissonCatsAsyncRateLimiter {
  // blocking script loading
  def useClient[F[_]: Concurrent](
    client: RedissonClient,
    strategy: Strategy
  ): F[RedissonCatsAsyncRateLimiter[F]] = {
    implicit val monad: CatsMonadAsyncError[F] = new CatsMonadAsyncError[F]()

    val redisStrategy = RedisStrategy(strategy)

    for {
      sha <- monad.eval {
        (
          client.getScript.scriptLoad(redisStrategy.acquireLuaScript),
          client.getScript.scriptLoad(redisStrategy.permissionsLuaScript)
        )
      }
    } yield new RedissonCatsAsyncRateLimiter(
      client = client,
      strategy = redisStrategy,
      monad = monad,
      closeClient = false,
      acquireSha = sha._1,
      permissionsSha = sha._2
    )
  }

  // blocking script loading
  def resource[F[_]: Concurrent](
    config: Config,
    strategy: Strategy
  ): Resource[F, RedissonCatsAsyncRateLimiter[F]] = {
    implicit val monad: CatsMonadAsyncError[F] = new CatsMonadAsyncError[F]()

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
      } yield new RedissonCatsAsyncRateLimiter(
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
