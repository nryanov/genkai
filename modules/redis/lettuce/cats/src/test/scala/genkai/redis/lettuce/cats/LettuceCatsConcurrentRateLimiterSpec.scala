package genkai.redis.lettuce.cats

import cats.effect.IO
import genkai.{ConcurrentRateLimiter, ConcurrentStrategy}
import genkai.effect.cats.CatsBaseSpec
import genkai.redis.lettuce.LettuceConcurrentRateLimiterSpec

import scala.concurrent.Future

class LettuceCatsConcurrentRateLimiterSpec
    extends LettuceConcurrentRateLimiterSpec[IO]
    with CatsBaseSpec {
  override def concurrentRateLimiter(strategy: ConcurrentStrategy): ConcurrentRateLimiter[IO] =
    LettuceCatsAsyncConcurrentRateLimiter.useClient[IO](redisClient, strategy).unsafeRunSync()

  override def toFuture[A](v: IO[A]): Future[A] = v.unsafeToFuture()
}
