package genkai.redis.jedis

import genkai.redis.{RedisConcurrentRateLimiterSpecForAll, RedisContainer}
import redis.clients.jedis.JedisPool

trait JedisConcurrentRateLimiterSpec[F[_]] extends RedisConcurrentRateLimiterSpecForAll[F] {
  var jedisPool: JedisPool = _

  override def afterContainersStart(redis: RedisContainer): Unit =
    jedisPool = new JedisPool(redis.containerIpAddress, redis.mappedPort(6379))

  override protected def afterAll(): Unit = {
    jedisPool.close()
    super.afterAll()
  }

  override protected def afterEach(): Unit = {
    super.afterEach()
    val jedis = jedisPool.getResource
    try jedis.flushAll()
    finally jedis.close()
  }
}
