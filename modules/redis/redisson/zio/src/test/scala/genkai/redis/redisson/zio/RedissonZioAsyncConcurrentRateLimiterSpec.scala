package genkai.redis.redisson.zio

import genkai.{ConcurrentRateLimiter, ConcurrentStrategy}
import genkai.effect.zio.ZioBaseSpec
import genkai.redis.redisson.RedissonConcurrentRateLimiterSpec
import zio.Task

import scala.concurrent.Future

class RedissonZioAsyncConcurrentRateLimiterSpec extends RedissonConcurrentRateLimiterSpec[Task] with ZioBaseSpec {
  override def concurrentRateLimiter(strategy: ConcurrentStrategy): ConcurrentRateLimiter[Task] =
    runtime.unsafeRun(RedissonZioAsyncConcurrentRateLimiter.useClient(redisClient, strategy))

  override def toFuture[A](v: Task[A]): Future[A] = runtime.unsafeRunToFuture(v)
}
