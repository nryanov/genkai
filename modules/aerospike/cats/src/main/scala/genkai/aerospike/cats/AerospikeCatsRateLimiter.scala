package genkai.aerospike.cats

import cats.effect.{Blocker, ContextShift, Resource, Sync}
import com.aerospike.client.policy.ClientPolicy
import com.aerospike.client.{AerospikeClient, Language}
import genkai.Strategy
import genkai.monad.syntax._
import genkai.effect.cats.CatsMonadError
import genkai.aerospike.{AerospikeRateLimiter, AerospikeStrategy}

import scala.concurrent.duration._

class AerospikeCatsRateLimiter[F[_]: Sync: ContextShift] private (
  client: AerospikeClient,
  namespace: String,
  monad: CatsMonadError[F],
  strategy: AerospikeStrategy,
  closeClient: Boolean
) extends AerospikeRateLimiter[F](
      client,
      namespace,
      monad,
      strategy,
      closeClient
    )

object AerospikeCatsRateLimiter {
  def useClient[F[_]: Sync: ContextShift](
    client: AerospikeClient,
    namespace: String,
    strategy: Strategy,
    blocker: Blocker,
    sleepInterval: Duration = 1000 millis,
    timeout: Duration = 10000 millis
  ): F[AerospikeCatsRateLimiter[F]] = {
    implicit val monad: CatsMonadError[F] = new CatsMonadError[F](blocker)

    val aerospikeStrategy = AerospikeStrategy(strategy)

    for {
      task <- monad.eval(
        client.registerUdfString(
          null, // use default policy
          aerospikeStrategy.luaScript,
          aerospikeStrategy.serverPath,
          Language.LUA
        )
      )
      _ <- blocker.blockOn(
        monad.eval(task.waitTillComplete(sleepInterval.toMillis.toInt, timeout.toMillis.toInt))
      )
    } yield new AerospikeCatsRateLimiter(
      client = client,
      namespace = namespace,
      monad = monad,
      strategy = aerospikeStrategy,
      closeClient = false
    )
  }

  def resource[F[_]: Sync: ContextShift](
    host: String,
    port: Int,
    policy: ClientPolicy = new ClientPolicy(),
    namespace: String,
    strategy: Strategy,
    blocker: Blocker,
    sleepInterval: Duration = 1000 millis,
    timeout: Duration = 10000 millis
  ): Resource[F, AerospikeCatsRateLimiter[F]] = {
    implicit val monad: CatsMonadError[F] = new CatsMonadError[F](blocker)

    val aerospikeStrategy = AerospikeStrategy(strategy)

    Resource.make {
      for {
        client <- monad.eval(new AerospikeClient(policy, host, port))
        task <- monad.eval(
          client.registerUdfString(
            null, // use default policy
            aerospikeStrategy.luaScript,
            aerospikeStrategy.serverPath,
            Language.LUA
          )
        )
        _ <- blocker.blockOn(
          monad.eval(task.waitTillComplete(sleepInterval.toMillis.toInt, timeout.toMillis.toInt))
        )
      } yield new AerospikeCatsRateLimiter(
        client = client,
        namespace = namespace,
        monad = monad,
        strategy = aerospikeStrategy,
        closeClient = true
      )
    }(_.close())
  }
}
