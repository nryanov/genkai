package genkai.redis.jedis

import genkai.redis.{RedisContainer, RedisSpecForAll}
import redis.clients.jedis.JedisPool

trait JedisSpec[F[_]] extends RedisSpecForAll[F] {
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
