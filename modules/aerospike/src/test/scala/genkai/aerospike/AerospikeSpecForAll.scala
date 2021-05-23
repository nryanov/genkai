package genkai.aerospike

import com.aerospike.client.AerospikeClient
import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import genkai.RateLimiterBaseSpec

trait AerospikeSpecForAll[F[_]] extends RateLimiterBaseSpec[F] with TestContainerForAll {
  override val containerDef: AerospikeContainer.Def = AerospikeContainer.Def()

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
}
