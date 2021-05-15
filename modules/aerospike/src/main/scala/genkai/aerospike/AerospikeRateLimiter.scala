package genkai.aerospike

import java.time.Instant

import genkai.monad.syntax._
import genkai.monad.MonadError
import genkai.{Key, Logging, RateLimiter}
import com.aerospike.client.AerospikeClient
import com.aerospike.client.policy.WritePolicy

abstract class AerospikeRateLimiter[F[_]](
  client: AerospikeClient,
  namespace: String,
  implicit val monad: MonadError[F],
  strategy: AerospikeStrategy,
  closeClient: Boolean
) extends RateLimiter[F]
    with Logging[F] {

  private val writePolicy = new WritePolicy(client.writePolicyDefault)
  writePolicy.expiration = strategy.expiration

  override def permissions[A: Key](key: A): F[Long] = {
    val now = Instant.now()
    val keyStr = strategy.key(namespace, key, now)
    val args = strategy.permissionsArgs(now)

    debug(s"Permissions request ($keyStr): $args") *> monad
      .eval(
        client.execute(
          writePolicy,
          keyStr,
          strategy.packageName,
          strategy.permissionsFunction,
          args: _*
        )
      )
      .map(tokens => strategy.toPermissions(tokens.asInstanceOf[Long]))
  }

  override def reset[A: Key](key: A): F[Unit] = {
    val now = Instant.now()
    val keyStr = strategy.key(namespace, key, now)
    debug(s"Reset limits for: $keyStr") *> monad.eval(client.delete(writePolicy, keyStr)).void
  }

  override def acquire[A: Key](key: A, instant: Instant, cost: Long): F[Boolean] = {
    val keyStr = strategy.key(namespace, key, instant)
    val args = strategy.acquireArgs(instant, cost)

    debug(s"Acquire request ($keyStr): $args") *> monad
      .eval(
        client
          .execute(writePolicy, keyStr, strategy.packageName, strategy.acquireFunction, args: _*)
      )
      .map(tokens => strategy.isAllowed(tokens.asInstanceOf[Long]))
  }

  override def close(): F[Unit] = monad.whenA(closeClient)(monad.eval(client.close()))

  override protected def monadError: MonadError[F] = monad
}
