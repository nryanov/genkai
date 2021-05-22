package genkai.redis.lettuce.cats

import cats.effect.{Concurrent, Resource}
import genkai.ConcurrentStrategy
import genkai.monad.syntax._
import genkai.effect.cats.CatsMonadAsyncError
import genkai.redis.RedisConcurrentStrategy
import genkai.redis.lettuce.LettuceAsyncConcurrentRateLimiter
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection

class LettuceCatsAsyncConcurrentRateLimiter[F[_]: Concurrent] private (
  client: RedisClient,
  connection: StatefulRedisConnection[String, String],
  strategy: RedisConcurrentStrategy,
  closeClient: Boolean,
  acquireSha: String,
  releaseSha: String,
  permissionsSha: String,
  monad: CatsMonadAsyncError[F]
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

object LettuceCatsAsyncConcurrentRateLimiter {
  // blocking script loading
  def useClient[F[_]: Concurrent](
    client: RedisClient,
    strategy: ConcurrentStrategy
  ): F[LettuceCatsAsyncConcurrentRateLimiter[F]] = {
    implicit val monad: CatsMonadAsyncError[F] = new CatsMonadAsyncError[F]()

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
    } yield new LettuceCatsAsyncConcurrentRateLimiter(
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
  def resource[F[_]: Concurrent](
    redisUri: String,
    strategy: ConcurrentStrategy
  ): Resource[F, LettuceCatsAsyncConcurrentRateLimiter[F]] = {
    implicit val monad: CatsMonadAsyncError[F] = new CatsMonadAsyncError[F]()

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
      } yield new LettuceCatsAsyncConcurrentRateLimiter(
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
