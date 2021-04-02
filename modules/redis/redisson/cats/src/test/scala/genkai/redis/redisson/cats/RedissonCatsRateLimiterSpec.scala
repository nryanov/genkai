package genkai.redis.redisson.cats

import cats.effect.IO
import genkai.{RateLimiter, Strategy}
import genkai.effect.cats.CatsBaseSpec
import genkai.redis.redisson.RedissonSpec

import scala.concurrent.Future

class RedissonCatsRateLimiterSpec extends RedissonSpec[IO] with CatsBaseSpec {
  override def rateLimiter(strategy: Strategy): RateLimiter[IO] =
    RedissonCatsRateLimiter.useClient[IO](redisClient, strategy, blocker).unsafeRunSync()

  override def toFuture[A](v: IO[A]): Future[A] = v.unsafeToFuture()
}
