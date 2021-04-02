package genkai.redis.redisson.zio

import genkai.{RateLimiter, Strategy}
import genkai.effect.zio.ZioBaseSpec
import genkai.redis.redisson.RedissonSpec
import zio._

import scala.concurrent.Future

class RedissonZioAsyncRateLimiterSpec extends RedissonSpec[Task] with ZioBaseSpec {
  override def rateLimiter(strategy: Strategy): RateLimiter[Task] =
    runtime.unsafeRun(RedissonZioAsyncRateLimiter.useClient(redisClient, strategy))

  override def toFuture[A](v: Task[A]): Future[A] = runtime.unsafeRunToFuture(v)
}
