package genkai.redis.lettuce

import genkai.{ConcurrentRateLimiter, ConcurrentStrategy, Identity}

import scala.concurrent.Future

class LettuceSyncConcurrentRateLimiterSpec extends LettuceConcurrentRateLimiterSpec[Identity] {
  override def concurrentRateLimiter(
    strategy: ConcurrentStrategy
  ): ConcurrentRateLimiter[Identity] =
    LettuceSyncConcurrentRateLimiter(redisClient, strategy)

  override def toFuture[A](v: Identity[A]): Future[A] = Future.successful(v)
}
