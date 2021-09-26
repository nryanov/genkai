package genkai.aerospike

import java.time.Duration

import com.dimafeng.testcontainers.GenericContainer
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.wait.strategy.{WaitStrategy, WaitStrategyTarget}

class AerospikeContainer(underlying: GenericContainer) extends GenericContainer(underlying)

object AerospikeContainer {
  // use custom wait strategy (fix it?)
  private val waitStrategy = new WaitStrategy {
    override def waitUntilReady(waitStrategyTarget: WaitStrategyTarget): Unit = Thread.sleep(5000)

    override def withStartupTimeout(startupTimeout: Duration): WaitStrategy = this
  }

  case class Def()
      extends GenericContainer.Def[AerospikeContainer](
        new AerospikeContainer(
          GenericContainer(
            dockerImage = "aerospike:ce-5.4.0.11",
            exposedPorts = Seq(3000, 3001, 3002),
            command = Seq("--config-file", "/opt/aerospike/etc/aerospike.conf"),
            classpathResourceMapping = Seq(("aerospike.conf", "/opt/aerospike/etc/aerospike.conf", BindMode.READ_ONLY)),
            waitStrategy = waitStrategy
          )
        )
      )
}
