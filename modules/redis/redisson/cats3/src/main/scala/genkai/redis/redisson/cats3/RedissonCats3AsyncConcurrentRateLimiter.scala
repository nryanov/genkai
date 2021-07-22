package genkai.redis.redisson.cats3

import cats.effect.{Async, Resource}
import genkai.ConcurrentStrategy
import genkai.monad.syntax._
import genkai.effect.cats3.Cats3MonadAsyncError
import genkai.redis.RedisConcurrentStrategy
import genkai.redis.redisson.RedissonAsyncConcurrentRateLimiter
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config

class RedissonCats3AsyncConcurrentRateLimiter[F[_]: Async] private (
  client: RedissonClient,
  monad: Cats3MonadAsyncError[F],
  strategy: RedisConcurrentStrategy,
  closeClient: Boolean,
  acquireSha: String,
  releaseSha: String,
  permissionsSha: String
) extends RedissonAsyncConcurrentRateLimiter[F](
      client = client,
      monad = monad,
      strategy = strategy,
      closeClient = closeClient,
      acquireSha = acquireSha,
      releaseSha = releaseSha,
      permissionsSha = permissionsSha
    ) {}

object RedissonCats3AsyncConcurrentRateLimiter {
  // blocking script loading
  def useClient[F[_]: Async](
    client: RedissonClient,
    strategy: ConcurrentStrategy
  ): F[RedissonCats3AsyncConcurrentRateLimiter[F]] = {
    implicit val monad: Cats3MonadAsyncError[F] = new Cats3MonadAsyncError[F]()

    val redisStrategy = RedisConcurrentStrategy(strategy)

    for {
      sha <- monad.eval {
        (
          client.getScript.scriptLoad(redisStrategy.acquireLuaScript),
          client.getScript.scriptLoad(redisStrategy.releaseLuaScript),
          client.getScript.scriptLoad(redisStrategy.permissionsLuaScript)
        )
      }
    } yield new RedissonCats3AsyncConcurrentRateLimiter(
      client = client,
      strategy = redisStrategy,
      monad = monad,
      closeClient = false,
      acquireSha = sha._1,
      releaseSha = sha._2,
      permissionsSha = sha._3
    )
  }

  // blocking script loading
  def resource[F[_]: Async](
    config: Config,
    strategy: ConcurrentStrategy
  ): Resource[F, RedissonCats3AsyncConcurrentRateLimiter[F]] = {
    implicit val monad: Cats3MonadAsyncError[F] = new Cats3MonadAsyncError[F]()

    val redisStrategy = RedisConcurrentStrategy(strategy)

    Resource.make {
      for {
        client <- monad.eval(Redisson.create(config))
        sha <- monad.eval {
          (
            client.getScript.scriptLoad(redisStrategy.acquireLuaScript),
            client.getScript.scriptLoad(redisStrategy.releaseLuaScript),
            client.getScript.scriptLoad(redisStrategy.permissionsLuaScript)
          )
        }
      } yield new RedissonCats3AsyncConcurrentRateLimiter(
        client = client,
        strategy = redisStrategy,
        monad = monad,
        closeClient = true,
        acquireSha = sha._1,
        releaseSha = sha._2,
        permissionsSha = sha._3
      )
    }(_.close())
  }
}
