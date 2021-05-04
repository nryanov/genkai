package genkai.aerospike

import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import genkai.BaseSpec

trait AerospikeSpecForAll[F[_]] extends BaseSpec[F] with TestContainerForAll {
  override val containerDef: AerospikeContainer.Def = AerospikeContainer.Def()
}
