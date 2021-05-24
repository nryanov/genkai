package genkai.redis.jedis

import genkai.{Identity, RateLimiter, Strategy}

import scala.concurrent.Future

class JedisSyncRateLimiterSpec extends JedisRateLimiterSpec[Identity] {
  override def rateLimiter(strategy: Strategy): RateLimiter[Identity] =
    JedisSyncRateLimiter(jedisPool, strategy)

  override def toFuture[A](v: Identity[A]): Future[A] = Future.successful(v)
}
