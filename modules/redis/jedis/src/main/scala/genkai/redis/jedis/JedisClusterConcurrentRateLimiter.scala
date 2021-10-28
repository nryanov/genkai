package genkai.redis.jedis

import java.time.Instant

import redis.clients.jedis.JedisCluster
import genkai.monad.syntax._
import genkai.monad.MonadError
import genkai.redis.RedisConcurrentStrategy
import genkai.{ConcurrentLimitExhausted, ConcurrentRateLimiter, Key}

abstract class JedisClusterConcurrentRateLimiter[F[_]](
  cluster: JedisCluster,
  implicit val monad: MonadError[F],
  strategy: RedisConcurrentStrategy,
  closeClient: Boolean,
  acquireSha: String,
  releaseSha: String,
  permissionsSha: String
) extends ConcurrentRateLimiter[F] {

  override private[genkai] def use[A: Key, B](key: A, instant: Instant)(
    f: => F[B]
  ): F[Either[ConcurrentLimitExhausted[A], B]] =
    monad.bracket(acquire(key, instant)) { acquired =>
      monad.ifM(monad.pure(acquired))(
        ifTrue = monad.suspend(f).map[Either[ConcurrentLimitExhausted[A], B]](r => Right(r)),
        ifFalse = monad.pure[Either[ConcurrentLimitExhausted[A], B]](Left(ConcurrentLimitExhausted(key)))
      )
    }(acquired => monad.whenA(acquired)(release(key, instant).void))

  override def reset[A: Key](key: A): F[Unit] = {
    val now = Instant.now()
    val keyStr = strategy.keys(key, now)
    monad.eval(cluster.unlink(keyStr: _*))
  }

  override private[genkai] def acquire[A: Key](key: A, instant: Instant): F[Boolean] = {
    val keys = strategy.keys(key, instant)
    val args = keys ::: strategy.acquireArgs(instant)

    for {
      tokens <- monad.eval(cluster.evalsha(acquireSha, keys.size, args: _*))
    } yield strategy.isAllowed(tokens.toString.toLong)
  }

  override private[genkai] def release[A: Key](key: A, instant: Instant): F[Boolean] = {
    val keys = strategy.keys(key, instant)
    val args = keys ::: strategy.releaseArgs(instant)

    for {
      tokens <- monad.eval(cluster.evalsha(releaseSha, keys.size, args: _*))
    } yield strategy.isReleased(tokens.toString.toLong)
  }

  override private[genkai] def permissions[A: Key](key: A, instant: Instant): F[Long] = {
    val keys = strategy.keys(key, instant)
    val args = keys ::: strategy.permissionsArgs(instant)

    for {
      tokens <- monad.eval(cluster.evalsha(permissionsSha, keys.size, args: _*))
    } yield strategy.toPermissions(tokens.toString.toLong)
  }

  override def close(): F[Unit] = monad.whenA(closeClient)(monad.eval(cluster.close()))

  override def monadError: MonadError[F] = monad
}
