package genkai.redis.jedis

import genkai.{Id, RateLimiter, Strategy}

import scala.concurrent.Future

class JedisSyncRateLimiterSpec extends JedisRateLimiterSpec[Id] {
  override def rateLimiter(strategy: Strategy): RateLimiter[Id] =
    JedisSyncRateLimiter(jedisPool, strategy)

  override def toFuture[A](v: Id[A]): Future[A] = Future.successful(v)
}
