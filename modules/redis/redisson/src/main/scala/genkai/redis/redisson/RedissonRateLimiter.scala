package genkai.redis.redisson

import java.time.Instant
import java.util.Collections

import genkai.monad.syntax._
import genkai.{Key, Logging, RateLimiter}
import genkai.monad.MonadError
import genkai.redis.RedisStrategy
import org.redisson.api.{RScript, RedissonClient}
import org.redisson.client.codec.StringCodec

abstract class RedissonRateLimiter[F[_]](
  client: RedissonClient,
  implicit val monad: MonadError[F],
  strategy: RedisStrategy,
  closeClient: Boolean,
  acquireSha: String,
  permissionsSha: String
) extends RateLimiter[F]
    with Logging[F] {
  /* to avoid unnecessary memory allocations */
  private val scriptCommand: RScript = client.getScript(new StringCodec)

  override def permissions[A: Key](key: A): F[Long] = {
    val now = Instant.now()

    val keyStr = strategy.key(key, now)
    val args = strategy.permissionsArgs(now)

    debug(s"Permissions request ($keyStr): $args") *>
      monad
        .eval(
          evalSha(
            permissionsSha,
            Collections.singletonList(keyStr),
            args
          )
        )
        .map(strategy.toPermissions)
  }

  override def reset[A: Key](key: A): F[Unit] = {
    val now = Instant.now()
    val keyStr = strategy.key(key, now)
    debug(s"Reset limits for: $keyStr") *>
      monad.eval(client.getKeys.unlink(keyStr)).void
  }

  override def acquire[A: Key](key: A, instant: Instant, cost: Long): F[Boolean] = {
    val keyStr = strategy.key(key, instant)
    val args = strategy.acquireArgs(instant, cost)

    debug(s"Acquire request ($keyStr): $args") *>
      monad
        .eval(
          evalSha(
            acquireSha,
            Collections.singletonList(keyStr),
            args
          )
        )
        .map(strategy.isAllowed)
  }

  override def close(): F[Unit] =
    monad.ifA(monad.pure(closeClient))(
      monad.eval(client.shutdown()),
      monad.unit
    )

  override protected def monadError: MonadError[F] = monad

  private def evalSha(sha: String, keys: java.util.List[Object], args: Seq[String]): Long =
    scriptCommand.evalSha[Long](
      RScript.Mode.READ_WRITE,
      sha,
      RScript.ReturnType.INTEGER,
      keys,
      args: _*
    )
}
