package genkai.redis.redisson.cats3

import cats.effect.IO
import genkai.{RateLimiter, Strategy}
import genkai.effect.cats3.Cats3BaseSpec
import genkai.redis.redisson.RedissonRateLimiterSpec

import scala.concurrent.Future

class RedissonCats3AsyncRateLimiterSpec extends RedissonRateLimiterSpec[IO] with Cats3BaseSpec {
  override def rateLimiter(strategy: Strategy): RateLimiter[IO] =
    RedissonCats3AsyncRateLimiter.useClient[IO](redisClient, strategy).unsafeRunSync()

  override def toFuture[A](v: IO[A]): Future[A] = v.unsafeToFuture()
}
