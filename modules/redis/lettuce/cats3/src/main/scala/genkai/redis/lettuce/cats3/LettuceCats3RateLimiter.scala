package genkai.redis.lettuce.cats3

import cats.effect.{Resource, Sync}
import genkai.Strategy
import genkai.monad.syntax._
import genkai.effect.cats3.Cats3BlockingMonadError
import genkai.redis.RedisStrategy
import genkai.redis.lettuce.LettuceRateLimiter
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection

class LettuceCats3RateLimiter[F[_]: Sync] private (
  client: RedisClient,
  connection: StatefulRedisConnection[String, String],
  strategy: RedisStrategy,
  closeClient: Boolean,
  acquireSha: String,
  permissionsSha: String,
  monad: Cats3BlockingMonadError[F]
) extends LettuceRateLimiter[F](
      client,
      connection,
      monad,
      strategy,
      closeClient,
      acquireSha,
      permissionsSha
    )

object LettuceCats3RateLimiter {
  def useClient[F[_]: Sync](
    client: RedisClient,
    strategy: Strategy
  ): F[LettuceCats3RateLimiter[F]] = {
    implicit val monad: Cats3BlockingMonadError[F] = new Cats3BlockingMonadError[F]()

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
    } yield new LettuceCats3RateLimiter(
      client = client,
      connection = connection,
      strategy = redisStrategy,
      closeClient = false,
      acquireSha = sha._1,
      permissionsSha = sha._2,
      monad
    )
  }

  def resource[F[_]: Sync](
    redisUri: String,
    strategy: Strategy
  ): Resource[F, LettuceCats3RateLimiter[F]] = {
    implicit val monad: Cats3BlockingMonadError[F] = new Cats3BlockingMonadError[F]()

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
      } yield new LettuceCats3RateLimiter(
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
