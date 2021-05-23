package genkai.redis.lettuce

import java.time.Instant

import genkai.monad.syntax._
import genkai.{Key, Logging, RateLimiter}
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
) extends RateLimiter[F]
    with Logging[F] {

  private val syncCommands = connection.sync()

  override private[genkai] def permissions[A: Key](key: A, instant: Instant): F[Long] = {
    val keyStr = strategy.keys(key, instant)
    val args = strategy.permissionsArgs(instant)

    debug(s"Permissions request ($keyStr): $args") *> monad
      .eval(
        syncCommands.evalsha[Long](
          permissionsSha,
          ScriptOutputType.INTEGER,
          keyStr.toArray,
          args: _*
        )
      )
      .map(tokens => strategy.toPermissions(tokens))
  }

  override def reset[A: Key](key: A): F[Unit] = {
    val now = Instant.now()
    val keyStr = strategy.keys(key, now)
    debug(s"Reset limits for: $keyStr") *>
      monad.eval(syncCommands.unlink(keyStr: _*)).void

  }

  override def acquire[A: Key](key: A, instant: Instant, cost: Long): F[Boolean] = {
    val keyStr = strategy.keys(key, instant)
    val args = strategy.acquireArgs(instant, cost)

    debug(s"Acquire request ($keyStr): $args") *> monad
      .eval(
        syncCommands.evalsha[Long](
          acquireSha,
          ScriptOutputType.INTEGER,
          keyStr.toArray,
          args: _*
        )
      )
      .map(tokens => strategy.isAllowed(tokens))
  }

  override def close(): F[Unit] =
    monad.ifM(monad.pure(closeClient))(
      monad.eval(connection.close()) *>
        monad.eval(client.shutdown()),
      monad.eval(connection.close())
    )

  override def monadError: MonadError[F] = monad
}
