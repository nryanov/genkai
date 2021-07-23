package genkai.aerospike.cats3

import cats.effect.{Resource, Sync}
import com.aerospike.client.{AerospikeClient, Language}
import com.aerospike.client.policy.ClientPolicy
import genkai.Strategy
import genkai.monad.syntax._
import genkai.aerospike.{AerospikeRateLimiter, AerospikeStrategy}
import genkai.effect.cats3.Cats3BlockingMonadError

import scala.concurrent.duration._

class AerospikeCats3RateLimiter[F[_]: Sync] private (
  client: AerospikeClient,
  namespace: String,
  monad: Cats3BlockingMonadError[F],
  strategy: AerospikeStrategy,
  closeClient: Boolean
) extends AerospikeRateLimiter[F](
      client,
      namespace,
      monad,
      strategy,
      closeClient
    )

object AerospikeCats3RateLimiter {
  def useClient[F[_]: Sync](
    client: AerospikeClient,
    namespace: String,
    strategy: Strategy,
    sleepInterval: Duration = 1000 millis,
    timeout: Duration = 10000 millis
  ): F[AerospikeCats3RateLimiter[F]] = {
    implicit val monad: Cats3BlockingMonadError[F] = new Cats3BlockingMonadError[F]()

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
      _ <- monad.eval(task.waitTillComplete(sleepInterval.toMillis.toInt, timeout.toMillis.toInt))

    } yield new AerospikeCats3RateLimiter(
      client = client,
      namespace = namespace,
      monad = monad,
      strategy = aerospikeStrategy,
      closeClient = false
    )
  }

  def resource[F[_]: Sync](
    host: String,
    port: Int,
    policy: ClientPolicy = new ClientPolicy(),
    namespace: String,
    strategy: Strategy,
    sleepInterval: Duration = 1000 millis,
    timeout: Duration = 10000 millis
  ): Resource[F, AerospikeCats3RateLimiter[F]] = {
    implicit val monad: Cats3BlockingMonadError[F] = new Cats3BlockingMonadError[F]()

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
        _ <- monad.eval(task.waitTillComplete(sleepInterval.toMillis.toInt, timeout.toMillis.toInt))
      } yield new AerospikeCats3RateLimiter(
        client = client,
        namespace = namespace,
        monad = monad,
        strategy = aerospikeStrategy,
        closeClient = true
      )
    }(_.close())
  }
}
