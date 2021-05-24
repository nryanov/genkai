package genkai.redis.lettuce.zio

import genkai.{ConcurrentRateLimiter, ConcurrentStrategy}
import genkai.effect.zio.ZioBaseSpec
import genkai.redis.lettuce.LettuceConcurrentRateLimiterSpec
import zio.Task

import scala.concurrent.Future

class LettuceZioAsyncConcurrentRateLimiterSpec
    extends LettuceConcurrentRateLimiterSpec[Task]
    with ZioBaseSpec {
  override def concurrentRateLimiter(strategy: ConcurrentStrategy): ConcurrentRateLimiter[Task] =
    runtime.unsafeRun(LettuceZioAsyncConcurrentRateLimiter.useClient(redisClient, strategy))

  override def toFuture[A](v: Task[A]): Future[A] = runtime.unsafeRunToFuture(v)
}
