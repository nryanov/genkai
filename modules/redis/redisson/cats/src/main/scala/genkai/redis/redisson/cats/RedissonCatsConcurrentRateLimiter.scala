package genkai.redis.redisson.cats

import cats.effect.{Blocker, ContextShift, Resource, Sync}
import genkai.ConcurrentStrategy
import genkai.monad.syntax._
import genkai.effect.cats.CatsBlockingMonadError
import genkai.redis.RedisConcurrentStrategy
import genkai.redis.redisson.RedissonConcurrentRateLimiter
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config

class RedissonCatsConcurrentRateLimiter[F[_]: Sync: ContextShift] private (
  client: RedissonClient,
  monad: CatsBlockingMonadError[F],
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

object RedissonCatsConcurrentRateLimiter {
  def useClient[F[_]: Sync: ContextShift](
    client: RedissonClient,
    strategy: ConcurrentStrategy,
    blocker: Blocker
  ): F[RedissonCatsConcurrentRateLimiter[F]] = {
    implicit val monad: CatsBlockingMonadError[F] = new CatsBlockingMonadError[F](blocker)

    val redisStrategy = RedisConcurrentStrategy(strategy)

    for {
      sha <- monad.eval {
        (
          client.getScript.scriptLoad(redisStrategy.acquireLuaScript),
          client.getScript.scriptLoad(redisStrategy.releaseLuaScript),
          client.getScript.scriptLoad(redisStrategy.permissionsLuaScript)
        )
      }
    } yield new RedissonCatsConcurrentRateLimiter(
      client = client,
      strategy = redisStrategy,
      monad = monad,
      closeClient = false,
      acquireSha = sha._1,
      releaseSha = sha._2,
      permissionsSha = sha._3
    )
  }

  def resource[F[_]: Sync: ContextShift](
    config: Config,
    strategy: ConcurrentStrategy,
    blocker: Blocker
  ): Resource[F, RedissonCatsConcurrentRateLimiter[F]] = {
    implicit val monad: CatsBlockingMonadError[F] = new CatsBlockingMonadError[F](blocker)

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
      } yield new RedissonCatsConcurrentRateLimiter(
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
