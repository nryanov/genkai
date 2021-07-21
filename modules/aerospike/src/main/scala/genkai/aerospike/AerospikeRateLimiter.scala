package genkai.aerospike

import java.time.Instant

import genkai.monad.syntax._
import genkai.monad.MonadError
import genkai.{Key, RateLimiter}
import com.aerospike.client.AerospikeClient
import com.aerospike.client.policy.WritePolicy

abstract class AerospikeRateLimiter[F[_]](
  client: AerospikeClient,
  namespace: String,
  implicit val monad: MonadError[F],
  strategy: AerospikeStrategy,
  closeClient: Boolean
) extends RateLimiter[F] {

  private val writePolicy = new WritePolicy(client.writePolicyDefault)
  writePolicy.expiration = strategy.expiration

  override private[genkai] def permissions[A: Key](key: A, instant: Instant): F[Long] = {
    val keyStr = strategy.key(namespace, key, instant)
    val args = strategy.permissionsArgs(instant)

    monad
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
    monad.eval(client.delete(writePolicy, keyStr)).void
  }

  override private[genkai] def acquire[A: Key](key: A, instant: Instant, cost: Long): F[Boolean] = {
    val keyStr = strategy.key(namespace, key, instant)
    val args = strategy.acquireArgs(instant, cost)

    monad
      .eval(
        client
          .execute(writePolicy, keyStr, strategy.packageName, strategy.acquireFunction, args: _*)
      )
      .map(tokens => strategy.isAllowed(tokens.asInstanceOf[Long]))
  }

  override def close(): F[Unit] = monad.whenA(closeClient)(monad.eval(client.close()))

  override def monadError: MonadError[F] = monad
}
