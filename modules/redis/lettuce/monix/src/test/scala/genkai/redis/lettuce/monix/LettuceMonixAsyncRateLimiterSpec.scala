package genkai.redis.lettuce.monix

import genkai.effect.monix.MonixBaseSpec
import genkai.{RateLimiter, Strategy}
import genkai.redis.lettuce.LettuceRateLimiterSpec
import monix.eval.Task

import scala.concurrent.Future

class LettuceMonixAsyncRateLimiterSpec extends LettuceRateLimiterSpec[Task] with MonixBaseSpec {
  override def rateLimiter(strategy: Strategy): RateLimiter[Task] =
    LettuceMonixAsyncRateLimiter.useClient(redisClient, strategy).runSyncUnsafe()

  override def toFuture[A](v: Task[A]): Future[A] = v.runToFuture
}
