package genkai.redis.redisson

import java.time.Instant

import genkai.monad.syntax._
import genkai.{ConcurrentLimitExhausted, ConcurrentRateLimiter, Key}
import genkai.monad.MonadError
import genkai.redis.RedisConcurrentStrategy
import org.redisson.api.{RScript, RedissonClient}
import org.redisson.client.codec.StringCodec

import scala.collection.JavaConverters._

abstract class RedissonConcurrentRateLimiter[F[_]](
  client: RedissonClient,
  implicit val monad: MonadError[F],
  strategy: RedisConcurrentStrategy,
  closeClient: Boolean,
  acquireSha: String,
  releaseSha: String,
  permissionsSha: String
) extends ConcurrentRateLimiter[F] {
  /* to avoid unnecessary memory allocations */
  private val scriptCommand: RScript = client.getScript(new StringCodec)

  override private[genkai] def permissions[A: Key](key: A, instant: Instant): F[Long] = {
    val keyStr = strategy.keys(key, instant)
    val args = strategy.permissionsArgs(instant)

    monad
      .eval(
        evalSha(
          permissionsSha,
          new java.util.LinkedList[Object](keyStr.asJava),
          args
        )
      )
      .map(strategy.toPermissions)
  }

  override def reset[A: Key](key: A): F[Unit] = {
    val now = Instant.now()
    val keyStr = strategy.keys(key, now)
    monad.eval(client.getKeys.unlink(keyStr: _*)).void
  }

  override private[genkai] def use[A: Key, B](key: A, instant: Instant)(
    f: => F[B]
  ): F[Either[ConcurrentLimitExhausted[A], B]] =
    monad.bracket(acquire(key, instant)) { acquired =>
      monad.ifM(monad.pure(acquired))(
        ifTrue = monad.suspend(f).map[Either[ConcurrentLimitExhausted[A], B]](r => Right(r)),
        ifFalse = monad.pure[Either[ConcurrentLimitExhausted[A], B]](Left(ConcurrentLimitExhausted(key)))
      )
    }(acquired => monad.whenA(acquired)(release(key, instant).void))

  override private[genkai] def release[A: Key](key: A, instant: Instant): F[Boolean] = {
    val keyStr = strategy.keys(key, instant)
    val args = strategy.releaseArgs(instant)

    monad
      .eval(
        evalSha(
          releaseSha,
          new java.util.LinkedList[Object](keyStr.asJava),
          args
        )
      )
      .map(strategy.isReleased)
  }

  override def acquire[A: Key](key: A, instant: Instant): F[Boolean] = {
    val keyStr = strategy.keys(key, instant)
    val args = strategy.acquireArgs(instant)

    monad
      .eval(
        evalSha(
          acquireSha,
          new java.util.LinkedList[Object](keyStr.asJava),
          args
        )
      )
      .map(strategy.isAllowed)
  }

  override def close(): F[Unit] =
    monad.ifM(monad.pure(closeClient))(
      monad.eval(client.shutdown()),
      monad.unit
    )

  override def monadError: MonadError[F] = monad

  private def evalSha(sha: String, keys: java.util.List[Object], args: Seq[String]): Long =
    scriptCommand.evalSha[Long](
      RScript.Mode.READ_WRITE,
      sha,
      RScript.ReturnType.INTEGER,
      keys,
      args: _*
    )
}
