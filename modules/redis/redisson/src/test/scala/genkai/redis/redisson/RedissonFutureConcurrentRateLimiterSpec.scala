package genkai.redis.redisson

import genkai.{ConcurrentRateLimiter, ConcurrentStrategy}

import scala.concurrent.Future

class RedissonFutureConcurrentRateLimiterSpec extends RedissonConcurrentRateLimiterSpec[Future] {
  override def concurrentRateLimiter(
    strategy: ConcurrentStrategy
  ): ConcurrentRateLimiter[Future] =
    RedissonFutureConcurrentRateLimiter(redisClient, strategy)

  override def toFuture[A](v: Future[A]): Future[A] = v
}
