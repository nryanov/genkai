package genkai.redis.lettuce.monix

import genkai.{ConcurrentRateLimiter, ConcurrentStrategy}
import genkai.effect.monix.MonixBaseSpec
import genkai.redis.lettuce.LettuceConcurrentRateLimiterSpec
import monix.eval.Task

import scala.concurrent.Future

class LettuceMonixAsyncConcurrentRateLimiterSpec
    extends LettuceConcurrentRateLimiterSpec[Task]
    with MonixBaseSpec {
  override def concurrentRateLimiter(strategy: ConcurrentStrategy): ConcurrentRateLimiter[Task] =
    LettuceMonixAsyncConcurrentRateLimiter.useClient(redisClient, strategy).runSyncUnsafe()

  override def toFuture[A](v: Task[A]): Future[A] = v.runToFuture
}
