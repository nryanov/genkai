package genkai.redis.lettuce.zio

import genkai.{RateLimiter, Strategy}
import genkai.effect.zio.ZioBaseSpec
import genkai.redis.lettuce.LettuceRateLimiterSpec
import zio.Task

import scala.concurrent.Future

class LettuceZioRateLimiterSpec extends LettuceRateLimiterSpec[Task] with ZioBaseSpec {
  override def rateLimiter(strategy: Strategy): RateLimiter[Task] =
    runtime.unsafeRun(LettuceZioRateLimiter.useClient(redisClient, strategy))

  override def toFuture[A](v: Task[A]): Future[A] = runtime.unsafeRunToFuture(v)
}
