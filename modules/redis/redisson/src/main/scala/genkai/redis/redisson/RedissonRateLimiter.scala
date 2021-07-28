package genkai.redis.redisson

import java.time.Instant

import genkai.monad.syntax._
import genkai.{Key, RateLimiter}
import genkai.monad.MonadError
import genkai.redis.RedisStrategy
import org.redisson.api.{RScript, RedissonClient}
import org.redisson.client.codec.StringCodec

import scala.collection.JavaConverters._

abstract class RedissonRateLimiter[F[_]](
  client: RedissonClient,
  implicit val monad: MonadError[F],
  strategy: RedisStrategy,
  closeClient: Boolean,
  acquireSha: String,
  permissionsSha: String
) extends RateLimiter[F] {
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

  override def acquireS[A: Key](key: A, instant: Instant, cost: Long): F[RateLimiter.State] = {
    val keyStr = strategy.keys(key, instant)
    val args = strategy.acquireArgs(instant, cost)

    monad
      .eval(
        evalSha(
          acquireSha,
          new java.util.LinkedList[Object](keyStr.asJava),
          args
        )
      )
      .map(strategy.toState(_, instant, Key[A].convert(key)))
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
      RScript.ReturnType.MULTI,
      keys,
      args: _*
    )
}
