package genkai.redis.lettuce.cats

import cats.effect.IO
import genkai.effect.cats.CatsBaseSpec
import genkai.{RateLimiter, Strategy}
import genkai.redis.lettuce.LettuceRateLimiterSpec

import scala.concurrent.Future

class LettuceCatsRateLimiterSpec extends LettuceRateLimiterSpec[IO] with CatsBaseSpec {
  override def rateLimiter(strategy: Strategy): RateLimiter[IO] =
    LettuceCatsRateLimiter.useClient[IO](redisClient, strategy, blocker).unsafeRunSync()

  override def toFuture[A](v: IO[A]): Future[A] = v.unsafeToFuture()
}
