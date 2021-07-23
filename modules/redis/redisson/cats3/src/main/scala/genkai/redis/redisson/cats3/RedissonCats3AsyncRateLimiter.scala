package genkai.redis.redisson.cats3

import cats.effect.{Async, Resource}
import genkai.Strategy
import genkai.monad.syntax._
import genkai.effect.cats3.Cats3MonadAsyncError
import genkai.redis.RedisStrategy
import genkai.redis.redisson.RedissonAsyncRateLimiter
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config

class RedissonCats3AsyncRateLimiter[F[_]: Async] private (
  client: RedissonClient,
  monad: Cats3MonadAsyncError[F],
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

object RedissonCats3AsyncRateLimiter {
  // blocking script loading
  def useClient[F[_]: Async](
    client: RedissonClient,
    strategy: Strategy
  ): F[RedissonCats3AsyncRateLimiter[F]] = {
    implicit val monad: Cats3MonadAsyncError[F] = new Cats3MonadAsyncError[F]()

    val redisStrategy = RedisStrategy(strategy)

    for {
      sha <- monad.eval {
        (
          client.getScript.scriptLoad(redisStrategy.acquireLuaScript),
          client.getScript.scriptLoad(redisStrategy.permissionsLuaScript)
        )
      }
    } yield new RedissonCats3AsyncRateLimiter(
      client = client,
      strategy = redisStrategy,
      monad = monad,
      closeClient = false,
      acquireSha = sha._1,
      permissionsSha = sha._2
    )
  }

  // blocking script loading
  def resource[F[_]: Async](
    config: Config,
    strategy: Strategy
  ): Resource[F, RedissonCats3AsyncRateLimiter[F]] = {
    implicit val monad: Cats3MonadAsyncError[F] = new Cats3MonadAsyncError[F]()

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
      } yield new RedissonCats3AsyncRateLimiter(
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
