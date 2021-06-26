package genkai.redis.redisson

import genkai.{Id, RateLimiter, Strategy}

import scala.concurrent.Future

class RedissonSyncRateLimiterSpec extends RedissonRateLimiterSpec[Id] {
  override def rateLimiter(strategy: Strategy): RateLimiter[Id] =
    RedissonSyncRateLimiter(redisClient, strategy)

  override def toFuture[A](v: Id[A]): Future[A] = Future.successful(v)
}
