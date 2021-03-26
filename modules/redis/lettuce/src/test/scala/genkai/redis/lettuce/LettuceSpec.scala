package genkai.redis.lettuce

import genkai.redis.{RedisContainer, RedisSpecForAll}
import io.lettuce.core.RedisClient

trait LettuceSpec[F[_]] extends RedisSpecForAll[F] {
  var redisClient: RedisClient = _

  override def afterContainersStart(redis: RedisContainer): Unit =
    redisClient =
      RedisClient.create(s"redis://${redis.containerIpAddress}:${redis.mappedPort(6379)}")

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
