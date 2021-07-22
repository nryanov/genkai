package genkai.aerospike.zio

import com.aerospike.client.{AerospikeClient, Language}
import com.aerospike.client.policy.ClientPolicy
import genkai.Strategy
import genkai.aerospike.{AerospikeRateLimiter, AerospikeStrategy}
import genkai.effect.zio.ZioBlockingMonadError
import zio.blocking.{Blocking, blocking}
import zio.{Has, Task, ZIO, ZLayer, ZManaged}

import scala.concurrent.duration._

class AerospikeZioRateLimiter private (
  client: AerospikeClient,
  namespace: String,
  monad: ZioBlockingMonadError,
  strategy: AerospikeStrategy,
  closeClient: Boolean
) extends AerospikeRateLimiter[Task](
      client,
      namespace,
      monad,
      strategy,
      closeClient
    )

object AerospikeZioRateLimiter {
  def useClient(
    client: AerospikeClient,
    namespace: String,
    strategy: Strategy,
    sleepInterval: Duration = 1000 millis,
    timeout: Duration = 10000 millis
  ): ZIO[Blocking, Throwable, AerospikeZioRateLimiter] = for {
    blocker <- ZIO.service[Blocking.Service]
    monad = new ZioBlockingMonadError(blocker)
    aerospikeStrategy = AerospikeStrategy(strategy)
    task <- monad.eval(
      client.registerUdfString(
        null, // use default policy
        aerospikeStrategy.luaScript,
        aerospikeStrategy.serverPath,
        Language.LUA
      )
    )
    _ <- blocker.blocking(
      monad.eval(task.waitTillComplete(sleepInterval.toMillis.toInt, timeout.toMillis.toInt))
    )
  } yield new AerospikeZioRateLimiter(
    client = client,
    namespace = namespace,
    monad = monad,
    strategy = aerospikeStrategy,
    closeClient = false
  )

  def layerUsingClient(
    client: AerospikeClient,
    namespace: String,
    strategy: Strategy,
    sleepInterval: Duration = 1000 millis,
    timeout: Duration = 10000 millis
  ): ZLayer[Blocking, Throwable, Has[AerospikeZioRateLimiter]] =
    useClient(
      client,
      namespace,
      strategy,
      sleepInterval,
      timeout
    ).toLayer

  def managed(
    host: String,
    port: Int,
    policy: ClientPolicy = new ClientPolicy(),
    namespace: String,
    strategy: Strategy,
    sleepInterval: Duration = 1000 millis,
    timeout: Duration = 10000 millis
  ): ZManaged[Blocking, Throwable, AerospikeZioRateLimiter] =
    ZManaged.make {
      for {
        blocker <- ZIO.service[Blocking.Service]
        monad = new ZioBlockingMonadError(blocker)
        client <- monad.eval(new AerospikeClient(policy, host, port))
        aerospikeStrategy = AerospikeStrategy(strategy)
        task <- monad.eval(
          client.registerUdfString(
            null, // use default policy
            aerospikeStrategy.luaScript,
            aerospikeStrategy.serverPath,
            Language.LUA
          )
        )
        _ <- blocker.blocking(
          monad.eval(task.waitTillComplete(sleepInterval.toMillis.toInt, timeout.toMillis.toInt))
        )
      } yield new AerospikeZioRateLimiter(
        client = client,
        namespace = namespace,
        monad = monad,
        strategy = aerospikeStrategy,
        closeClient = true
      )
    } { limiter =>
      blocking(limiter.close().ignore)
    }

  def layerFromManaged(
    host: String,
    port: Int,
    policy: ClientPolicy = new ClientPolicy(),
    namespace: String,
    strategy: Strategy,
    sleepInterval: Duration = 1000 millis,
    timeout: Duration = 10000 millis
  ): ZLayer[Blocking, Throwable, Has[AerospikeZioRateLimiter]] =
    ZLayer.fromManaged(
      managed(
        host,
        port,
        policy,
        namespace,
        strategy,
        sleepInterval,
        timeout
      )
    )
}
