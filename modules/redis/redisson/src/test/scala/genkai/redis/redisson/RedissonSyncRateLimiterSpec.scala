package genkai.redis.redisson

import genkai.{Identity, RateLimiter, Strategy}

import scala.concurrent.Future

class RedissonSyncRateLimiterSpec extends RedissonRateLimiterSpec[Identity] {
  override def rateLimiter(strategy: Strategy): RateLimiter[Identity] =
    RedissonSyncRateLimiter(redisClient, strategy)

  override def toFuture[A](v: Identity[A]): Future[A] = Future.successful(v)
}
