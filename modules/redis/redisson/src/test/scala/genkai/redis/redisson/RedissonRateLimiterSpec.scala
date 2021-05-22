package genkai.redis.redisson

import genkai.redis.{RedisContainer, RedisRateLimiterSpecForAll}
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config

trait RedissonRateLimiterSpec[F[_]] extends RedisRateLimiterSpecForAll[F] {
  var redisClient: RedissonClient = _

  override def afterContainersStart(redis: RedisContainer): Unit = {
    val config = new Config()
    config
      .useSingleServer()
      .setTimeout(1000000)
      .setConnectionMinimumIdleSize(1)
      .setConnectionPoolSize(2)
      .setAddress(s"redis://${redis.containerIpAddress}:${redis.mappedPort(6379)}")

    redisClient = Redisson.create(config)
  }

  override protected def afterAll(): Unit = {
    redisClient.shutdown()
    super.afterAll()
  }

  override protected def afterEach(): Unit = {
    super.afterEach()
    redisClient.getKeys.flushall()
  }
}
