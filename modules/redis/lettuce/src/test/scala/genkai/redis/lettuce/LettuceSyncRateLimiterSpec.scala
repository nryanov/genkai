package genkai.redis.lettuce

import genkai.{Identity, RateLimiter, Strategy}

import scala.concurrent.Future

class LettuceSyncRateLimiterSpec extends LettuceRateLimiterSpec[Identity] {
  override def rateLimiter(strategy: Strategy): RateLimiter[Identity] =
    LettuceSyncRateLimiter(redisClient, strategy)

  override def toFuture[A](v: Identity[A]): Future[A] = Future.successful(v)
}
