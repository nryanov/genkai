package genkai.redis.redisson

import genkai.{RateLimiter, Strategy}

import scala.concurrent.Future

class RedissonFutureRateLimiterSpec extends RedissonRateLimiterSpec[Future] {
  override def rateLimiter(strategy: Strategy): RateLimiter[Future] =
    RedissonFutureRateLimiter(redisClient, strategy)

  override def toFuture[A](v: Future[A]): Future[A] = v
}
