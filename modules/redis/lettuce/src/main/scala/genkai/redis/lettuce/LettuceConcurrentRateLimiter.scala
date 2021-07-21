package genkai.redis.lettuce

import java.time.Instant

import genkai.monad.syntax._
import genkai.{ConcurrentLimitExhausted, ConcurrentRateLimiter, Key}
import genkai.monad.MonadError
import genkai.redis.RedisConcurrentStrategy
import io.lettuce.core.{RedisClient, ScriptOutputType}
import io.lettuce.core.api.StatefulRedisConnection

abstract class LettuceConcurrentRateLimiter[F[_]](
  client: RedisClient,
  connection: StatefulRedisConnection[String, String],
  implicit val monad: MonadError[F],
  strategy: RedisConcurrentStrategy,
  closeClient: Boolean,
  acquireSha: String,
  releaseSha: String,
  permissionsSha: String
) extends ConcurrentRateLimiter[F] {

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

  override private[genkai] def use[A: Key, B](key: A, instant: Instant)(
    f: => F[B]
  ): F[Either[ConcurrentLimitExhausted[A], B]] =
    monad.bracket(acquire(key, instant)) { acquired =>
      monad.ifM(monad.pure(acquired))(
        ifTrue = monad.suspend(f).map[Either[ConcurrentLimitExhausted[A], B]](r => Right(r)),
        ifFalse =
          monad.pure[Either[ConcurrentLimitExhausted[A], B]](Left(ConcurrentLimitExhausted(key)))
      )
    }(acquired => monad.whenA(acquired)(release(key, instant).void))

  override private[genkai] def release[A: Key](key: A, instant: Instant): F[Boolean] = {
    val keyStr = strategy.keys(key, instant)
    val args = strategy.releaseArgs(instant)

    monad
      .eval(
        syncCommands.evalsha[Long](
          releaseSha,
          ScriptOutputType.INTEGER,
          keyStr.toArray,
          args: _*
        )
      )
      .map(tokens => strategy.isReleased(tokens))
  }

  override private[genkai] def acquire[A: Key](key: A, instant: Instant): F[Boolean] = {
    val keyStr = strategy.keys(key, instant)
    val args = strategy.acquireArgs(instant)

    monad
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
