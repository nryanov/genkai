package genkai.redis.redisson.monix

import genkai.{ConcurrentRateLimiter, ConcurrentStrategy}
import genkai.effect.monix.MonixBaseSpec
import genkai.redis.redisson.RedissonConcurrentRateLimiterSpec
import monix.eval.Task

import scala.concurrent.Future

class RedissonMonixConcurrentRateLimiterSpec extends RedissonConcurrentRateLimiterSpec[Task] with MonixBaseSpec {
  override def concurrentRateLimiter(strategy: ConcurrentStrategy): ConcurrentRateLimiter[Task] =
    RedissonMonixAsyncConcurrentRateLimiter.useClient(redisClient, strategy).runSyncUnsafe()

  override def toFuture[A](v: Task[A]): Future[A] = v.runToFuture
}
