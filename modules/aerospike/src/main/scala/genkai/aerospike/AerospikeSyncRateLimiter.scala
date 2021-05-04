package genkai.aerospike

import com.aerospike.client.policy.ClientPolicy
import genkai.monad.syntax._
import genkai.monad.IdMonadError
import genkai.{Identity, Strategy}
import com.aerospike.client.{AerospikeClient, Language}

import scala.concurrent.duration._

class AerospikeSyncRateLimiter private (
  client: AerospikeClient,
  namespace: String,
  strategy: AerospikeStrategy,
  closeClient: Boolean
) extends AerospikeRateLimiter[Identity](client, namespace, IdMonadError, strategy, closeClient)

object AerospikeSyncRateLimiter {
  def apply(
    client: AerospikeClient,
    namespace: String,
    strategy: Strategy,
    sleepInterval: Duration = 1000 millis,
    timeout: Duration = 10000 millis
  ): AerospikeSyncRateLimiter = {
    implicit val monad = IdMonadError
    val aerospikeStrategy = AerospikeStrategy(strategy)

    monad
      .eval(
        client.registerUdfString(
          null, // use default configured policy
          aerospikeStrategy.luaScript,
          aerospikeStrategy.serverPath,
          Language.LUA
        )
      )
      .map(_.waitTillComplete(sleepInterval.toMillis.toInt, timeout.toMillis.toInt))

    new AerospikeSyncRateLimiter(client, namespace, aerospikeStrategy, false)
  }

  def create(
    host: String,
    port: Int,
    policy: ClientPolicy = new ClientPolicy(),
    namespace: String,
    strategy: Strategy,
    sleepInterval: Duration = 1000 millis,
    timeout: Duration = 10000 millis
  ): AerospikeSyncRateLimiter = {
    implicit val monad = IdMonadError
    val aerospikeStrategy = AerospikeStrategy(strategy)

    monad
      .eval(new AerospikeClient(policy, host, port))
      .tap { client =>
        monad
          .eval(
            client.registerUdfString(
              null, // use default configured policy
              aerospikeStrategy.luaScript,
              aerospikeStrategy.serverPath,
              Language.LUA
            )
          )
          .map(_.waitTillComplete(sleepInterval.toMillis.toInt, timeout.toMillis.toInt))
      }
      .map(client => new AerospikeSyncRateLimiter(client, namespace, aerospikeStrategy, true))
  }
}
