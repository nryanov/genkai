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

  override private[genkai] def permissions[A: Key](key: A, instant: Instant): F[Long] = {
    val keyStr = strategy.keys(key, instant)
    val args = strategy.permissionsArgs(instant)

    monad
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
    monad.eval(syncCommands.unlink(keyStr: _*)).void

  }

  override def acquireS[A: Key](key: A, instant: Instant, cost: Long): F[RateLimiter.State] = {
    val keyStr = strategy.keys(key, instant)
    val args = strategy.acquireArgs(instant, cost)

    monad
      .eval(
        syncCommands.evalsha[Any](
          acquireSha,
          ScriptOutputType.MULTI,
          keyStr.toArray,
          args: _*
        )
      )
      .map(tokens => strategy.toState(tokens, instant, Key[A].convert(key)))
  }

  override def close(): F[Unit] =
    monad.ifM(monad.pure(closeClient))(
      monad.eval(connection.close()) *>
        monad.eval(client.shutdown()),
      monad.eval(connection.close())
    )

  override def monadError: MonadError[F] = monad
}
