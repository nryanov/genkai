package genkai.redis.redisson.cats

import cats.effect.{Concurrent, Resource}
import genkai.ConcurrentStrategy
import genkai.monad.syntax._
import genkai.effect.cats.CatsMonadAsyncError
import genkai.redis.RedisConcurrentStrategy
import genkai.redis.redisson.RedissonAsyncConcurrentRateLimiter
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config

class RedissonCatsAsyncConcurrentRateLimiter[F[_]: Concurrent] private (
  client: RedissonClient,
  monad: CatsMonadAsyncError[F],
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

object RedissonCatsAsyncConcurrentRateLimiter {
  // blocking script loading
  def useClient[F[_]: Concurrent](
    client: RedissonClient,
    strategy: ConcurrentStrategy
  ): F[RedissonCatsAsyncConcurrentRateLimiter[F]] = {
    implicit val monad: CatsMonadAsyncError[F] = new CatsMonadAsyncError[F]()

    val redisStrategy = RedisConcurrentStrategy(strategy)

    for {
      sha <- monad.eval {
        (
          client.getScript.scriptLoad(redisStrategy.acquireLuaScript),
          client.getScript.scriptLoad(redisStrategy.releaseLuaScript),
          client.getScript.scriptLoad(redisStrategy.permissionsLuaScript)
        )
      }
    } yield new RedissonCatsAsyncConcurrentRateLimiter(
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
  def resource[F[_]: Concurrent](
    config: Config,
    strategy: ConcurrentStrategy
  ): Resource[F, RedissonCatsAsyncConcurrentRateLimiter[F]] = {
    implicit val monad: CatsMonadAsyncError[F] = new CatsMonadAsyncError[F]()

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
      } yield new RedissonCatsAsyncConcurrentRateLimiter(
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
