package genkai.redis.redisson.monix

import genkai.{RateLimiter, Strategy}
import genkai.effect.monix.MonixBaseSpec
import genkai.redis.redisson.RedissonRateLimiterSpec
import monix.eval.Task

import scala.concurrent.Future

class RedissonMonixAsyncRateLimiterSpec extends RedissonRateLimiterSpec[Task] with MonixBaseSpec {
  override def rateLimiter(strategy: Strategy): RateLimiter[Task] =
    RedissonMonixAsyncRateLimiter.useClient(redisClient, strategy).runSyncUnsafe()

  override def toFuture[A](v: Task[A]): Future[A] = v.runToFuture
}
