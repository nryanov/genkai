package genkai.redis

import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import genkai.ConcurrentRateLimiterBaseSpec

trait RedisConcurrentRateLimiterSpecForAll[F[_]] extends ConcurrentRateLimiterBaseSpec[F] with TestContainerForAll {
  override val containerDef: RedisContainer.Def = RedisContainer.Def()
}
