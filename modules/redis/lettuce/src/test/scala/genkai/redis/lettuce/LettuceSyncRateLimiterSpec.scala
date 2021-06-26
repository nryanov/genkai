package genkai.redis.lettuce

import genkai.{Id, RateLimiter, Strategy}

import scala.concurrent.Future

class LettuceSyncRateLimiterSpec extends LettuceRateLimiterSpec[Id] {
  override def rateLimiter(strategy: Strategy): RateLimiter[Id] =
    LettuceSyncRateLimiter(redisClient, strategy)

  override def toFuture[A](v: Id[A]): Future[A] = Future.successful(v)
}
