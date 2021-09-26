package genkai.redis.lettuce.cats3

import cats.effect.IO
import genkai.{ConcurrentRateLimiter, ConcurrentStrategy}
import genkai.effect.cats3.Cats3BaseSpec
import genkai.redis.lettuce.LettuceConcurrentRateLimiterSpec

import scala.concurrent.Future

class LettuceCats3ConcurrentRateLimiterSpec extends LettuceConcurrentRateLimiterSpec[IO] with Cats3BaseSpec {
  override def concurrentRateLimiter(strategy: ConcurrentStrategy): ConcurrentRateLimiter[IO] =
    LettuceCats3AsyncConcurrentRateLimiter.useClient[IO](redisClient, strategy).unsafeRunSync()

  override def toFuture[A](v: IO[A]): Future[A] = v.unsafeToFuture()
}
