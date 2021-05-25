package genkai.redis.lettuce

import genkai.{ConcurrentRateLimiter, ConcurrentStrategy}

import scala.concurrent.Future

class LettuceFutureConcurrentRateLimiterSpec extends LettuceConcurrentRateLimiterSpec[Future] {
  override def concurrentRateLimiter(
    strategy: ConcurrentStrategy
  ): ConcurrentRateLimiter[Future] =
    LettuceFutureConcurrentRateLimiter(redisClient, strategy)

  override def toFuture[A](v: Future[A]): Future[A] = v
}
