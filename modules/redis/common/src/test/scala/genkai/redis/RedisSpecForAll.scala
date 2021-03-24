package genkai.redis

import com.dimafeng.testcontainers.munit.TestContainerForAll
import genkai.BaseSpec

trait RedisSpecForAll extends BaseSpec with TestContainerForAll {
  override val containerDef: RedisContainer.Def = RedisContainer.Def()
}
