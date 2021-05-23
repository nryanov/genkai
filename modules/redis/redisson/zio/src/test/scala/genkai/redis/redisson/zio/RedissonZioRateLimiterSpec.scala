package genkai.redis.redisson.zio

import genkai.{RateLimiter, Strategy}
import genkai.effect.zio.ZioBaseSpec
import genkai.redis.redisson.RedissonRateLimiterSpec
import zio.Task

import scala.concurrent.Future

class RedissonZioRateLimiterSpec extends RedissonRateLimiterSpec[Task] with ZioBaseSpec {
  override def rateLimiter(strategy: Strategy): RateLimiter[Task] =
    runtime.unsafeRun(RedissonZioRateLimiter.useClient(redisClient, strategy))

  override def toFuture[A](v: Task[A]): Future[A] = runtime.unsafeRunToFuture(v)
}
