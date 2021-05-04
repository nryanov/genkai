package genkai.aerospike

import com.aerospike.client.AerospikeClient
import genkai.{Identity, RateLimiter, Strategy}

import scala.concurrent.Future

class AerospikeSyncRateLimiterSpec extends AerospikeSpecForAll[Identity] {
  var aerospikeClient: AerospikeClient = _
  var aerospikeStrategy: AerospikeStrategy = _

  override def afterContainersStart(aerospike: AerospikeContainer): Unit =
    aerospikeClient = new AerospikeClient(aerospike.containerIpAddress, aerospike.mappedPort(3000))

  override protected def afterAll(): Unit = {
    aerospikeClient.close()
    super.afterAll()
  }

  override protected def afterEach(): Unit = {
    super.afterEach()
    aerospikeClient.truncate(null, "test", null, null)
  }

  override def rateLimiter(strategy: Strategy): RateLimiter[Identity] = {
    aerospikeStrategy = AerospikeStrategy(strategy)
    AerospikeSyncRateLimiter(aerospikeClient, "test", strategy)
  }

  override def toFuture[A](v: Identity[A]): Future[A] = Future.successful(v)
}
