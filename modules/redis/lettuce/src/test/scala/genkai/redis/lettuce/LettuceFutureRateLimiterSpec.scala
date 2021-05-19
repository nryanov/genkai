package genkai.redis.lettuce

import genkai.{RateLimiter, Strategy}

import scala.concurrent.Future

class LettuceFutureRateLimiterSpec extends LettuceRateLimiterSpec[Future] {
  override def rateLimiter(strategy: Strategy): RateLimiter[Future] =
    LettuceFutureRateLimiter(redisClient, strategy)

  override def toFuture[A](v: Future[A]): Future[A] = v
}
