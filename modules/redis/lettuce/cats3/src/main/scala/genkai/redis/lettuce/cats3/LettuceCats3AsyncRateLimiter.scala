package genkai.redis.lettuce.cats3

import cats.effect.{Async, Resource}
import genkai.Strategy
import genkai.monad.syntax._
import genkai.effect.cats3.Cats3MonadAsyncError
import genkai.redis.RedisStrategy
import genkai.redis.lettuce.LettuceAsyncRateLimiter
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection

class LettuceCats3AsyncRateLimiter[F[_]: Async] private (
  client: RedisClient,
  connection: StatefulRedisConnection[String, String],
  strategy: RedisStrategy,
  closeClient: Boolean,
  acquireSha: String,
  permissionsSha: String,
  monad: Cats3MonadAsyncError[F]
) extends LettuceAsyncRateLimiter[F](
      client,
      connection,
      monad,
      strategy,
      closeClient,
      acquireSha,
      permissionsSha
    )

object LettuceCats3AsyncRateLimiter {
  // blocking script loading
  def useClient[F[_]: Async](
    client: RedisClient,
    strategy: Strategy
  ): F[LettuceCats3AsyncRateLimiter[F]] = {
    implicit val monad: Cats3MonadAsyncError[F] = new Cats3MonadAsyncError[F]()

    val redisStrategy = RedisStrategy(strategy)

    for {
      connection <- monad.eval(client.connect())
      command = connection.sync()
      sha <- monad.eval {
        (
          command.scriptLoad(redisStrategy.acquireLuaScript),
          command.scriptLoad(redisStrategy.permissionsLuaScript)
        )
      }
    } yield new LettuceCats3AsyncRateLimiter(
      client = client,
      connection = connection,
      strategy = redisStrategy,
      closeClient = false,
      acquireSha = sha._1,
      permissionsSha = sha._2,
      monad
    )
  }

  // blocking script loading
  def resource[F[_]: Async](
    redisUri: String,
    strategy: Strategy
  ): Resource[F, LettuceCats3AsyncRateLimiter[F]] = {
    implicit val monad: Cats3MonadAsyncError[F] = new Cats3MonadAsyncError[F]()

    val redisStrategy = RedisStrategy(strategy)

    Resource.make {
      for {
        client <- monad.eval(RedisClient.create(redisUri))
        connection <- monad.eval(client.connect())
        command = connection.sync()
        sha <- monad.eval {
          (
            command.scriptLoad(redisStrategy.acquireLuaScript),
            command.scriptLoad(redisStrategy.permissionsLuaScript)
          )
        }
      } yield new LettuceCats3AsyncRateLimiter(
        client = client,
        connection = connection,
        strategy = redisStrategy,
        closeClient = true,
        acquireSha = sha._1,
        permissionsSha = sha._2,
        monad
      )
    }(_.close())
  }
}
