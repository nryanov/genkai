package genkai.redis.lettuce

import genkai.redis.{RedisContainer, RedisRateLimiterSpecForAll}
import io.lettuce.core.RedisClient
import io.lettuce.core.resource.DefaultClientResources

trait LettuceRateLimiterSpec[F[_]] extends RedisRateLimiterSpecForAll[F] {
  var redisClient: RedisClient = _

  override def afterContainersStart(redis: RedisContainer): Unit = {
    val clientResources =
      DefaultClientResources.builder().ioThreadPoolSize(2).computationThreadPoolSize(2).build()
    redisClient = RedisClient.create(
      clientResources,
      s"redis://${redis.containerIpAddress}:${redis.mappedPort(6379)}"
    )
  }

  override protected def afterAll(): Unit = {
    redisClient.shutdown()
    super.afterAll()
  }

  override protected def afterEach(): Unit = {
    super.afterEach()
    val connection = redisClient.connect()
    try connection.sync().flushall()
    finally connection.close()
  }
}
