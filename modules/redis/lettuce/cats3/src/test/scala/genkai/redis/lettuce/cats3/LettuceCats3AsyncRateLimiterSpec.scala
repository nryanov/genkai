package genkai.redis.lettuce.cats3

import cats.effect.IO
import genkai.effect.cats3.Cats3BaseSpec
import genkai.{RateLimiter, Strategy}
import genkai.redis.lettuce.LettuceRateLimiterSpec

import scala.concurrent.Future

class LettuceCats3AsyncRateLimiterSpec extends LettuceRateLimiterSpec[IO] with Cats3BaseSpec {
  override def rateLimiter(strategy: Strategy): RateLimiter[IO] =
    LettuceCats3AsyncRateLimiter.useClient[IO](redisClient, strategy).unsafeRunSync()

  override def toFuture[A](v: IO[A]): Future[A] = v.unsafeToFuture()
}
