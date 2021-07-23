package genkai.redis.lettuce.cats

import cats.effect.{Resource, Sync}
import genkai.Strategy
import genkai.monad.syntax._
import genkai.effect.cats.CatsBlockingMonadError
import genkai.redis.RedisStrategy
import genkai.redis.lettuce.LettuceRateLimiter
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection

class LettuceCatsRateLimiter[F[_]: Sync: ContextShift] private (
  client: RedisClient,
  connection: StatefulRedisConnection[String, String],
  strategy: RedisStrategy,
  closeClient: Boolean,
  acquireSha: String,
  permissionsSha: String,
  monad: CatsBlockingMonadError[F]
) extends LettuceRateLimiter[F](
      client,
      connection,
      monad,
      strategy,
      closeClient,
      acquireSha,
      permissionsSha
    )

object LettuceCatsRateLimiter {
  def useClient[F[_]: Sync: ContextShift](
    client: RedisClient,
    strategy: Strategy,
    blocker: Blocker
  ): F[LettuceCatsRateLimiter[F]] = {
    implicit val monad: CatsBlockingMonadError[F] = new CatsBlockingMonadError[F](blocker)

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
    } yield new LettuceCatsRateLimiter(
      client = client,
      connection = connection,
      strategy = redisStrategy,
      closeClient = false,
      acquireSha = sha._1,
      permissionsSha = sha._2,
      monad
    )
  }

  def resource[F[_]: Sync: ContextShift](
    redisUri: String,
    strategy: Strategy,
    blocker: Blocker
  ): Resource[F, LettuceCatsRateLimiter[F]] = {
    implicit val monad: CatsBlockingMonadError[F] = new CatsBlockingMonadError[F](blocker)

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
      } yield new LettuceCatsRateLimiter(
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
