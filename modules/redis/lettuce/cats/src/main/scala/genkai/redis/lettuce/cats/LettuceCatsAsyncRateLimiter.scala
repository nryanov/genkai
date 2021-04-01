package genkai.redis.lettuce.cats

import cats.effect.{Concurrent, Resource}
import genkai.Strategy
import genkai.monad.syntax._
import genkai.effect.cats.CatsMonadAsyncError
import genkai.redis.RedisStrategy
import genkai.redis.lettuce.LettuceAsyncRateLimiter
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection

class LettuceCatsAsyncRateLimiter[F[_]: Concurrent] private (
  client: RedisClient,
  connection: StatefulRedisConnection[String, String],
  strategy: RedisStrategy,
  closeClient: Boolean,
  acquireSha: String,
  permissionsSha: String,
  monad: CatsMonadAsyncError[F]
) extends LettuceAsyncRateLimiter[F](
      client,
      connection,
      monad,
      strategy,
      closeClient,
      acquireSha,
      permissionsSha
    )

object LettuceCatsAsyncRateLimiter {
  // blocking script loading
  def useClient[F[_]: Concurrent](
    client: RedisClient,
    strategy: Strategy
  ): F[LettuceCatsAsyncRateLimiter[F]] = {
    implicit val monad: CatsMonadAsyncError[F] = new CatsMonadAsyncError[F]()

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
    } yield new LettuceCatsAsyncRateLimiter(
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
  def resource[F[_]: Concurrent](
    redisUri: String,
    strategy: Strategy
  ): Resource[F, LettuceCatsAsyncRateLimiter[F]] = {
    implicit val monad: CatsMonadAsyncError[F] = new CatsMonadAsyncError[F]()

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
      } yield new LettuceCatsAsyncRateLimiter(
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
