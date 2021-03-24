package genkai.redis

import com.dimafeng.testcontainers.GenericContainer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy

class RedisContainer(underlying: GenericContainer) extends GenericContainer(underlying) {}

object RedisContainer {
  val waitStrategy = new HostPortWaitStrategy()

  case class Def()
      extends GenericContainer.Def[RedisContainer](
        new RedisContainer(
          GenericContainer(
            dockerImage = "redis:6.2",
            exposedPorts = Seq(6379),
            waitStrategy = waitStrategy
          )
        )
      )
}
