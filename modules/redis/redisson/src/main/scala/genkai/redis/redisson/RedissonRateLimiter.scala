package genkai.redis.redisson

import java.time.Instant
import java.util.Collections

import genkai.monad.syntax._
import genkai.{Key, RateLimiter}
import genkai.monad.MonadError
import genkai.redis.RedisStrategy
import org.redisson.api.{RScript, RedissonClient}

abstract class RedissonRateLimiter[F[_]](
  client: RedissonClient,
  implicit val monad: MonadError[F],
  strategy: RedisStrategy,
  closeClient: Boolean,
  acquireSha: String,
  permissionsSha: String
) extends RateLimiter[F] {
  override def permissions[A: Key](key: A): F[Long] = {
    val now = Instant.now()

    monad
      .eval(
        evalSha(
          permissionsSha,
          Collections.singletonList(strategy.key(key, now)),
          strategy.args(now)
        )
      )
      .map(strategy.toPermissions)
  }

  override def reset[A: Key](key: A): F[Unit] = {
    val now = Instant.now()
    monad.eval(client.getKeys.unlink(strategy.key(key, now))).void
  }

  override def acquire[A: Key](key: A, instant: Instant): F[Boolean] =
    monad
      .eval(
        evalSha(
          acquireSha,
          Collections.singletonList(strategy.key(key, instant)),
          strategy.argsWithTtl(instant)
        )
      )
      .map(strategy.isAllowed)

  override def close(): F[Unit] = monad.ifA(monad.pure(closeClient))(
    monad.eval(client.shutdown()),
    monad.unit
  )

  override protected def monadError: MonadError[F] = monad

  private def evalSha(sha: String, keys: java.util.List[Object], args: Object*): Long =
    client.getScript.evalSha[Long](
      RScript.Mode.READ_WRITE,
      sha,
      RScript.ReturnType.INTEGER,
      keys,
      args
    )
}
