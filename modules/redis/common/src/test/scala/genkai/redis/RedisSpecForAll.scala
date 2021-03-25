package genkai.redis

import com.dimafeng.testcontainers.munit.TestContainerForAll
import genkai.BaseSpec

trait RedisSpecForAll[F[_]] extends BaseSpec[F] with TestContainerForAll {
  override val containerDef: RedisContainer.Def = RedisContainer.Def()
}
