package genkai.redis.redisson.cats

import cats.effect.IO
import genkai.{RateLimiter, Strategy}
import genkai.effect.cats.CatsBaseSpec
import genkai.redis.redisson.RedissonRateLimiterSpec

import scala.concurrent.Future

class RedissonCatsAsyncRateLimiterSpec extends RedissonRateLimiterSpec[IO] with CatsBaseSpec {
  override def rateLimiter(strategy: Strategy): RateLimiter[IO] =
    RedissonCatsAsyncRateLimiter.useClient[IO](redisClient, strategy).unsafeRunSync()

  override def toFuture[A](v: IO[A]): Future[A] = v.unsafeToFuture()
}
