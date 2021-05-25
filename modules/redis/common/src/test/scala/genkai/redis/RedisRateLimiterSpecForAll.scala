package genkai.redis

import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import genkai.RateLimiterBaseSpec

trait RedisRateLimiterSpecForAll[F[_]] extends RateLimiterBaseSpec[F] with TestContainerForAll {
  override val containerDef: RedisContainer.Def = RedisContainer.Def()
}
