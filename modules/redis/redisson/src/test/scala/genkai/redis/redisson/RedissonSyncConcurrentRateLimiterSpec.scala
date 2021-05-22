package genkai.redis.redisson

import genkai.{ConcurrentRateLimiter, ConcurrentStrategy, Identity}

import scala.concurrent.Future

class RedissonSyncConcurrentRateLimiterSpec extends RedissonConcurrentRateLimiterSpec[Identity] {
  override def concurrentRateLimiter(
    strategy: ConcurrentStrategy
  ): ConcurrentRateLimiter[Identity] =
    RedissonSyncConcurrentRateLimiter(redisClient, strategy)

  override def toFuture[A](v: Identity[A]): Future[A] = Future.successful(v)
}
