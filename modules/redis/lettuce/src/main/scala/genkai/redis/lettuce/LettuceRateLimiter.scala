package genkai.redis.lettuce

import java.time.Instant

import genkai.monad.syntax._
import genkai.{Key, RateLimiter}
import genkai.monad.MonadError
import genkai.redis.RedisStrategy
import io.lettuce.core.{RedisClient, ScriptOutputType}
import io.lettuce.core.api.StatefulRedisConnection

abstract class LettuceRateLimiter[F[_]](
  client: RedisClient,
  connection: StatefulRedisConnection[String, String],
  implicit val monad: MonadError[F],
  strategy: RedisStrategy,
  closeClient: Boolean,
  acquireSha: String,
  permissionsSha: String
) extends RateLimiter[F] {

  private val syncCommands = connection.sync()

  override def permissions[A: Key](key: A): F[Long] = {
    val now = Instant.now()

    monad
      .eval(
        syncCommands.evalsha[Long](
          permissionsSha,
          ScriptOutputType.INTEGER,
          Array(strategy.key(key, now)),
          strategy.args(now): _*
        )
      )
      .map(strategy.toPermissions)
  }

  override def reset[A: Key](key: A): F[Unit] = {
    val now = Instant.now()
    monad.eval(syncCommands.unlink(strategy.key(key, now))).void
  }

  override def acquire[A: Key](key: A, instant: Instant): F[Boolean] =
    monad
      .eval(
        syncCommands.evalsha[Long](
          acquireSha,
          ScriptOutputType.INTEGER,
          Array(strategy.key(key, instant)),
          strategy.argsWithTtl(instant): _*
        )
      )
      .map(strategy.isAllowed)

  override def close(): F[Unit] = monad.ifA(monad.pure(closeClient))(
    monad.eval(connection.close()).flatMap(_ => monad.eval(client.shutdown())),
    monad.eval(connection.close())
  )

  override protected def monadError: MonadError[F] = monad
}
