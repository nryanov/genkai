package genkai.redis.lettuce.cats3

import cats.effect.{Async, Resource}
import genkai.ConcurrentStrategy
import genkai.effect.cats3.Cats3MonadAsyncError
import genkai.monad.syntax._
import genkai.redis.RedisConcurrentStrategy
import genkai.redis.lettuce.LettuceAsyncConcurrentRateLimiter
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection

class LettuceCats3AsyncConcurrentRateLimiter[F[_]: Async] private (
  client: RedisClient,
  connection: StatefulRedisConnection[String, String],
  strategy: RedisConcurrentStrategy,
  closeClient: Boolean,
  acquireSha: String,
  releaseSha: String,
  permissionsSha: String,
  monad: Cats3MonadAsyncError[F]
) extends LettuceAsyncConcurrentRateLimiter[F](
      client = client,
      connection = connection,
      monad = monad,
      strategy = strategy,
      closeClient = closeClient,
      acquireSha = acquireSha,
      releaseSha = releaseSha,
      permissionsSha = permissionsSha
    )

object LettuceCats3AsyncConcurrentRateLimiter {
  // blocking script loading
  def useClient[F[_]: Async](
    client: RedisClient,
    strategy: ConcurrentStrategy
  ): F[LettuceCats3AsyncConcurrentRateLimiter[F]] = {
    implicit val monad: Cats3MonadAsyncError[F] = new Cats3MonadAsyncError[F]()

    val redisStrategy = RedisConcurrentStrategy(strategy)

    for {
      connection <- monad.eval(client.connect())
      command = connection.sync()
      sha <- monad.eval {
        (
          command.scriptLoad(redisStrategy.acquireLuaScript),
          command.scriptLoad(redisStrategy.releaseLuaScript),
          command.scriptLoad(redisStrategy.permissionsLuaScript)
        )
      }
    } yield new LettuceCats3AsyncConcurrentRateLimiter(
      client = client,
      connection = connection,
      strategy = redisStrategy,
      closeClient = false,
      acquireSha = sha._1,
      releaseSha = sha._2,
      permissionsSha = sha._3,
      monad
    )
  }

  // blocking script loading
  def resource[F[_]: Async](
    redisUri: String,
    strategy: ConcurrentStrategy
  ): Resource[F, LettuceCats3AsyncConcurrentRateLimiter[F]] = {
    implicit val monad: Cats3MonadAsyncError[F] = new Cats3MonadAsyncError[F]()

    val redisStrategy = RedisConcurrentStrategy(strategy)

    Resource.make {
      for {
        client <- monad.eval(RedisClient.create(redisUri))
        connection <- monad.eval(client.connect())
        command = connection.sync()
        sha <- monad.eval {
          (
            command.scriptLoad(redisStrategy.acquireLuaScript),
            command.scriptLoad(redisStrategy.releaseLuaScript),
            command.scriptLoad(redisStrategy.permissionsLuaScript)
          )
        }
      } yield new LettuceCats3AsyncConcurrentRateLimiter(
        client = client,
        connection = connection,
        strategy = redisStrategy,
        closeClient = true,
        acquireSha = sha._1,
        releaseSha = sha._2,
        permissionsSha = sha._3,
        monad
      )
    }(_.close())
  }
}
