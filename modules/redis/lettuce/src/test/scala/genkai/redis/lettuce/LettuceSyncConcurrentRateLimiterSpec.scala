package genkai.redis.lettuce

import genkai.{ConcurrentRateLimiter, ConcurrentStrategy, Id}

import scala.concurrent.Future

class LettuceSyncConcurrentRateLimiterSpec extends LettuceConcurrentRateLimiterSpec[Id] {
  override def concurrentRateLimiter(
    strategy: ConcurrentStrategy
  ): ConcurrentRateLimiter[Id] =
    LettuceSyncConcurrentRateLimiter(redisClient, strategy)

  override def toFuture[A](v: Id[A]): Future[A] = Future.successful(v)
}
