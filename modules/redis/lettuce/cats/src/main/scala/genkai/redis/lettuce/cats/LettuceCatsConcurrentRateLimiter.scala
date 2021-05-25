package genkai.redis.lettuce.cats

import cats.effect.{Blocker, ContextShift, Resource, Sync}
import genkai.ConcurrentStrategy
import genkai.monad.syntax._
import genkai.effect.cats.CatsMonadError
import genkai.redis.RedisConcurrentStrategy
import genkai.redis.lettuce.LettuceConcurrentRateLimiter
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection

class LettuceCatsConcurrentRateLimiter[F[_]: Sync: ContextShift] private (
  client: RedisClient,
  connection: StatefulRedisConnection[String, String],
  strategy: RedisConcurrentStrategy,
  closeClient: Boolean,
  acquireSha: String,
  releaseSha: String,
  permissionsSha: String,
  monad: CatsMonadError[F]
) extends LettuceConcurrentRateLimiter[F](
      client = client,
      connection = connection,
      monad = monad,
      strategy = strategy,
      closeClient = closeClient,
      acquireSha = acquireSha,
      releaseSha = releaseSha,
      permissionsSha = permissionsSha
    )

object LettuceCatsConcurrentRateLimiter {
  def useClient[F[_]: Sync: ContextShift](
    client: RedisClient,
    strategy: ConcurrentStrategy,
    blocker: Blocker
  ): F[LettuceCatsConcurrentRateLimiter[F]] = {
    implicit val monad: CatsMonadError[F] = new CatsMonadError[F](blocker)

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
    } yield new LettuceCatsConcurrentRateLimiter(
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

  def resource[F[_]: Sync: ContextShift](
    redisUri: String,
    strategy: ConcurrentStrategy,
    blocker: Blocker
  ): Resource[F, LettuceCatsConcurrentRateLimiter[F]] = {
    implicit val monad: CatsMonadError[F] = new CatsMonadError[F](blocker)

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
      } yield new LettuceCatsConcurrentRateLimiter(
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
