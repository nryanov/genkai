package genkai.redis.redisson

import genkai.{ConcurrentRateLimiter, ConcurrentStrategy, Id}

import scala.concurrent.Future

class RedissonSyncConcurrentRateLimiterSpec extends RedissonConcurrentRateLimiterSpec[Id] {
  override def concurrentRateLimiter(
    strategy: ConcurrentStrategy
  ): ConcurrentRateLimiter[Id] =
    RedissonSyncConcurrentRateLimiter(redisClient, strategy)

  override def toFuture[A](v: Id[A]): Future[A] = Future.successful(v)
}
