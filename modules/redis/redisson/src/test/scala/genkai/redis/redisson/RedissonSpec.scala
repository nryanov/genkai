package genkai.redis.redisson

import genkai.redis.{RedisContainer, RedisSpecForAll}
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config

trait RedissonSpec[F[_]] extends RedisSpecForAll[F] {
  var redisClient: RedissonClient = _

  override def afterContainersStart(redis: RedisContainer): Unit = {
    val config = new Config()
    config
      .useSingleServer()
      .setTimeout(1000000)
      .setAddress(s"redis://${redis.containerIpAddress}:${redis.mappedPort(6379)}")

    redisClient = Redisson.create(config)
  }

  override def afterAll(): Unit = {
    redisClient.shutdown()
    super.afterAll()
  }

  override def afterEach(context: AfterEach): Unit = {
    super.afterEach(context)

    redisClient.getKeys.flushall()
  }
}
