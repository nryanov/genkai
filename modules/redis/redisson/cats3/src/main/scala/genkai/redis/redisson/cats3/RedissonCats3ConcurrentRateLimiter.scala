package genkai.redis.redisson.cats3

import cats.effect.{Resource, Sync}
import genkai.ConcurrentStrategy
import genkai.monad.syntax._
import genkai.effect.cats3.Cats3BlockingMonadError
import genkai.redis.RedisConcurrentStrategy
import genkai.redis.redisson.RedissonConcurrentRateLimiter
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config

class RedissonCats3ConcurrentRateLimiter[F[_]: Sync] private (
  client: RedissonClient,
  monad: Cats3BlockingMonadError[F],
  strategy: RedisConcurrentStrategy,
  closeClient: Boolean,
  acquireSha: String,
  releaseSha: String,
  permissionsSha: String
) extends RedissonConcurrentRateLimiter[F](
      client = client,
      monad = monad,
      strategy = strategy,
      closeClient = closeClient,
      acquireSha = acquireSha,
      releaseSha = releaseSha,
      permissionsSha = permissionsSha
    ) {}

object RedissonCats3ConcurrentRateLimiter {
  def useClient[F[_]: Sync](
    client: RedissonClient,
    strategy: ConcurrentStrategy
  ): F[RedissonCats3ConcurrentRateLimiter[F]] = {
    implicit val monad: Cats3BlockingMonadError[F] = new Cats3BlockingMonadError[F]()

    val redisStrategy = RedisConcurrentStrategy(strategy)

    for {
      sha <- monad.eval {
        (
          client.getScript.scriptLoad(redisStrategy.acquireLuaScript),
          client.getScript.scriptLoad(redisStrategy.releaseLuaScript),
          client.getScript.scriptLoad(redisStrategy.permissionsLuaScript)
        )
      }
    } yield new RedissonCats3ConcurrentRateLimiter(
      client = client,
      strategy = redisStrategy,
      monad = monad,
      closeClient = false,
      acquireSha = sha._1,
      releaseSha = sha._2,
      permissionsSha = sha._3
    )
  }

  def resource[F[_]: Sync](
    config: Config,
    strategy: ConcurrentStrategy
  ): Resource[F, RedissonCats3ConcurrentRateLimiter[F]] = {
    implicit val monad: Cats3BlockingMonadError[F] = new Cats3BlockingMonadError[F]()

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
      } yield new RedissonCats3ConcurrentRateLimiter(
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
